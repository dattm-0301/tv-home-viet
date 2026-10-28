# Changelog

## 1.1.0 — 2026-10-28

- Remote settings for city/coordinates, timezone, three Home layouts, widget visibility, color background and idle clock.
- Personal options persist separately from theme defaults; theme updates preserve TV choices.
- Keep unambiguous 24-hour time on devices using Android’s 12-hour system preference.
- Fix 8-column persistence and runtime JSON HDMI mapping.
- Weather and dealer caches cannot appear under a newly selected location/dealer. Disabled widgets stop their network requests.
- Guarded release updater verifies SHA-256 and preserves app data; backups include personal JSON.
- Document customization, update/recovery limits, source compatibility and developer release flow.

## 1.0.1 — 2026-10-28

- HDMI-CEC entries now read physical-port state rather than a cached device connection.
- Standby has an explicit uncertain label; connection callbacks refresh the open menu.
- Home no longer presents the last selected input as the active input.
- Verified switching HDMI 1/2 and returning Home on a Xiaomi TV using the private package identity.

## 1.0.0 — public preview

- Vietnamese Home dashboard, Gregorian / lunar date, weather and market widgets.
- App groups, pin / reorder / hide / rename, TV remote navigation and clock screensaver.
- Independent public package ID; neutral default weather location; configurable coordinates.
- Gradient screensaver without the photo collection.
- Clear stale / reference quotes, guarded fuel product selection and corrected timezone parsing.
- HTTPS enforcement, no precise-location permission, protected theme import.
- Setup with APK/config backup and no automatic removal of the stock launcher.
- Fixed color selector resource types; isolated demo and device-compatibility claims.
