# Emulator fixtures

Eight separate sample apps with original geometric banners, no network permission and no media service. They exist only to demonstrate pin / group / rename / reorder / launch in screenshots. These apps are not in the launcher APK.

Build from repo root: `./launcher/gradlew -p demo assembleDebug`. Install the generated APKs only on a disposable emulator. `seed.py emulator-SERIAL` configures sample pins/groups in the **debug** launcher; it refuses real-device serials. Screenshots must be captioned as sample apps, not claimed as working streaming services.

The library fixture also accepts `--ez security_probe true` when started through ADB. It attempts theme-provider read and internal clock launch, and logs the denial results under `TVHOME_SECURITY_PROBE`; this verifies ordinary-app boundaries without root.
