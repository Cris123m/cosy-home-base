# Plan

Setup checklist for this TV. The saved launcher settings are in [../launcher/cosy.properties](../launcher/cosy.properties); see [../README.md](../README.md#re-apply-from-scratch) to repeat the setup.

## Phase 0 - Repo and PC tools
- Repo cloned to `D:\Server\www\tv-launcher`; tools installed under `D:\Server\Tools\` ([01-prerequisites.md](01-prerequisites.md)).
- Upstream reference clones in `D:\Server\Tools\upstream\` (`cleanyourtv`, `cosy-home-tv`). The parts used are copied into this repo so it works without them.

## Phase 1 - Connect
- `adb connect 192.168.8.202:5555`, accept "Allow USB debugging?" on the TV.

## Phase 2 - Back up first
- Save full package list, disabled list, `getprop`, all `settings` (global / secure / system), the Home activity, Private DNS state and Wi-Fi owner to `backup/<date>/` before any change.
- Restore script written and committed before the first change.

## Phase 3 - Debloat
- Start from `cleanyourtv/examples/sony-kd-65x80j` (same 2021 Sony Google TV generation), checking every package on this TV with `dumpsys package`.
- `pm disable-user --user 0` only, never uninstall. One `pm` command per shell call. Small batches, screenshot after each, reboot at the end and compare.
- Never touch: TV app and inputs (`com.sony.dtv.tvx`, tvinput.*), HDMI-CEC / eARC (braviasync*), remote / Bluetooth, keyboard, Play Store, Play Services, factory packages (never launched).
- Must stay (owner's choice): voice assistant, Chromecast, screen mirroring (Miracast), Sony software update, and the apps: TV, SmartTube, Spotify, Netflix, Prime Video, Yle Areena, Ruutu, F1 TV, Elisa Viihde, MTV Katsomo, Send files to TV, APKMirror Installer, File Manager+.
- Unclear packages are asked as a checklist.

## Phase 4 - AdGuard DNS
- Private DNS `dns.adguard-dns.com`, mode `hostname`. Does not block YouTube ads. Check that an app still plays. Undo: `private_dns_mode opportunistic`.

## Phase 5 - Launcher (iterative)
- Use the saved **Cosy Home** launcher (changing day/night valley scene, clock, weather, 7-app dock, frosted apps panel), location **Vantaa**.
- Dock: TV, Netflix, SmartTube, Spotify, F1 TV, Yle Areena, Ruutu.
- Sony adjustments: Live TV / inputs via `com.sony.dtv.tvx`, no Ambilight chip.
- Before disabling setupwraith: check the saved Wi-Fi owner (`dumpsys wifi | grep cname`).
- Confirm Home really changed (`dumpsys activity activities | grep mFocusedApp`).
- Remote buttons: test each real button. **YouTube button -> SmartTube.**
- Then fine-tune the look in rounds; each round is a commit with screenshots.

## Phase 6 - Apps
- Already installed. List versions and sources in `docs/05-apps.md` (no APKs in the repo).
- Play Store auto-updates stay enabled. APKMirror Installer is kept installed; APK files are not stored in this repo.

## Phase 7 - Close ADB
- [04-close-adb.md](04-close-adb.md). Verify "Connection refused".
