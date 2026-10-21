# Original prompt

This is the full prompt used to start this project with an AI coding agent (Claude Code), saved verbatim (only the repo URL and folder name are updated to the project's current name, tv-launcher).
To redo the whole setup (for example after a factory reset or on a new TV), see "How to run it again" at the bottom.

---

## Prompt (verbatim)

```text
I will be doing the "cleanyourtv" to my TV (https://cleanyour.tv/ or https://github.com/Gathin23/cleanyourtv ). I want the whole setup process and restore information and everything to be stored to my personal github. I will create a new repo there to which you shall connect. https://github.com/samipuro/tv-launcher

Add to the repo: Save the entire initial prompt with instructions on how to perform it again. Save any specific configuration we make or specific wallpapers or apps we install so we can install them later. Save also the full restore/uninstall configuration or instructions to the repo. Basic idea of the repo is to be the backup for setup and restore.


First time initial setup (document these):
1. must enable dev mode; Asetukset -> Järjestelmä -> Tietoja -> Käyttöjärjestelmän koontiversio -> paina OK 7 kertaa
2. must enable USB debugging: Asetukset -> Järjestelmä -> Kehittäjäasetukset -> USB-vianetsintä + ADB-vianetsintä + Pysy käynnissä -> ON


Original instruction from cleanyourtv:

Before you start, on the TV: Settings → Device Preferences → About → press OK on "Build" 7 times,
then go back → Developer options → turn on USB debugging (and Network/ADB debugging if your TV
shows it). Keep the TV on. Some boxes cut power to their USB port while USB debugging is on, so a
USB speaker or drive plugged into it goes dead until you switch debugging off again.

---

I want to debloat my Android TV and replace its launcher with a clean Apple TV style home screen.
My TV model is: Sony XR-65A80J 65" 4K Ultra HD OLED. It has connected Sony Bravia Theatre Bar 8 7.0.2 -soundbar. Work on the real device, step by step, and verify everything you do with screenshots pulled from the TV. Ask me before anything that depends on my preferences.

Connect
- Find the TV on my network (hostname, ARP table, or ask me for the IP) and connect over ADB on port 5555. IP is 192.168.8.202 and name Living room tv.

- If the stock adb binary fails with "No route to host" on macOS even though the port is open, that is
  macOS Local Network privacy blocking adb. Don't debug it: use the Python `adb-shell` package in a venv
  as the ADB client instead. If its file push hangs on larger files, transfer files as base64 chunks
  through the shell and verify the md5.
- If port 5555 stays closed, the TV may only offer Wireless debugging (Google TV sets often do): look for
  `_adb-tls-connect._tcp` with `dns-sd -B` or `adb mdns services`, ask me for the code and port under
  Developer options > Wireless debugging > Pair device with pairing code, then `adb pair`, `adb connect`
  and `adb tcpip 5555`. Android switches Wireless debugging off whenever Wi-Fi drops.
- Tell me when the "Allow USB debugging?" prompt will appear on the TV so I can accept it.
- If `adb install` hangs on Android 14 it is waiting for an on-screen confirmation; push the APK and run
  `pm install -r` over the shell instead.

Back up first
- Save the full package list, disabled-package list, getprop output and all settings to a backup folder
  before changing anything, and write a restore script that re-enables everything you disable.
- Ask me what is plugged into the TV's USB ports. Some devices cut their power while USB debugging
  is on, so warn me, and remind me to switch debugging off once you're done.
- When you finish, close network ADB instead of leaving it open: switch USB debugging and
  Network/ADB debugging off on the TV, revoke the USB debugging authorisations, reboot, and check
  with `tools/adb-closed.py <tv ip>` that the port refuses the connection. Don't trust `nc` on macOS:
  Local Network privacy can block it too, and its "No route to host" proves nothing; only "Connection
  refused" does. Port 5555 is plaintext, and on firmware with `ro.adb.secure=0` any device on the LAN
  can get a shell.

Debloat
- List installed packages and sort them into: manufacturer telemetry/analytics, manufacturer content
  and ad services (PatchWall, recommendation rows, content providers), background services I probably
  don't need (share/cast duplicates, cleaners), preloaded apps, and streaming apps.
- Disable the first categories with `pm disable-user --user 0`, never uninstall system packages.
- Never disable what the TV needs to work: the TV app and input services that play HDMI and the
  tuner, the input/source menu, remote control and Bluetooth pairing services, the on-screen
  keyboard, Google Play Store and Play Services. Manufacturer package names often don't say what a
  package does: check its activities and services with `dumpsys package` first, and ask me if it's
  still unclear.
- Before touching streaming apps, voice assistant, Chromecast, Miracast or the update service, ask me
  which ones I use with a checklist question and disable only what I don't tick. I need at least TV, SmartTube, Spotify, Netflix, Prime Video, YLE Areena, Ruutu, F1 TV, Elisa Viihde, MTV Katsomo and also specific apps as "Send files to TV", "APK Mirror installer" (NO NOT UPDATE THIS TO LATER VERSION EVER NEVER EVER!), File Manager+.
- Run `pm` commands one per shell call, chained commands may silently stop after the first.
- Don't launch a package whose name says factory, production, AT or fac menu (`com.cvte.tv.hkcfac`,
  `com.at.factory.*`, `mediatek.factorymenu.ui` and the like) to see what it does, even read-only:
  on Haier/CVTE boards opening one flips a factory-mode flag that kills the Input key and CEC, and
  makes the next boot disable Play services, Netflix and Chromecast - which the shell then cannot
  re-enable. `dumpsys package <name>` tells you what a package is without starting it, and
  `docs/TROUBLESHOOTING.md` has the way out if one has been opened.
- Disable in small batches and screenshot between them: a batch can take something load-bearing with
  it and you cannot tell which.
- After the last batch, reboot once and compare `pm list packages -d` with what you disabled: some
  firmware re-enables packages at every boot from its own list (TCL Android 9 does this for its user
  center). Note those in the list header instead of disabling them again after each boot.
- Offer me ad-blocking DNS as an optional extra: on Android 9 and newer, Private DNS set to an
  ad-blocking resolver (`settings put global private_dns_specifier dns.adguard-dns.com`, then
  `settings put global private_dns_mode hostname`). Tell me it doesn't block YouTube ads, check an app
  still plays afterwards, and note the undo (`private_dns_mode opportunistic`) in the README.

Launcher
- Check that the TV accepts APKs before building anything. Some China-market sets answer every
  sideload with `INSTALL_FAILED_VERIFICATION_FAILURE` — `adb install`, `pm install`, a hand-started
  installer and even the manufacturer's own APKs all fail — and others refuse any package that
  declares `android.intent.category.HOME` unless it carries the manufacturer's signature. If you
  hit either, do the debloat half, say plainly that the launcher half is impossible here, and stop
  retrying installs. The matching `examples/` header says which case your TV is.
- Don't install a third-party launcher that charges for basics like wallpapers. Build a small native
  Android TV launcher instead (Java, AndroidX RecyclerView, minSdk matching the TV, debug build is fine),
  install it over ADB, set it as the default home and disable the stock launcher. If several apps claim
  the HOME intent, list them with `cmd package query-activities -a android.intent.action.MAIN -c
  android.intent.category.HOME` and set the default with `cmd package set-home-activity`. Those two
  commands can both name your launcher while Home still opens something else: on some Google TV builds
  `set-home-activity` prints `Success` and changes nothing, and setupwraith's `.RecoveryActivity`
  keeps the key. Confirm with `dumpsys activity activities | grep mFocusedApp` before telling me the
  takeover worked.
- Before disabling the stock launcher, check which other apps claim HOME: on Google TV the setup wizard
  (`com.google.android.tungsten.setupwraith`) takes over even after the stock launcher is disabled, and
  the matching `examples/` header says what to do. Before disabling setupwraith, run
  `dumpsys wifi | grep -o 'cname=[^ ]*'`: a Wi-Fi network saved during first setup belongs to it, and
  Android deletes that network the moment it is disabled, which takes the TV off Wi-Fi and ADB. If it
  does, ask me to forget the network and re-join it in Settings first.
- On Google TV the stock launcher also answers the remote's app buttons (Netflix, YouTube and the like),
  Input and Settings/dashboard buttons, so after disabling it ask me to press each one, and remap any that
  do nothing with the launcher's key services: `input_key_keycodes` and `app_keys` with
  INPUT_KEY_SERVICE=1, `settings_key_keycodes` with HOME_KEY_SERVICE=1 (`config.xml` or the theme).
  A button the TV logs as `KEY_UNKNOWN` or `KEY_PROGRAM` cannot be matched by keycode, since every
  unmapped button shares it: read the scancode with `getevent -lt` and use `settings_key_scancodes`,
  which only `config.xml` carries (a theme can set keycodes, not scancodes).
- On Android 11 and newer the launcher must declare `<queries>` for `android.intent.category.LEANBACK_LAUNCHER`,
  or package visibility silently hides every app and the grid comes up empty; target a recent SDK, some
  Google TV 14 sets refuse older targets at install.
- Design, Apple TV 4K style: pitch black background; a dock row at the bottom of the screen inside a
  slightly lighter rounded band holding the four apps I pick (ask me which, and the order); the rest of
  the apps in a 4-column grid below that scrolls up when I press Down; one translucent "glass" highlight
  that springs from tile to tile instead of per-tile borders; the focused tile scales up slightly and
  shows its name underneath; above the dock, the focused dock app's name in large light type with an
  ambient glow in a colour sampled from its banner; frosted pill buttons top right for Inputs, Wi-Fi
  (showing the network name) and Settings; a greeting, a large thin clock and the date top left in a
  downloaded open-licence typeface such as Outfit. Inputs opens a compact glass dropdown under the pill
  listing the HDMI ports, AV and Live TV, switching via the TV input framework.
- Make D-pad navigation deterministic: Up/Down move exactly one row, Up from the dock goes to the top
  bar, the ends of the top bar don't fall through to tiles. Remember the last focused tile.
- Keep it light: the TV has about 1 GB of RAM. No blur, no video, no network polling.

Verify
- After each change, inject remote key presses over ADB, take a screenshot from the TV and look at it.
  Fix what you see before moving on.
- Live TV and HDMI screenshots come back empty (0 bytes); that is the TV, not a broken debloat. Check
  `dumpsys activity activities | grep topResumedActivity` instead (see `docs/TROUBLESHOOTING.md`).
- If a remote button relies on an accessibility helper, check that the service is enabled and bound
  for the current user, then ask me to press the real button; an injected key is not proof (see
  `docs/TROUBLESHOOTING.md`).
- When done, give me a restore script, a build-and-install script, and a README that explains how to
  re-enable any app and how to change the dock order or the wallpaper.

----------

The above was the basic prompt from https://cleanyour.tv/

But I want to make customizations to this basic too.
- adguard dns as done here: https://github.com/majnioui/CleanXiaomiTV
- visuals as in here: https://github.com/oliverjwroberts/cosy-home-tv
- I guess I need to download adb also? https://developer.android.com/tools/adb

ALL THIS MUST BE DOCUMENTED.

FIRST, START MAKING A PLAN. Ask me questions.
```

---

## Follow-up answers given to the agent

These are the saved setup choices, updated to reflect the final configuration. Use them when re-running; they take precedence over the original prompt above. The exact launcher settings are in [../launcher/cosy.properties](../launcher/cosy.properties).

- Working folder for the repo: `D:\Server\www\tv-launcher`. Random tool installs (adb, Android SDK): `D:\Server\Tools\`.
- Launcher: start from **cosy-home-tv as is** (changing scene, weather), location **Vantaa**, then fine-tune the look iteratively. It should look and feel very nice and premium. This overrides the "Apple TV style / no network polling / no blur" parts of the cleanyourtv prompt.
- Dock (7 apps, in order): TV, Netflix, SmartTube, Spotify, F1 TV, Yle Areena, Ruutu.
- Remote: the **YouTube** button should open **SmartTube**. Netflix, Prime Video and other app buttons stay as they are.
- Repo is public. No APK files are kept here; app versions and sources are in [05-apps.md](05-apps.md). Play Store auto-updates stay enabled, including for F1 TV and APKMirror Installer.
- Nothing is plugged into the TV's USB ports.
- Soundbar (Sony Bravia Theatre Bar 8) is on HDMI eARC: keep all HDMI-CEC / eARC / Bravia Sync packages.
- Must stay enabled: voice assistant, Chromecast, screen mirroring (Miracast), Sony software/firmware update.
- AdGuard DNS: default `dns.adguard-dns.com`.
- Ask before installing anything on the PC.
- Docs in English. TV language stays Finnish, so docs give the Finnish menu paths.

---

## How to run it again

1. Do the TV first-time setup: [02-tv-first-setup.md](02-tv-first-setup.md).
2. Install the PC tools: [01-prerequisites.md](01-prerequisites.md).
3. Clone this repo and open an AI coding agent (Claude Code) in it.
4. Tell the agent: "Read `README.md` and `docs/`, then re-apply this setup to the TV at 192.168.8.202 step by step, verifying with screenshots. Back up first." The debloat list, launcher source, its config, and app versions and sources are all in this repo. Download app APKs from their sources as needed.
5. Without an agent, follow the manual steps in [README.md](../README.md) ("Re-apply from scratch").
