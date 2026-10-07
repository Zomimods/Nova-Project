# SAMI Live TV — release readiness

## Status: not release-approved

The source fixes in this package are statically checked, but no Android APK or
device test was completed in this environment. The Firebase Realtime Database
rules used by the bundled V20 web app also need an owner-side security review.

## Verified application path

- `MainActivity` loads the bundled `app/src/main/assets/v20/index.html` in a
  JavaScript-enabled WebView.
- The active V20 page uses Firebase Web Auth and Realtime Database. Its web
  configuration is embedded in the V20 source; `google-services.json` does not
  replace or retarget that configuration.
- V20 comment records include a Firebase UID and display name, and public profile
  records can include a name and photo. Whether those records are readable
  depends on the Firebase Realtime Database rules for the configured project.
- The repository also contains a native Firestore adapter and
  `firebase/firestore.rules`, but the active V20 `MainActivity` does not use
  that adapter. Firestore rules do not protect the Realtime Database.
- This archive has no `database.rules.json`; the deployed Realtime Database
  rules could not be inspected from the source package.

## Required before release

1. Confirm the Firebase Web configuration in `V20.xml` belongs to the intended
   project. Enable the providers used by the UI and test sign-in and password
   reset on a real Android device. Google popup/redirect flows must be verified
   in the embedded WebView; do not assume browser behavior is identical.
2. Review the configured Realtime Database rules for per-user data, comments,
   `publicProfiles`, moderation/admin data, support records, and statistics.
   Test cross-user access and unauthorized writes with the Firebase Emulator
   Suite. Do not rely on `firestore.rules` to secure these paths.
3. Decide whether storing/displaying comment authors' UIDs, names, and profile
   photos matches the privacy notice and moderation policy.
4. Run the Android unit tests and assemble an APK with Android SDK Platform 34
   and Build-Tools 34.0.0. Verify playback, stream fallback, file selection,
   audio search permission, background behavior, account flows, and channel
   comments on a physical device.
5. Keep the HTTP/HLS host allowlist in
   `app/src/main/res/xml/network_security_config.xml` limited to the channel
   sources; the WebView permits mixed content for playback compatibility.

## Checks in this package

```bash
python3 extract_v20.py --check
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The V20 parity check is also part of both Android CI and release workflows.
Android SDK Platform 34 is not configured in the current workspace, so Gradle
tests and APK assembly remain unverified here.
