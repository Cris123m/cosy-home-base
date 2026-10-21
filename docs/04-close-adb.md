# Close ADB when done

Network ADB on port 5555 is plaintext. While it is open, any device on the LAN that the TV has authorised (or any device at all, on firmware with `ro.adb.secure=0`) can get a shell. Close it after every session.

## On the TV

- Finnish: **Asetukset -> Järjestelmä -> Kehittäjäasetukset**
  - **USB-vianetsintä -> OFF**
  - **ADB-vianetsintä -> OFF**
  - **Peruuta USB-vianetsinnän valtuutukset** (revoke USB debugging authorisations) -> OK
  - **Pysy käynnissä -> OFF** (optional, saves the OLED panel)
- English: Settings -> System -> Developer options -> USB debugging OFF, ADB debugging OFF, Revoke USB debugging authorisations, Stay awake OFF.

The exact Finnish wording of the revoke item may differ slightly on this firmware; it is near the USB debugging switch.

Then reboot the TV (hold the power button on the remote -> Restart / "Käynnistä uudelleen").

## Verify from the PC

```powershell
python -I tools\adb-closed.py 192.168.8.202
```

Only **"Connection refused"** proves the port is closed. A timeout or "No route to host" proves nothing.

## Next session

Developer options stay visible, so only step 2 of [02-tv-first-setup.md](02-tv-first-setup.md) is needed, and the "Allow USB debugging?" prompt must be accepted again (the authorisations were revoked).
