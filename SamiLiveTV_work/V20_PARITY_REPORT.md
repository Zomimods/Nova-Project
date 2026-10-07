# SAMI Live TV — V20 repair report

## What changed

- Added the missing Media3 DASH version-catalog alias referenced by the Android
  Gradle build.
- Repaired `extract_v20.py`: it now resolves input/output paths relative to the
  script, supports `--check`, and reports the JavaScript function count
  correctly.
- Updated the V20 source's boot sequence to isolate optional startup failures.
  Regenerated the bundled HTML from that source while retaining the source's
  safer `localStorage` handling and null checks.
- Added V20 source/asset parity checks to both Android CI and release workflows.
- Hardened the WebView: disabled unnecessary filesystem access and limited
  granted web permissions to audio capture only.
- Corrected Firebase setup/release notes: the active V20 WebView uses its
  bundled Firebase Web configuration and Realtime Database; the retained
  native Firestore adapter and its rules are not the active V20 data path.

## Verification performed

- `python3 extract_v20.py` regenerated the bundled V20 HTML successfully.
- `python3 extract_v20.py --check` passed after regeneration.
- The 19 HTTP channel URLs resolve to 16 hosts, and every host is present in the
  Android cleartext allowlist.
- Confirmed the Gradle catalog now defines the DASH alias used by
  `app/build.gradle.kts`.
- Confirmed the active WebView path writes comment records containing a UID and
  display name; the deployed Realtime Database rules are not included in this
  archive and could not be inspected.

## Not verified

The Gradle unit-test task was attempted, but Android SDK Platform 34 is not
installed/configured in this workspace (`ANDROID_HOME` and `ANDROID_SDK_ROOT`
are unset and `sdkmanager` is unavailable). The task did not complete here.
No APK build, Firebase Emulator rules test, or on-device playback/auth test is
claimed. See `RELEASE_READINESS.md` for the remaining release checks.
