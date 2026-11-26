# Multi-device fork

Unofficial, community fork of AirSync that lets one Mac talk to several Android devices at once (for example a phone and a tablet). **This fork is not affiliated with, endorsed by or supported by the AirSync author.** All credit for AirSync goes to Sameera Wijerathna ([sameerasw](https://github.com/sameerasw)).

> **Note:** The features added in this fork were developed with AI assistance and have not been tested thoroughly. There may be unintended bugs, and support from the fork maintainer ([@tarun0](https://github.com/tarun0)) is not guaranteed.

- Upstream Mac app: https://github.com/sameerasw/airsync-mac
- Upstream Android app: https://github.com/sameerasw/airsync-android
- Official website: https://sameerasw.com/AirSync

## Source only, no builds

This repository is **source code only**. There are no official or unofficial builds from this fork: no APK, no `.app`, no `.dmg`, no GitHub Releases, no CI artifacts, and none will be published. Please do not redistribute builds of this fork either; upstream's license (`LICENSE`, "Additional Terms: Modified Build Redistribution Restriction", and `COMMERCIAL-EULA.txt`) forbids publishing modified builds.

If you want the Plus/Pro features, either buy/support them from the original author (see the official website above), or compile the app yourself from source. Upstream's README describes the self-compile path under "Building Locally": select the `AirSync Self Compiled` scheme in Xcode and archive. This fork adds nothing to that mechanism and does not change how licensing works; the multi-device features are behind the existing Plus check (`AppState.shared.isPlus`), exactly like upstream's Plus features, so builds that are not self-compiled still need the author's license.

## What is added (Android side)

- Clipboard payloads carry origin and id so the Mac hub can relay between phones without echo; echoed or same-text updates are dropped before apply, history and toast.
- Replay of active notifications to the Mac after connect (flagged as replay, at most 30, oldest first).
- Background clipboard capture without an Accessibility service (rooted devices only, off by default): a small helper (`tools/clipbridge/ClipWatch.java`, compiled into `app/src/main/assets/clipwatch.dex` by `tools/clipbridge/build.sh`) runs as the shell uid.
- Reconnect exponential backoff with jitter, optional Wi-Fi whitelist for reconnects, idle media bar hiding, shorter clipboard confirmation pill.

Design and status details: [multi-device-clipboard.md](multi-device-clipboard.md).

## Build (Android)

The project links `island-sdk` / `island-sdk-compose` from `../essentials` (see `settings.gradle.kts`), so that checkout must sit next to this repository.

```bash
ANDROID_HOME=/path/to/android-sdk ./gradlew assembleDebug
```

Install the resulting debug build yourself (for example with `adb install`); nothing is distributed from this repository.

## Operational notes

- The Mac app of this fork is required for the multi-device features.
- Rebuild `clipwatch.dex` with `tools/clipbridge/build.sh` if you change the helper (needs `android.jar`; set `ANDROID_JAR` if needed).

## Status and limits

Tested with a phone and a tablet connected to one Mac. Expect rough edges.

## Architecture overview

- The Mac is the hub. Each phone keeps its own WebSocket session to the Mac; the first to connect is Primary, later ones join as Secondary without displacing it. Primary-only features: macOS notification banners, media control, calls, file browser, remote control, wake, Quick Share send and the single-device ADB target.
- Clipboard messages carry \`origin\` (stable per-install id), \`id\` (uuid) and \`ts\`; the Mac relays a copy to every phone except the origin, with a short-TTL seen-ring against echoes. Older peers ignore the extra keys.
- Encryption: one shared symmetric key for all sessions (per-device keys are not implemented).
- Mac side: \`Core/MultiDevice/\` (\`DeviceStore\`, per-phone ADB state), \`NativeMirrorSession\` for per-device mirrors, and changes in \`WebSocketServer*\` (routing of secondary frames), \`AppState\` and the home screen sections. Files touched are small deltas on top of upstream; rebasing onto new upstream releases is mostly conflict-free except around \`WebSocketServer+Handlers.swift\`, \`AppState.swift\` and \`SidebarView.swift\`.
- Full write-up with line-level notes: [multi-device-clipboard.md](multi-device-clipboard.md).

## Testing notes

Hardware-tested with one Mac and two Android devices (a phone, "Phone A", and a tablet, "Tablet B") connected simultaneously. Not tested: three or more devices, non-Samsung/OEM variations, BLE with several peers, Windows/other clients. No automated tests were added beyond upstream's.

## Roadmap / ideas

Per-device encryption keys, per-phone Quick Share send, banners for secondary notifications, one native desktop session per phone, a Windows client, and an "extensions" framework (feasibility only, nothing built).

## Contributing and upstream sync

\`\`\`bash
git remote add upstream https://github.com/sameerasw/airsync-android.git
git fetch upstream
git rebase upstream/main   # or merge
\`\`\`

Changes that are not specific to multi-device are better sent to the upstream project.

## Licensing and credits

Derived from sameerasw/airsync-android; the upstream \`LICENSE\` (Mozilla Public License 2.0 with "Additional Terms: Modified Build Redistribution Restriction") applies to this fork and must be kept. Per those terms, modified builds must not be published, which is why this repository is source only. New files in this fork are offered under the same terms. Not affiliated with the author.
