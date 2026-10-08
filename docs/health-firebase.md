# Samsung Health Data SDK → Firebase

Nemotron reads Samsung Health through the official Samsung Health Data SDK and writes one snapshot per day to
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

Before connecting, Samsung Health must be installed and its developer mode for
Samsung Health Data SDK must be enabled. On the phone: Samsung Health → ⋮ →
Settings → About Samsung Health → tap the version repeatedly → Developer mode
(Samsung Health Data SDK) → enable data reading. Then use the app's Samsung
Health setup action to approve the requested read permissions.

The NAS/server should read the data with Firebase Admin credentials. The old
Health Connect, LAN webhook, and Syncthing health-file paths are no longer used.
