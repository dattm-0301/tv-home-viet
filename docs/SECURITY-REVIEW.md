# Public preview review — 2026-10-28

Scope: this sanitized source tree and the signed 1.0.1 APK, not the private TV checkout or its full history. This is a local engineering review, not an independent penetration-test certificate.

## Changes made for publication

| Area | Result |
|---|---|
| Personal data | Precise home coordinates, personal filesystem paths, session notes / dumps, ADB keys, screenshots of the real TV and private Git history excluded. Default weather is central Hanoi. |
| Location | No precise-location permission. Coordinates are entered explicitly; changing them invalidates weather cache. Latitude/longitude reject NaN, infinity and out-of-range values. |
| Transport | App explicitly disallows cleartext HTTP, uses platform TLS validation, bounded response sizes and connection/read timeouts. No custom certificate bypass. |
| Android components | Clock activity is private. Dream/accessibility services have Android binding permissions. Exported theme provider requires WRITE_SECURE_SETTINGS and only allows bounded theme filenames. |
| Runtime permission probe | A separate ordinary sample app, with visibility of the launcher and no privileged permission, was denied both theme-provider access and internal clock activity launch: theme_denied=true, clock_denied=true. |
| App removal | App menu delegates to Android confirmation; no silent privileged uninstall. Installer contains no package-disable or system-uninstall step. |
| Installation | Explicit device serial and application-ID check; existing APK/config snapshot before install. Fresh install, same-signature update and theme import exercised. An unrelated demo APK was rejected before install. |
| Release signing | Dedicated release key, stored outside the repo with owner-only permissions. Release is not debuggable and has a different application ID from the private TV build. No signing password/private key is published. |
| Licensing | MIT upstream attribution retained; all font OFL notices retained and font metadata checked. Photo collection excluded. Lunar dataset's personal/non-commercial condition documented prominently. |
| CI | Read-only contents permission, pinned action commits, no signing secret, no pull_request_target workflow, Gradle distribution checksum pinned. |

## Checks and outcomes

- JDK 17 / Android SDK 34: debug + unsigned release build succeeded; signed release verified with apksigner and installed successfully.
- 169 unit tests passed (including coordinate boundary and timestamp-offset checks), zero failures/errors.
- Full Android release lint: **0 errors, 175 warnings**. Remaining warnings are largely existing hardcoded text/localization, unused resources and layout suggestions; they were not suppressed to claim a warning-free build.
- Gitleaks 8.30.1 source scan: no detected secrets. A clean Git history is created for this public distribution; the private repository history is not reused.
- 27 resolved runtime Maven dependencies queried against OSV: no advisories returned at review time. This does not cover every build-tool or future vulnerability.
- Android 14 Google TV arm64-v8a emulator, 1920×1080 / 320 dpi: actual signed release launched; network weather and market cache rendered; D-pad menu, app reorder, settings/font switch and app library exercised. The forecast rain bars flex to preserve percentage labels; the clock forecast row was enlarged after visual testing. Demo apps are original sample fixtures, not streaming services.

## Important limits

- Hardware HDMI, Xiaomi backlight/middleware, power-cycle startup, vendor remote keys and system screensaver activation are not proven by an emulator. The private predecessor ran on Xiaomi L55MA-EP Android 10; this new public package identity still needs testing on that physical TV.
- On this Google TV emulator image, requesting the Home role did not consistently change the resolved Home away from the Google launcher. Direct app launch works. Do not advertise automatic Home replacement on all Google TVs; keep stock Home enabled and test firmware-specific behavior.
- Quotes depend on external HTTPS feeds. Observation/edit times are not guaranteed exchange ticks or exact effective timestamps. Fallback gold is labeled Tham khảo; stale responses/cache are marked Cũ. Provider failures and changed schemas remain possible.
- Local APK/config copies are not full restorable firmware/account backups. A signature change or Android version downgrade rule may prevent direct rollback; stock Home is the recovery route.
- Source/license review found a **non-commercial restriction on the lunar table**. Public redistribution with the notice is allowed by its retained terms, but commercial rights were not obtained. Do not describe the whole distribution as MIT.

## Reproduce

```sh
cd launcher
./gradlew testReleaseUnitTest lintRelease assembleRelease
cd ..
gitleaks dir . --redact --max-target-megabytes 2
```

Use a disposable emulator and `demo/` for screenshots/probes. Never run sample seeding against the household TV. See PRIVACY.md, THIRD_PARTY.md and SETUP.md.

## Android references

- [Exported components](https://developer.android.com/privacy-and-security/risks/android-exported)
- [Cleartext communications](https://developer.android.com/privacy-and-security/risks/cleartext-communications)
- [Protecting signing keys](https://developer.android.com/studio/publish/app-signing)

## Physical TV follow-up — 2026-10-28

The private `com.ts.launcher` identity, signed with its existing key, was upgraded in place on a Xiaomi TV identifying itself as MiTV-MTEQ0. HDMI 1 and HDMI 2 opened the correct Xiaomi source player, and Home returned to the custom launcher. The TV reported a cached PS5 CEC device as connected while its parent HDMI port was in standby; the menu now uses the physical port, displays standby explicitly, and listens for state callbacks. A standby flag is not proof of a cable or playable video on this firmware. Hot-plug transitions with an actively playing source have not been tested. This does not establish firmware Home allowlisting for the separate public package. Private screenshots, Wi-Fi names, location labels, backups and footage are kept outside this public repository.
