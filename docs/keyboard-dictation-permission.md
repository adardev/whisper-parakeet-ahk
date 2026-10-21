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
`ignore` for `com.nemotron.keyboard`. The first updated APK was installed with
`adb install -r`, preserving package data. Its permission activity then crashed
because the optimized Keyboard APK does not include an AppCompat decor class
used indirectly by its theme (`ClassNotFoundException` for
`androidx.appcompat.widget.FitWindowsLinearLayout`).

The follow-up replaces that screen with a platform `Activity`, uses Android's
platform Material no-action-bar theme, and requests the runtime permission
immediately. This version compiled and passed split/signature/manifest checks.
The phone disconnected from ADB before this follow-up APK could be installed;
device installation and permission-dialog interaction remain to be verified.

Build and ownership checks:

- Gradle debug build succeeded.
- Split-package build succeeded and verified all three output APKs use the same
  signer.
- Manifest inspection confirmed the permission activity and recording service
  are in the Keyboard APK.
- The Whisper model remains packaged only in Keyboard.
- The release artifact is only `com.nemotron.keyboard`; Core and UI are not
  intended to be updated for this fix.

## Crash fix follow-up

The first permission screen used `AppCompatActivity`, but the Keyboard dex
pruner removes an AppCompat decor class referenced indirectly by the themed
window. On the A54, `KeyboardMicrophonePermissionActivity` crashed while
inflating its content (`ClassNotFoundException` for
`androidx.appcompat.widget.FitWindowsLinearLayout`). The follow-up permission
screen is a platform `Activity`; its retry/settings actions use platform APIs
and it does not depend on pruned AppCompat classes.
