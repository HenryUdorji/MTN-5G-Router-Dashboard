# MTN 5G Router Dashboard

Custom tools for the MTN-branded ZTE MC888D 5G router, using its built-in
web API (no firmware changes).

## Step 1: probe the router

`probe.py` is read-only. It logs in, reads firmware version and signal
fields, and saves them to `probe_output.json` (IMEI, SIM and MAC fields are
redacted). It never changes any router setting.

On a Mac, connected to the router's Wi-Fi:

```bash
cd ~/Downloads
python3 probe.py
```

Enter the admin password from the router's sticker when asked. If login
fails, don't retry repeatedly: the router locks you out after several
failed attempts.

Requires Python 3.8+ (no extra packages).
