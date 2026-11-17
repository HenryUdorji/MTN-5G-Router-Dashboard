#!/usr/bin/env python3
"""
Client for the ZTE MC888 / MC888D web API (MTN Nigeria firmware).

Confirmed on firmware BD_MTNNGMC888V1.0.0B05 / WEB_MTNNGMC888DV1.0.0B04:
  - login: password = SHA256(SHA256(pw).upper() + LD).upper(), goformId=LOGIN
  - session cookie: stok
  - write token AD = SHA256(SHA256(wa_inner_version + cr_version) + RD), uppercase hex

Command-line use:
  python3 zte.py signal        print parsed signal once
  python3 zte.py test-write    confirm the write token (AD) works by re-setting
                               the web UI language to its current value
"""
import base64
import getpass
import hashlib
import http.cookiejar
import json
import os
import sys
import threading
import time
import urllib.parse
import urllib.request

SIGNAL_FIELDS = [
    "network_type", "network_provider", "ppp_status", "signalbar",
    "wan_active_band", "wan_active_channel", "wan_lte_ca",
    "realtime_tx_thrpt", "realtime_rx_thrpt",
    "lte_rsrp", "lte_rsrq", "lte_snr", "lte_rssi", "lte_band", "lte_pci", "cell_id",
    "lte_ca_pcell_band", "lte_ca_pcell_freq", "lte_multi_ca_scell_info",
    "lte_band_lock", "lte_pci_lock", "lte_earfcn_lock",
    "Z5g_rsrp", "Z5g_rsrq", "Z5g_SINR", "Z5g_rssi",
    "nr5g_action_band", "nr5g_action_channel", "nr5g_pci", "nr5g_band_lock",
    "monthly_rx_bytes", "monthly_tx_bytes",
]


class RouterError(Exception):
    pass


def _sha256(s):
    return hashlib.sha256(s.encode()).hexdigest()


def _md5(s):
    return hashlib.md5(s.encode()).hexdigest()


class Router:
    def __init__(self, host="192.168.0.1", password=None):
        self.base = f"http://{host}"
        self.password = password
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.headers = {
            "Referer": f"{self.base}/index.html",
            "X-Requested-With": "XMLHttpRequest",
            "User-Agent": "Mozilla/5.0",
        }
        self.lock = threading.Lock()

    # ---- transport ----
    def _open(self, req):
        with self.opener.open(req, timeout=10) as r:
            body = r.read().decode("utf-8", "replace")
        try:
            return json.loads(body)
        except json.JSONDecodeError:
            raise RouterError(f"non-JSON reply: {body[:200]!r}")

    def _get(self, fields):
        q = {"isTest": "false", "cmd": ",".join(fields), "_": str(int(time.time() * 1000))}
        if len(fields) > 1:
            q["multi_data"] = "1"
        url = f"{self.base}/goform/goform_get_cmd_process?{urllib.parse.urlencode(q)}"
        return self._open(urllib.request.Request(url, headers=self.headers))

    def _post(self, data):
        h = dict(self.headers)
        h["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8"
        req = urllib.request.Request(f"{self.base}/goform/goform_set_cmd_process",
                                     data=urllib.parse.urlencode(data).encode(),
                                     headers=h, method="POST")
        return self._open(req)

    # ---- session ----
    def logged_in(self):
        return self._get(["loginfo"]).get("loginfo") == "ok"

    def login(self):
        if not self.password:
            raise RouterError("no password set")
        info = self._get(["LD", "login_lock_time", "psw_fail_num_str"])
        if info.get("LD"):
            pw = _sha256(_sha256(self.password).upper() + info["LD"]).upper()
        else:
            pw = base64.b64encode(self.password.encode()).decode()
        res = self._post({"isTest": "false", "goformId": "LOGIN", "password": pw})
        if str(res.get("result")) != "0":
            # Stop here: repeated failures lock the admin login.
            raise RouterError(f"login failed: {res} (attempts left: {info.get('psw_fail_num_str')})")

    def ensure_login(self):
        if not self.logged_in():
            self.login()

    # ---- reads ----
    def get(self, fields):
        with self.lock:
            self.ensure_login()
            return self._get(fields)

    def signal(self):
        return parse_signal(self.get(SIGNAL_FIELDS))

    # ---- writes ----
    def _ad(self):
        """Write token, as the router's own page computes it (js/util.js: cookWithRequest = SHA256,
        uppercase hex): AD = SHA256(SHA256(wa_inner_version + cr_version) + RD)."""
        v = self._get(["wa_inner_version", "cr_version", "RD"])
        h = lambda x: _sha256(x).upper()
        return h(h(v.get("wa_inner_version", "") + v.get("cr_version", "")) + v.get("RD", ""))

    def set(self, goform_id, **params):
        """Send a write command with the AD token."""
        with self.lock:
            self.ensure_login()
            data = {"isTest": "false", "goformId": goform_id, "AD": self._ad()}
            data.update(params)
            res = self._post(data)
            if str(res.get("result")).lower() in ("success", "0"):
                return res
            raise RouterError(f"{goform_id} rejected: {res}")

    def developer_login(self):
        """Unlock the router's Developer options (same password as the admin login)."""
        with self.lock:
            self.ensure_login()
            ld = self._get(["LD"]).get("LD", "")
            pw = _sha256(_sha256(self.password).upper() + ld).upper()
            res = self._post({"isTest": "false", "goformId": "DEVELOPER_OPTION_LOGIN",
                              "password": pw, "AD": self._ad()})
            if str(res.get("result")) != "0":
                raise RouterError(f"developer login failed: {res}")
            return res


# ---- parsing ----
def _num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def _hex(v):
    try:
        return int(v, 16)
    except (TypeError, ValueError):
        return None


def decode_lte_mask(mask):
    m = _hex(mask.replace("0x", "")) if mask else None
    return [i + 1 for i in range(64) if m and (m >> i) & 1]


def parse_scells(info):
    """'1,211,1,3,1650,20.0;...' -> [{pci, band, earfcn, bw_mhz}]"""
    out = []
    for part in filter(None, (info or "").split(";")):
        f = part.split(",")
        if len(f) >= 6:
            out.append({"pci": _num(f[1]), "band": f[3], "earfcn": f[4], "bw_mhz": _num(f[5])})
    return out


def parse_signal(raw):
    lte_pci = _hex(raw.get("lte_pci"))
    nr_pci = _hex(raw.get("nr5g_pci"))
    return {
        "ts": time.time(),
        "network_type": raw.get("network_type"),
        "provider": raw.get("network_provider"),
        "connected": "connected" in (raw.get("ppp_status") or ""),
        "bars": _num(raw.get("signalbar")),
        "lte": {
            "band": raw.get("lte_ca_pcell_band") or raw.get("lte_band"),
            "earfcn": raw.get("lte_ca_pcell_freq") or raw.get("wan_active_channel"),
            "pci": lte_pci,
            "cell_id": raw.get("cell_id"),
            "rsrp": _num(raw.get("lte_rsrp")),
            "rsrq": _num(raw.get("lte_rsrq")),
            "sinr": _num(raw.get("lte_snr")),
            "rssi": _num(raw.get("lte_rssi")),
            "scells": parse_scells(raw.get("lte_multi_ca_scell_info")),
            "band_lock": decode_lte_mask(raw.get("lte_band_lock")),
            "cell_lock": {"pci": raw.get("lte_pci_lock"), "earfcn": raw.get("lte_earfcn_lock")},
        },
        "nr": {
            "band": raw.get("nr5g_action_band"),
            "arfcn": raw.get("nr5g_action_channel"),
            "pci": nr_pci,
            "rsrp": _num(raw.get("Z5g_rsrp")),
            "rsrq": _num(raw.get("Z5g_rsrq")),
            "sinr": _num(raw.get("Z5g_SINR")),
            "rssi": _num(raw.get("Z5g_rssi")),
            "band_lock": [b for b in (raw.get("nr5g_band_lock") or "").split(",") if b],
        },
        # realtime_*_thrpt is bytes per second
        "down_mbps": (_num(raw.get("realtime_rx_thrpt")) or 0) * 8 / 1e6,
        "up_mbps": (_num(raw.get("realtime_tx_thrpt")) or 0) * 8 / 1e6,
        "month_down_gb": (_num(raw.get("monthly_rx_bytes")) or 0) / 1e9,
        "month_up_gb": (_num(raw.get("monthly_tx_bytes")) or 0) / 1e9,
    }


def router_from_env():
    host = os.environ.get("MC888_HOST", "192.168.0.1")
    pw = os.environ.get("MC888_PASSWORD") or getpass.getpass("Router admin password: ")
    return Router(host, pw)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "signal"
    r = router_from_env()
    if cmd == "signal":
        print(json.dumps(r.signal(), indent=2))
    elif cmd == "test-write":
        lang = r.get(["Language"]).get("Language") or "en"
        res = r.set("SET_WEB_LANGUAGE", Language=lang)
        print(f"Write OK: {res}")
    else:
        sys.exit(__doc__)
