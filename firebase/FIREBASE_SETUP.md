# Firebase setup for the V20 Android app

## Active Firebase client

`MainActivity` loads the bundled V20 interface from
`app/src/main/assets/v20/index.html`. That web client uses Firebase
Authentication and the Firebase Realtime Database. Its Firebase Web
configuration is embedded in `V20.xml` and copied into the Android asset by
`extract_v20.py`.

Before release, confirm that the Firebase project in `V20.xml` is the project
you intend to use, then enable and test the authentication providers used by
the page. To change projects, update the Web configuration in `V20.xml`, run
`python3 extract_v20.py`, and verify with `python3 extract_v20.py --check`.

## Database security and privacy

- This package does not include Realtime Database rules. Review the deployed
  rules in the Firebase console and test them with the Firebase Emulator Suite
  before release.
- V20 writes user data below `users/{uid}` and uses public profiles and comment
  records. Comment records include the author's UID and display name; profile
  records may include a photo. Ensure the rules and privacy notice match this
  behavior.
- Protect moderation/admin paths such as `admins` and `bans` from client writes.
  Do not grant broad public write access to make the UI appear functional.
- `firebase/firestore.rules` is for the retained native Firestore adapter. The
  active V20 WebView does not use that adapter, and Firestore rules do not
  protect the V20 Realtime Database.

The Firebase Web API key in a client configuration is not an admin credential.
Never place service-account keys or database-admin credentials in the WebView,
APK, repository, or chat. Enforce access through Firebase security rules.

## Optional native Firebase configuration

`app/google-services.json` is optional and ignored by Git. Supplying it enables
the Google Services plugin for the retained native Android Firebase code; it
does not configure, replace, or retarget the Firebase Web client used by V20.
