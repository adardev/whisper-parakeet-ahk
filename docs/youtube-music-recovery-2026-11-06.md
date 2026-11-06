# YouTube Music recovery — 2026-11-06

## What was wrong

The installed ReVanced clone (`app.revanced.android.apps.youtube.music`) was
closing during startup. Android logs showed microG refusing extended access for
that package/signature (`GmsAuthProvider: Not granting extended access`). This
is separate from the earlier microG GServices SQLite downgrade issue, which was
repaired by preserving the old database and letting microG recreate the active
`gservices.db`.

## Working recovery

Install the official Google package alongside the clone. This does not remove
ReVanced or alter microG data:

```powershell
adb install -r artifacts/youtube-music/YouTube-Music-9.41.53.apk
adb shell monkey -p com.google.android.apps.youtube.music 1
```

The official package is `com.google.android.apps.youtube.music`, version
9.41.53, arm64-v8a. It was downloaded from APKPure's version page and its
SHA-256 matched the hash displayed there:

`757653480af2177ffd4ddcf054e7cbc023986fbb282267e0e09e56313d922a18`

APKPure also reports the Google signing certificate SHA-1 as
`afb0fed5eeaebdd86f56a97742f4b6b33ef59875`.

## Device verification

On the Galaxy A54, `adb install -r` returned `Success`. Launching the package
with `monkey` succeeded; Android reported
`com.google.android.apps.youtube.music/.activities.MusicActivity` as the
resumed activity and a live process ID. This confirms the official app opened
and remained alive through that check. Playback/account sign-in was not verified
in this check.

The ReVanced clone was left installed. Its microG extended-access/signature
problem remains unresolved; do not treat its successful build/install as proof
that it can authenticate. Use the official package for now. The official APK is
included here so the recovery can be repeated without another download.

## Source

- APKPure version page: https://apkpure.net/youtube-music/com.google.android.apps.youtube.music/download/9.41.53
