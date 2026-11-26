# Multi-device (clipboard hub, device sections, per-phone ADB and mirror)

## Status (2026-11-26)

Everything below is built and was exercised on hardware with two Android devices (a phone and a tablet) connected at once.
Multi-device is Plus-gated by design; `SELF_COMPILED` builds have every Pro/Plus feature on.

### Phase 1: clipboard hub and echo hardening
- Mac is the hub. Clipboard payloads carry `origin` (stable per-install id), `id` (uuid) and `ts`.
  The Mac applies a phone's copy to its pasteboard and relays it to every other phone, excluding
  the origin session. Old peers ignore the extra keys.
- Echo hardening: `SeenRing` (origin/text/id with a short TTL) on the Mac; pasteboard apply happens
  on main with a `changeCount` marker (fixes the apply/monitor race); origin exclusion on relay.
  Android drops own-origin, echoed and same-text updates before apply, history and toast.
- Secondary sessions get a `macInfo` ack so the phone handshake completes (was a bug: secondaries
  never finished connecting).

### Phase 2: device list, toggle, Make primary
- Connected-phones list with a per-device clipboard toggle and "Make primary". First phone to
  connect is primary; Make primary retargets the single-device features (see Primary vs Secondary).

### Per-phone wireless ADB (`MultiDeviceADB`)
- Refresh runs `adb devices -l` plus `adb mdns services`; mDNS serials are matched to phones by IP
  (phone names no longer contain the model). Among all candidate entries it picks the best live one.
- Auto-heal: if a phone has no live entry it connects using the mDNS endpoint. "Connect ADB" uses
  the mDNS port (Wireless Debugging picks a random port; `ip:5555` is tried first, legacy reported
  ports are the fallback).
- 2-miss debounce: a phone is only marked ADB-disconnected after two consecutive refresh misses
  (altdb ports change randomly). Phones being mirrored are kept, and starting a mirror marks the
  phone connected.
- Every scrcpy/adb call for a phone passes its own explicit `--serial`/`-s`.

### Per-device sections UI
- `DeviceStore` holds per-device notifications, apps and status frames. Secondary frames are routed
  per device instead of being dropped; actions go out via `sendMessage(to:)`.
- Devices settings area; per-device Mirror/Desktop pills; mini-phone connection popover (includes
  a QuickShare toggle for secondaries).
- Sidebar-aware headers: when the sidebar is open, section headers are title-only and the ADB
  status, Connect ADB and Mirror/Desktop pills live in the sidebar device cards. Window/sidebar
  title is "Devices" when more than one phone is connected.
- Notification hit-testing fix: the X / eye-slash circle used to fall through to the card tap and
  open the app; fixed with `contentShape`.

### Mirroring
- scrcpy: per-phone double-launch guard; window is raised to the front on launch and on repeat
  attempts. Blanket `pkill scrcpy` removed.
- Native "Android Mirror" and "Mirror Here" per device via `NativeMirrorSessions`: each session has
  its own port, scid, clients and decoder, so several inline mirrors run simultaneously (e.g. one
  per phone). Aspect-fit video viewport with letterbox-aware touch mapping.

### Notification persistence and replay
- Mac stores per-device notifications in
  `~/Library/Application Support/airsync-mac/Notifications/<deviceId>.json` (24 h, cap 50).
- Android replays its active notifications after `macInfo` (flagged `replay:true`, at most 30,
  oldest first; Android commit 568fff6). The Mac stores replayed items without raising banners.

### Primary vs Secondary
Primary-only: macOS notification banners, media control, calls, file browser, remote control, wake,
QuickShare send, and the single-device ADB target. Secondary phones get everything else per device:
clipboard, notifications (list only), apps, status, ADB, scrcpy and native mirror.

### Known limits
- Desktop mode (native desktop session) is one global session.
- One native mirror per phone.
- Secondary notifications do not raise macOS banners (list only).
- Per-device encryption keys are not done (one shared key).
- QuickShare send is primary-only.
- BLE clipboard stays single-peer.

### Operational gotchas
- macOS Local Network permission blocks adb servers spawned by agents or unsigned launches
  ("No route to host"). Start the server from your own Terminal: `adb kill-server && adb
  start-server`, then `adb connect <ip>:<port>`.
- Launch AirSync with `open /Applications/AirSync.app`, never from a terminal/script.
- altdb uses a random port plus mDNS; stray adb servers on port 5039 can confuse things (kill them).
- Ad-hoc re-signing after each install can reset the Local Network permission; re-approve.
- Do not press "reset key" in the Mac app while testing (breaks pairing for all phones).
- Keep `alwaysKillAdbBeforeConnect` false.

### Build and install (Mac)
```bash
cd "<path-to>/airsync-mac"
xcodebuild -project AirSync.xcodeproj -scheme "AirSync Self Compiled" \
  -destination 'platform=macOS' -derivedDataPath /tmp/airsync-dd \
  ARCHS=arm64 CODE_SIGNING_ALLOWED=NO build
osascript -e 'tell application "AirSync" to quit'
ditto "/tmp/airsync-dd/Build/Products/Self Compiled/AirSync.app" /Applications/AirSync.app
codesign --force --deep --sign - /Applications/AirSync.app
open /Applications/AirSync.app
```
Android is built with Gradle (`./gradlew assembleDebug`) and installed to the phones separately.

### Branches
- This is the `release/multi-device` line, based on upstream `sameerasw/airsync-mac` / `sameerasw/airsync-android`.

### Next ideas
Per-device encryption keys; per-phone QuickShare send; banners for secondary notifications;
Windows client; "extensions" framework (feasibility first).

## Original design (kept for reference; the hub model above is what was built)

Goal: Android phone + Android tablet + Mac (and later more) all share one clipboard. A copy on
any device lands on every other connected device, and nothing echoes back.

## What the code does today (single device)

Android (`airsync-android`):

- `utils/WebSocketUtil.kt` holds **one** `webSocket` and one `isConnected` flag; `sendMessage()`
  writes to that single socket. There is no notion of "which device am I sending to".
- `utils/ClipboardSyncManager.kt` dedupes with single-value `lastSentText` / `lastReceivedText`,
  and `syncTextToDesktop()` is the one outbound path (used by both the bridge and the tile).
- `utils/RootClipboardBridge.kt` (local fork) runs the uid-2000 helper and forwards each copy
  through `syncTextToDesktop()`.
- `data/local/DataStoreManager.kt` stores one `lastConnectedDevice`; `ConnectedDevice` is
  keyed by name/IP/port only — there is no stable device id.

Mac (`airsync-mac`):

- `Core/WebSocket/WebSocketServer+Outgoing.swift` → `sendClipboardUpdate()` calls
  `sendToFirstAvailable(message:)`, i.e. **only the first connected client** gets it.
- `Core/AppState.swift` keeps one `device` and the clipboard guards
  (`lastClipboardValue`, `lastClipboardChangeCount`, `lastReceivedFromAndroidText`).
- BLE (`Core/BLE/BLETransportBridge.swift`) is 1:1 and cannot carry a broadcast.

## Protocol change (backward compatible)

`clipboardUpdate.data` gains two optional fields:

```json
{ "type": "clipboardUpdate",
  "data": { "text": "...", "origin": "<deviceId>", "id": "<uuid>", "ts": 1795629600 } }
```

Old peers ignore unknown keys, so mixed versions keep working (they just stay single-device).
`origin` must be a stable per-install id (Android: new DataStore key; Mac: existing install id).

## Two topologies — pick one before coding

1. **Hub (recommended first step).** The Mac is already the server, so it relays: it applies a
   copy locally and forwards it to every *other* connected device, never back to `origin`.
   Android devices stay clients. No loop risk, minimal Android changes (one socket, one peer).
   Limits: no phone↔tablet sync while the Mac is off; Mac must relay rather than just consume.

2. **Mesh.** Every device connects to every other and forwards to all peers except `origin`,
   with a seen-set (`id` + short TTL) to stop loops. Works without the Mac but needs a
   connection registry on Android, peer discovery between two phones, and careful loop fencing.

## Work items

Android:

- `WebSocketUtil`: replace the single socket with a map `deviceId -> session`
  (`connect(device)`, `sendTo(deviceId, msg)`, `broadcast(msg)`), keep `isConnected()` as
  "any session alive" so the existing callers keep working.
- `ClipboardSyncManager`: dedupe on `(origin, text)` with a ~10 s TTL instead of two strings;
  ignore `origin == self`; never rebroadcast a text that arrived from another device.
- Outbound payload: add `origin`, `id`, `ts`; apply last-writer-wins on `ts` for simultaneous
  copies.
- UI: device list with per-device clipboard toggle, plus a "send to all / send to one" choice
  for the tile and share sheet. Decide whether multi-device is Plus-gated.
- `ConnectedDevice`: add a stable `id`; persist a list instead of `lastConnectedDevice`.
- BLE: keep it as an optional single-peer fallback, or drop it for clipboard in multi-device
  mode (it cannot broadcast).
- Notifications: a link arriving from another Android device still triggers "Continue browsing"
  on the receiver — probably fine, but make it configurable.

Mac:

- `sendClipboardUpdate()` → broadcast to all sessions except `origin` (drop
  `sendToFirstAvailable` for this message type; keep it for status/media where a single peer is
  intended).
- `AppState`: `device` → `devices` list; clipboard guards become per-origin or a
  `(origin, text, ts)` seen-ring so a copy from the phone does not bounce off the tablet.
- Menu bar / settings: show all connected devices; per-device names for the notification text.

## Testing notes

- Two Android devices + Mac: copy on phone → tablet + Mac get it; copy on Mac → both Android
  devices get it; nothing appears twice anywhere.
- Simultaneous copies (phone and tablet within the same second): both converge on the higher
  `ts` without a loop.
- Mixed versions: old Android app + new Mac → old app stays single-device but must not receive
  duplicates.
