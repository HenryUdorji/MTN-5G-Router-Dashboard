#!/usr/bin/env python3
"""
Read-only probe for ZTE MC888D (MTN) routers.

What it does:
  1. Fetches version/login fields WITHOUT logging in.
  2. Picks the login scheme from what the router reports (one attempt only,
     to avoid the router's lockout after repeated failures).
  3. Logs in and fetches signal / band / cell fields.
  4. Prints everything as raw JSON (and saves it to probe_output.json),
     with IMEI/IMSI/ICCID/phone-number/MAC fields redacted.

It never changes any setting. The only POST it sends is LOGIN.

Usage:
  python3 probe.py                      # prompts for the admin password
  python3 probe.py --host 192.168.0.1
  python3 probe.py --scheme base64      # force a login scheme if auto fails
Requires only Python 3.8+ standard library.
"""
import argparse
import base64
import getpass
import hashlib
import http.cookiejar
import json
import re
import sys
import time
import urllib.parse
import urllib.request

VERSION_FIELDS = [
    "wa_inner_version", "cr_version", "hardware_version", "web_version",
    "wa_version", "model_name", "Language", "LD", "RD",
    "loginfo", "login_lock_time", "psw_fail_num_str", "user_login_type",
]

SIGNAL_FIELDS = [
    # general
    "network_type", "network_provider", "ppp_status", "signalbar",
    "wan_active_band", "wan_active_channel", "wan_lte_ca", "dial_mode",
    "realtime_tx_thrpt", "realtime_rx_thrpt",
    # LTE
    "lte_rsrp", "lte_rsrq", "lte_snr", "lte_rssi", "lte_band",
    "lte_pci", "cell_id", "lte_ca_pcell_band", "lte_ca_pcell_freq",
    "lte_ca_scell_band", "lte_ca_scell_info", "lte_multi_ca_scell_info",
    "lte_band_lock", "lte_pci_lock", "lte_earfcn_lock",
    # 5G NR
    "Z5g_rsrp", "Z5g_rsrq", "Z5g_SINR", "Z5g_rssi", "Z5g_snr",
    "nr5g_action_band", "nr5g_action_channel", "nr5g_pci", "nr5g_cell_id",
    "nr5g_bandwidth", "nr5g_band_lock", "nr_ca_pcell_band",
    "nr_multi_ca_scell_info",
    # misc
    "rssi", "rscp", "ecio", "wan_ipaddr", "monthly_rx_bytes", "monthly_tx_bytes",
]

REDACT = re.compile(r"imei|imsi|iccid|msisdn|phone|mac|sim_|serial|sn$", re.I)


def redact(d):
    return {k: ("<redacted>" if REDACT.search(k) and v else v) for k, v in d.items()}


class Router:
    def __init__(self, host):
        self.base = f"http://{host}"
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar))
        self.headers = {
            # The router rejects requests without a same-origin Referer.
            "Referer": f"{self.base}/index.html",
            "X-Requested-With": "XMLHttpRequest",
            "User-Agent": "Mozilla/5.0",
        }

    def _open(self, req):
        with self.opener.open(req, timeout=10) as r:
            body = r.read().decode("utf-8", "replace")
        try:
            return json.loads(body)
        except json.JSONDecodeError:
            return {"_raw_non_json": body[:500]}

    def get(self, fields):
        q = {"isTest": "false", "cmd": ",".join(fields), "_": str(int(time.time() * 1000))}
        if len(fields) > 1:
            q["multi_data"] = "1"
        url = f"{self.base}/goform/goform_get_cmd_process?{urllib.parse.urlencode(q)}"
        return self._open(urllib.request.Request(url, headers=self.headers))

    def post(self, data):
        body = urllib.parse.urlencode(data).encode()
        h = dict(self.headers, **{"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"})
        req = urllib.request.Request(f"{self.base}/goform/goform_set_cmd_process",
                                     data=body, headers=h, method="POST")
        return self._open(req)


def sha256u(s):
    return hashlib.sha256(s.encode()).hexdigest().upper()


def login(r, password, scheme, info):
    if scheme == "sha256":
        ld = info.get("LD") or r.get(["LD"]).get("LD", "")
        pw = sha256u(sha256u(password) + ld)
    elif scheme == "base64":
        pw = base64.b64encode(password.encode()).decode()
    else:
        raise ValueError(scheme)
    return r.post({"isTest": "false", "goformId": "LOGIN", "password": pw})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="192.168.0.1")
    ap.add_argument("--password", help="admin password (prompted if omitted)")
    ap.add_argument("--scheme", choices=["auto", "sha256", "base64"], default="auto")
    args = ap.parse_args()

    r = Router(args.host)
    out = {}

    print(f"[1] Unauthenticated read from {r.base} ...")
    try:
        info = r.get(VERSION_FIELDS)
    except Exception as e:
        sys.exit(f"Could not reach router: {e!r}\nAre you on its Wi-Fi/LAN?")
    out["pre_login"] = info
    print(json.dumps(info, indent=2))

    if info.get("loginfo") == "ok":
        print("Already logged in (session cookie / no-auth reads).")
    else:
        if info.get("login_lock_time") not in (None, "", "0", "-1"):
            sys.exit(f"Router reports login lock ({info['login_lock_time']}s). Wait and retry.")
        scheme = args.scheme
        if scheme == "auto":
            scheme = "sha256" if info.get("LD") else "base64"
        print(f"\n[2] Logging in with scheme '{scheme}' "
              f"(failed attempts so far: {info.get('psw_fail_num_str') or 'unknown'}) ...")
        password = args.password or getpass.getpass("Router admin password: ")
        res = login(r, password, scheme, info)
        out["login_scheme"] = scheme
        out["login_response"] = res
        print("Login response:", res)
        if str(res.get("result")) != "0":
            print("\nLogin did NOT return result=0. Do not retry repeatedly (lockout).\n"
                  "Paste this output back so the scheme can be adjusted.")

    print("\n[3] Authenticated reads ...")
    auth_info = r.get(VERSION_FIELDS)
    sig = r.get(SIGNAL_FIELDS)
    out["post_login"] = auth_info
    out["signal"] = redact(sig)
    out["cookies"] = [c.name for c in r.jar]  # names only, not values
    print(json.dumps(out["signal"], indent=2))

    # Inputs for the write token, so we can confirm the AD formula later.
    wa, cr, rd = auth_info.get("wa_inner_version", ""), auth_info.get("cr_version", ""), auth_info.get("RD", "")
    out["ad_inputs_present"] = {"wa_inner_version": bool(wa), "cr_version": bool(cr), "RD": bool(rd)}

    with open("probe_output.json", "w") as f:
        json.dump(out, f, indent=2)
    print("\nSaved probe_output.json. Paste its contents back (check it for anything private first).")


if __name__ == "__main__":
    main()
