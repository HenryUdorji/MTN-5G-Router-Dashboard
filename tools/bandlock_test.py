#!/usr/bin/env python3
"""
Band-lock format test for the ZTE MC888D.

It reads your CURRENT band settings and sends them straight back, unchanged,
using the two band-lock commands. If the router answers "success" and the
settings read back the same, we know the exact format your firmware accepts.

Nothing is changed, but the router may re-check the network after each
command, so the internet can drop for a few seconds.

Usage (on the router's Wi-Fi, from the project folder):
  python3 tools/bandlock_test.py
"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from zte import RouterError, router_from_env  # noqa: E402

READ_FIELDS = ["lte_band_lock", "nr5g_band_lock", "net_select", "network_type",
               "ppp_status", "wan_active_band", "nr5g_action_band"]


def read(r):
    """Read the band fields, making sure we were still signed in when the router answered.

    The router allows one admin session at a time. If another client (the Android app,
    or the web page in a browser) signs in, our session ends and protected fields come
    back empty. Sign in again a few times, then give up with an explanation.
    """
    for _ in range(3):
        d = r.get(READ_FIELDS + ["loginfo"])
        if d.get("loginfo") == "ok":
            d.pop("loginfo", None)
            return d
        r.login()
        time.sleep(1)
    sys.exit("\nThe router keeps signing this script out. Another device is signed in to the router:\n"
             "fully close the MC888 Control app on your phone (swipe it away) and any browser tab\n"
             "showing http://192.168.0.1, wait 10 seconds, then run this again. Nothing was sent.")


def wait_for_internet(r, timeout=90):
    """Wait until the router reports a data connection again."""
    start = time.time()
    while time.time() - start < timeout:
        try:
            s = r.get(["ppp_status", "network_type"])
            if "connected" in s.get("ppp_status", "") and "disconnected" not in s.get("ppp_status", ""):
                return round(time.time() - start), s
        except Exception:
            pass
        time.sleep(3)
    return None, None


def step(r, out, name, goform_id, params, field, expected):
    print(f"\n--- {name} ---")
    print(f"Sending {goform_id} with {params}")
    try:
        res = r.set(goform_id, **params)
        ok = True
    except RouterError as e:
        res, ok = str(e), False
    print("Router answered:", res)
    secs, _ = wait_for_internet(r)
    print("Internet back after", f"{secs} s" if secs is not None else "more than 90 s (check the router)")
    after = read(r)
    same = after.get(field) == expected
    print(f"{field} now: {after.get(field)!r}  ->", "unchanged (good)" if same else "DIFFERENT, see note below")
    out[name] = {"sent": params, "response": res, "accepted": ok,
                 "internet_back_s": secs, "read_back": after.get(field), "unchanged": same}
    return ok and same


def main():
    r = router_from_env()
    before = read(r)
    print("Current settings:")
    print(json.dumps(before, indent=2))

    lte_mask = before.get("lte_band_lock", "")
    nr_list = before.get("nr5g_band_lock", "")
    if not lte_mask or not nr_list:
        sys.exit("Couldn't read the current band settings, so stopping without sending anything.")

    print("\nThis will send the SAME values back:")
    print(f"  4G bands: {lte_mask}")
    print(f"  5G bands: {nr_list}")
    print("The internet may drop for a few seconds after each one.")
    if input("Type yes to continue: ").strip().lower() != "yes":
        sys.exit("Cancelled, nothing sent.")

    out = {"before": before}
    lte_ok = step(r, out, "lte", "BAND_SELECT",
                  {"is_gw_band": "0", "gw_band_mask": "0", "is_lte_band": "1", "lte_band_mask": lte_mask},
                  "lte_band_lock", lte_mask)
    nr_ok = step(r, out, "nr", "WAN_PERFORM_NR5G_BAND_LOCK",
                 {"nr5g_band_mask": nr_list},
                 "nr5g_band_lock", nr_list)
    out["after"] = read(r)

    with open("bandlock_test_output.json", "w") as f:
        json.dump(out, f, indent=2)

    print("\n=== Result ===")
    print("4G band lock format:", "WORKS" if lte_ok else "needs adjusting")
    print("5G band lock format:", "WORKS" if nr_ok else "needs adjusting")
    if not (lte_ok and nr_ok):
        print("\nIf any band setting now reads differently, open http://192.168.0.1, go to the\n"
              "network/band settings page and set bands back to 'all' or 'auto', or reboot the router.")
    print("Saved bandlock_test_output.json. Paste its contents back.")


if __name__ == "__main__":
    main()
