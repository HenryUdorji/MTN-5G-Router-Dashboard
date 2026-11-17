# MC888 Control

An Android app for the MTN-branded ZTE MC888D 5G router. It uses the router's
built-in web API, so there's no firmware flashing.

## Repository layout

```
android/          Android app (Kotlin, Jetpack Compose)
  router/         Plain-Kotlin module: router drivers, parsing, login hashing (unit-tested)
  app/            The Android app: screens, theme, settings
tools/            Python scripts for probing a router from a laptop
```

## Running the app

1. Open the `android` folder in Android Studio (File > Open). Let it sync.
2. Connect your phone (USB debugging on), or start an emulator.
3. Press Run.
4. With the phone on the router's Wi-Fi, enter the admin password from the
   router's sticker. The router locks logins after about 5 wrong tries.

Run the router tests from the `android` folder: `./gradlew :router:test`

## Probing a router (tools/)

`tools/probe.py` is read-only. It logs in, reads the firmware version and
signal fields, and saves them to `probe_output.json` (IMEI, SIM and MAC
fields are redacted).

```bash
python3 tools/probe.py
```

Requires Python 3.8+ (no extra packages).

## Confirmed on

- ZTE MC888D, firmware `BD_MTNNGMC888V1.0.0B05`, web UI `WEB_MTNNGMC888DV1.0.0B04`
