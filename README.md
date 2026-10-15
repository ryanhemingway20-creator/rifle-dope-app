# Rifle Config — Android companion app

Enter your load and conditions on your phone, let it compute your full DOPE
table with a G7/G1 point-mass solver, and push it to the RifleDOPE board over
Bluetooth LE. Also sets the board's unit number / team and reads link + battery
status. No pairing, no PIN — the app scans for the board and connects in one tap.

This is one half of a two-part system:
- **this app** (`android/RifleConfig`)
- **the board firmware** (`firmware/rifle_display/rifle_unified.ino`, built with
  `ENABLE_BLECFG 1`)

They speak the text protocol in [`../PROTOCOL.md`](../PROTOCOL.md).

## Build & install (Android Studio)

1. Install **Android Studio** (free) — it bundles the right JDK and Android SDK.
2. **Open** the `RifleConfig` folder as a project. Let Gradle sync finish (the
   first sync downloads the AndroidX / Compose libraries, so it needs internet).
3. Put your phone in Developer mode with **USB debugging** on, plug it in, and
   click **Run ▶** — it installs and launches.
   *Or* build an installable file: **Build ▸ Build Bundle(s) / APK(s) ▸ Build
   APK(s)**, then copy `app/build/outputs/apk/debug/app-debug.apk` to the phone
   and tap it to install (you'll need to allow "install unknown apps" once).

> **Why there's no prebuilt APK in this folder:** the cloud environment this was
> built in can't reach Google's Android servers (`dl.google.com` is blocked), so
> the Android SDK and the AndroidX libraries can't be downloaded here to compile
> an APK. Android Studio on your own machine does it in two clicks. If you'd
> rather I hand you a ready-to-install APK with no Android Studio, connect GitHub
> to this project and I'll set up a cloud build that produces a signed APK you
> just download.

**Needs:** Android Studio (latest), an Android phone on 8.0+ with Bluetooth LE.

## Board side

1. In the Arduino IDE, install **NimBLE-Arduino** from the Library Manager.
2. Flash `firmware/rifle_display/rifle_unified.ino` (ESP32S3 Dev Module, OPI
   PSRAM on, USB CDC On Boot enabled). The board advertises as
   `RifleDOPE-1` (unit 1) / `RifleDOPE-2` (unit 2).

## Using it

- **Board** tab → **Scan** → tap your board. Status (firmware, unit, team,
  partner link, battery) shows once connected.
- **Load** tab → enter the rifle/load, zero, sight height, and the conditions
  the dope was trued at. You can keep several profiles (one per rifle/load).
- **DOPE** tab → review the computed table, then **Send to board**. The board
  stores it to flash and displays it on the DOPE / WIND / TABLE pages.
- **Load from board** pulls the board's current settings back into the app.

## Accuracy

The solver is a G7/G1 point-mass trajectory integrator with standard atmosphere.
It was validated to within **0.005 mil** against a py_ballisticcalc-derived
reference for the 6.5 CM 145gr load. These are **computed** holds from your
inputs — true them against live fire before you trust them downrange.

## Layout

```
RifleConfig/
  app/src/main/java/com/rifledope/config/
    ballistics/   G1/G7 drag tables + point-mass solver
    ble/          BleManager (scan/connect/chunked write/notify) + Protocol codec
    model/        Profile + BoardStatus
    data/         ProfileStore (saved profiles)
    vm/           AppViewModel (glue)
    ui/           Compose screens (Board / Load / DOPE)
```
