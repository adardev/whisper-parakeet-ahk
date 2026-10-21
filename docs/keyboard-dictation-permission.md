# Nemotron Keyboard microphone permission

The double-space dictation gesture was reaching `VoiceRecordService`, but the
installed `com.nemotron.keyboard` package had `android.permission.RECORD_AUDIO`
denied (`appops` reported `ignore`). The service did not check this before
starting its microphone foreground service, and it suppressed Toast messages
while the keyboard input connection was active. That made the gesture appear to
do nothing.

The keyboard now checks the runtime permission before starting dictation. If it
is missing, it opens `KeyboardMicrophonePermissionActivity`, which explains why
the permission is needed and lets Android show its normal microphone prompt.
If permission is revoked during recognition, the service stops retrying the
insufficient-permission error and opens the same screen. The permission screen
is packaged only in Nemotron Keyboard; Core excludes it.

There is no separate Android "background microphone" runtime permission for
this user-initiated keyboard dictation flow. Nemotron Keyboard already declares
`FOREGROUND_SERVICE_MICROPHONE`; `RECORD_AUDIO` is the user-controlled runtime
permission. Grant it only if you want voice dictation. The feature otherwise
does not request or use the microphone.

On 2026-10-21, the A54 reported `RECORD_AUDIO: granted=false` and app-op
`ignore` for `com.nemotron.keyboard`. The updated keyboard APK built and
installed with `adb install -r`, preserving package data. The permission remains
denied until the user accepts Android's prompt. Trigger double-space on the
keyboard and tap **Permitir micrófono**; after approval, return to the keyboard
and repeat the gesture. The permission prompt was not granted automatically.

Build and ownership checks:

- Gradle debug build succeeded.
- Split-package build succeeded and verified all three output APKs use the same
  signer.
- Manifest inspection confirmed the permission activity and recording service
  are in the Keyboard APK.
- The Whisper model remains packaged only in Keyboard.
- Only `com.nemotron.keyboard` was installed on the phone for this change.
