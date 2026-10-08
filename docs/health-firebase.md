# Health Connect → Firebase

Nemotron reads Health Connect on the phone and writes one snapshot per day to
Firebase Firestore:

```text
/health/{anonymousFirebaseUid}/snapshots/{yyyy-mm-dd}
```

Firebase Authentication must have **Anonymous** sign-in enabled. The app uses
the dedicated Firebase project (`chat-2bd24`) and stores the anonymous refresh token in Android
encrypted preferences. The Web API key can be changed from Settings if needed.

Add this match to the Firestore rules (the NAS uses Admin SDK, so it bypasses
client rules):

```text
match /health/{uid}/snapshots/{date} {
  allow create, update: if request.auth != null && request.auth.uid == uid;
  allow read: if false;
  allow delete: if false;
}
```

The NAS/server should read the data with Firebase Admin credentials. The old
LAN webhook and Syncthing health-file path are no longer used.
