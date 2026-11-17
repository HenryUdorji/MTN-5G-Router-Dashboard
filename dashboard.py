#!/usr/bin/env python3
"""
Live signal dashboard for the ZTE MC888D.

  python3 dashboard.py            then open http://localhost:8088
  python3 dashboard.py --port 9000 --interval 5

Polls the router every few seconds, keeps the last hour in memory, and
appends every sample to signal_log.csv. Read-only: it changes no settings.
Password: prompted at start, or set MC888_PASSWORD in the environment.
"""
import argparse
import csv
import json
import os
import threading
import time
import webbrowser
from collections import deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from zte import RouterError, router_from_env

HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.path.join(HERE, "signal_log.csv")
CSV_COLS = ["ts", "network_type", "lte_band", "lte_pci", "lte_rsrp", "lte_rsrq", "lte_sinr",
            "nr_band", "nr_pci", "nr_rsrp", "nr_rsrq", "nr_sinr", "down_mbps", "up_mbps"]

state = {"latest": None, "error": None, "history": deque(maxlen=3600)}


def flat(s):
    l, n = s["lte"], s["nr"]
    return {"ts": round(s["ts"], 1), "network_type": s["network_type"],
            "lte_band": l["band"], "lte_pci": l["pci"], "lte_rsrp": l["rsrp"],
            "lte_rsrq": l["rsrq"], "lte_sinr": l["sinr"], "nr_band": n["band"],
            "nr_pci": n["pci"], "nr_rsrp": n["rsrp"], "nr_rsrq": n["rsrq"],
            "nr_sinr": n["sinr"], "down_mbps": round(s["down_mbps"], 3),
            "up_mbps": round(s["up_mbps"], 3)}


def load_history(seconds=3600):
    if not os.path.exists(LOG):
        return
    cutoff = time.time() - seconds
    with open(LOG) as f:
        for row in csv.DictReader(f):
            try:
                if float(row["ts"]) < cutoff:
                    continue
            except (KeyError, ValueError):
                continue
            for k, v in row.items():
                if k not in ("network_type", "lte_band", "nr_band"):
                    try:
                        row[k] = float(v)
                    except (TypeError, ValueError):
                        row[k] = None
            state["history"].append(row)


def poller(router, interval):
    new_file = not os.path.exists(LOG)
    with open(LOG, "a", newline="") as f:
        w = csv.DictWriter(f, fieldnames=CSV_COLS)
        if new_file:
            w.writeheader()
        while True:
            try:
                s = router.signal()
                row = flat(s)
                state["latest"], state["error"] = s, None
                state["history"].append(row)
                w.writerow(row)
                f.flush()
            except RouterError as e:
                state["error"] = str(e)
                if "login failed" in str(e):
                    print(f"\n{e}\nStopping polling so the router doesn't lock the login.")
                    return
            except Exception as e:  # network blips: keep trying
                state["error"] = f"{type(e).__name__}: {e}"
            time.sleep(interval)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _send(self, code, body, ctype):
        data = body if isinstance(body, bytes) else body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            with open(os.path.join(HERE, "static", "index.html"), "rb") as f:
                self._send(200, f.read(), "text/html; charset=utf-8")
        elif self.path == "/api/latest":
            self._send(200, json.dumps({"latest": state["latest"], "error": state["error"]}),
                       "application/json")
        elif self.path == "/api/history":
            self._send(200, json.dumps(list(state["history"])), "application/json")
        else:
            self._send(404, "not found", "text/plain")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8088)
    ap.add_argument("--interval", type=float, default=3, help="seconds between polls")
    ap.add_argument("--no-browser", action="store_true")
    args = ap.parse_args()

    router = router_from_env()
    print("Logging in ...")
    router.ensure_login()  # fail fast on a bad password
    load_history()
    threading.Thread(target=poller, args=(router, args.interval), daemon=True).start()

    url = f"http://localhost:{args.port}"
    print(f"Dashboard running at {url}  (Ctrl+C to stop)")
    if not args.no_browser:
        threading.Timer(1.0, webbrowser.open, [url]).start()
    try:
        ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
