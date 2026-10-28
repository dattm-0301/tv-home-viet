# TV Home Việt contributor instructions

This branch is an independent launcher fork of Gathin23/cleanyourtv. It is not an upstream CleanYourTV contribution. Network widgets and the customized launcher are intentional. Upstream debloat lists and automatic Home takeover scripts are not part of this branch.

- Read README.md, THIRD_PARTY.md, PRIVACY.md and docs/SECURITY-REVIEW.md before changing behavior.
- Keep upstream MIT attribution, font licenses, and the lunar dataset’s personal/non-commercial terms. Never describe the entire distribution as MIT-only.
- Keep signing keys, private TV backups, precise personal coordinates, Wi-Fi names and other device/account identifiers out of source, history, screenshots and release assets.
- Use explicit device serials, back up first, preserve stock Home and data. Do not disable or uninstall system packages as part of launcher setup.
- Run JDK 17 with Android SDK 34: in launcher/, ./gradlew testReleaseUnitTest lintRelease assembleRelease. Lint must have zero errors; document inherited warnings accurately.
- Emulator results do not prove hardware HDMI, vendor Home allowlisting, remote buttons or startup behavior. State the package identity and device used for any physical test.
- Keep main as the upstream reference; develop on a separate branch. Do not automatically merge upstream changes or send pull requests to Gathin23.
