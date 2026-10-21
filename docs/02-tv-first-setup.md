# TV first-time setup

TV: **Sony XR-65A80J** (65" 4K OLED, 2021), Google TV / Android 12 (API 31), firmware build `STT2.230505.001.S136 682241`, connected by **Ethernet**. Name on the network: **Living room tv**, IP **192.168.8.202**.
Soundbar: **Sony Bravia Theatre Bar 8** on **HDMI eARC**.
TV menu language is **Finnish**; the English path is given for reference.

Do these on the TV before connecting from the PC.

## 1. Enable developer mode

- Finnish: **Asetukset -> Järjestelmä -> Tietoja -> Käyttöjärjestelmän koontiversio -> paina OK 7 kertaa**
- English: Settings -> System -> About -> Android TV OS build -> press OK 7 times

The TV says you are now a developer.

## 2. Enable USB / ADB debugging

- Finnish: **Asetukset -> Järjestelmä -> Kehittäjäasetukset -> USB-vianetsintä + ADB-vianetsintä + Pysy käynnissä -> ON**
- English: Settings -> System -> Developer options -> USB debugging + ADB debugging (network) + Stay awake -> ON

Notes:

- "Pysy käynnissä" (Stay awake) keeps the TV from sleeping during the session. Turn it off again when done.
- Some TVs cut power to their USB ports while USB debugging is on. Nothing is plugged into this TV's USB ports, so it does not matter here, but a USB drive or speaker would go dead until debugging is switched off.
- Keep the TV on during the whole session.

## 3. Accept the PC

The first time the PC connects (`adb connect 192.168.8.202:5555`), the TV shows **"Allow USB debugging?"** (Finnish: "Sallitaanko USB-vianetsintä?") with the PC's RSA key fingerprint. Tick "Always allow from this computer" and press OK. If the prompt is missed, run `adb disconnect` and `adb connect` again.

## 4. When finished: close ADB

See [04-close-adb.md](04-close-adb.md). Port 5555 is plaintext ADB: do not leave it open.
