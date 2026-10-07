# Health Connect → Firebase

Nemotron reads Health Connect on the phone and writes one snapshot per day to
Firebase Realtime Database:

```text
/health/{anonymousFirebaseUid}/{yyyy-mm-dd}
```

Configure Firebase Auth with **Anonymous** sign-in enabled. Configure the app
under Settings → Health Connect → Firebase with the Web API key and Realtime
Database URL. The API key is not a server secret; the anonymous refresh token
is stored in Android encrypted preferences.

Use rules equivalent to:

```json
{
  "rules": {
    "health": {
      "$uid": {
        "$date": {
          ".read": "auth != null && auth.uid == $uid",
          ".write": "auth != null && auth.uid == $uid"
        }
      }
    }
  }
}
```

The NAS/server should read the data with Firebase Admin credentials. The old
LAN webhook and Syncthing health-file path are no longer used.
