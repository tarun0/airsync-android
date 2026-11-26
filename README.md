# AirSync Android: multi-device fork (unofficial)

**This is an unofficial fork of AirSync by [sameerasw](https://github.com/sameerasw).** All credit for AirSync goes to him.

- Original Android app: https://github.com/sameerasw/airsync-android
- Original Mac app: https://github.com/sameerasw/airsync-mac
- Official website: https://sameerasw.com/AirSync

This fork is **not affiliated with or endorsed by the original author.** It is **source only**: no builds of any kind (no APK, no `.app`, no releases) are provided or should be redistributed. If you want the Plus/Pro features, buy/support them from the original author through the official website above, or compile the app yourself from source. The multi-device features of this fork sit behind the existing upstream Plus check, so builds that are not self-compiled still need the author's license. Upstream's `LICENSE` (MPL 2.0 plus Additional Terms) apply unchanged. The Android app has no Plus check of its own; the gate is in the Mac app.

## What this fork adds

Needs the matching Mac fork for the multi-device parts. Items marked (Plus) are behind upstream's Plus check on the Mac.

- **Background clipboard capture on Android (root, optional, off by default)**: a small helper (`tools/clipbridge/ClipWatch.java`, shipped as `assets/clipwatch.dex`) is started through `su` and run with `app_process` as the `shell` uid (2000), which Android allows to read the clipboard in the background. It subscribes to clipboard changes and forwards each copied text over the existing WebSocket; no Accessibility service is needed. It requires root access for AirSync; without root the helper is not installed and the feature stays inactive (Accessibility service, tile and share sheet still work). Copies that came from the Mac are skipped so nothing is sent back.
- **Multi-device (Plus)**: one Mac hub with several Android phones/tablets connected at once. Clipboard fans out between all devices with origin/id de-duplication and echo protection, per-device clipboard toggle, Primary/Secondary roles with "Make primary", and a Devices settings area.
- **Per-device sections (Plus)**: notifications and apps per device in a 2/3/4-device grid; sidebar device cards with per-device ADB status and Mirror/Desktop pills.
- **Per-phone wireless ADB (Plus)**: serials resolved per phone via mDNS, auto-heal, tolerance for random wireless-debugging ports, and manual `IP[:port]` connect; scrcpy calls use each phone's explicit serial, with a double-launch guard and window raise.
- **Simultaneous mirrors (Plus)**: inline "Mirror Here" and native mirror per device, each with its own session and port, aspect-fit video and letterbox-aware click mapping; mirror window close controls.
- **Notification persistence and replay**: per-device notifications are stored on the Mac (24 h, cap 50) and survive restarts; Android replays its active notifications after reconnect (at most 30, flagged so no banners are raised).
- **Android reconnect and UI tweaks**: exponential reconnect backoff with jitter, optional Wi-Fi whitelist for reconnects, hide-when-idle media bar, shorter clipboard confirmation pill.
- **Fixes**: crash in the sections grid with no entries, notification dismiss (X) hit target, `macInfo` ack so secondary phones finish their handshake, clipboard JSON escaping on Android.

## More information

Architecture, build instructions, operational gotchas, limits, testing notes, roadmap, upstream sync and licensing: [docs/MULTI_DEVICE.md](docs/MULTI_DEVICE.md).

---

## Original README (upstream)

# airsync-android

Android app for AirSync built with Kotlin Jetpack Compose

Min : Android 11

[<img src="https://steverichey.github.io/google-play-badge-svg/img/en_get.svg" width="30%" />](https://play.google.com/store/apps/details?id=com.sameerasw.airsync)


## Features
- QR Code / Google Lens pairing for quick setup
- Real-time notification mirroring and actionable replies
- Media playback and volume control from Mac
- Cross-device clipboard synchronization
- **Cellular Network Monitoring**: View your Android's cellular status (LTE, 5G, No Signal) directly from your Mac's menu bar.
- **Auto-Start Support**: Enhanced background stability with auto-start helpers for various Android OEMs (Xiaomi, OPPO, Vivo, Letv, etc.).

## How to connect?
Use your built-in camera or Google Lens or anything that can scan a QR code. It will prompt you to open the app. Once authorized, the last device will be saved on the mobile for now for easier reconnection.

## Fork additions

- **Background clipboard capture without Accessibility** — off by default; enable
  Settings → Clipboard → *Background capture (root)*. Android only lets the focused app read
  the clipboard, but uid 2000 (`shell`) holds `READ_CLIPBOARD_IN_BACKGROUND`, so a small helper
  (`tools/clipbridge/ClipWatch.java`, built to `assets/clipwatch.dex`) runs through
  `app_process` as uid 2000 and subscribes to clipboard changes. It needs root
  (KernelSU/Magisk, granted once). Copies that arrive from the Mac are skipped via
  `getPrimaryClipSource`, so nothing echoes back.
- **Shorter clipboard confirmation** — the floating pill now stays 400 ms (600 ms on error)
  instead of 1200/1500 ms.
- **Reconnect backoff** — blind retries wait 10 s, 20 s, 40 s, 80 s, 160 s, then every 5 minutes
  (±15% jitter) instead of probing every 2–10 s. The counter resets on success, on a manual
  connect and when a network becomes available.
- **Wi-Fi whitelist for reconnects** — Settings → Connection → *Reconnect only on these Wi-Fi
  networks*. The SSID is read through root (`cmd wifi status`), so no location permission is
  needed; an empty list keeps the old "retry on any network" behaviour.
- **Hide the floating player when idle** — Settings → Media and Files → *Hide player when idle*
  shows the now-playing bar only while the Mac actually has a media session.

Multi-device work (phone + tablet + Mac) is written up in
[docs/multi-device-clipboard.md](docs/multi-device-clipboard.md).

Build note: the app links the `island-sdk` / `island-sdk-compose` modules from `../essentials`
(see `settings.gradle.kts`). The `essentials` checkout must sit next to this repository so the relative include resolves. Build the helper dex with `tools/clipbridge/build.sh` (needs
`android.jar`; set `ANDROID_JAR` if it is not under `$ANDROID_HOME`).

## [Read Documentation and How-To](https://airsync.notion.site/)


## Thanks!

- To you, seriously…
- To my community mostly known as TIDWIB (Things I Do When I’m Bored ….. see the connection)
- May sound weird but AI, Yeah, without that deep research, assistant in helpless topics, explanations, occasional detonations and all lead to the rapid development of AirSync. Without this, how da hell do I implement a notification listener without weeks of study? Yeah, if you know how to use it efficiently, it is a damn good buddy to do things that you never could imagine.
- All the reddit peeps for showing that this is actually a thing that they wanted.
- [ClipSync](https://github.com/WinShell-Bhanu/Clipsync) by Bhanu, which the background clipboard sync is adapted from (MIT, see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)).
- All the libraries, apps and tools used for development and their creators. Issue volunteers, reddit community helpers…
- My sleep schedule… the lack of.
