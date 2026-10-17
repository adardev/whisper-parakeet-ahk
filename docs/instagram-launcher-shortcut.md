# Instagram launcher icon and Nemotron shortcut

The Nemotron shortcut picker can pin an Instagram shortcut to the One UI Home screen using the Nemotron app icon. The shortcut launches Instagram through `ShortcutActivity`; it does not disable or modify Instagram.

## Setup

1. Open Nemotron and choose **App shortcuts**.
2. Tap **+** beside Instagram and accept One UI's pin-shortcut prompt.
3. If an older Instagram shortcut is already pinned, remove it and recreate it from the updated Nemotron picker so it gets Nemotron's icon.
4. On the One UI Home screen, long-press Instagram's original app icon and choose **Remove**. This removes only that Home-screen icon; it does not uninstall Instagram. The Nemotron-pinned shortcut remains available.

To hide Instagram from both Home and the Apps screen, use One UI Home settings → **Hide apps on Home and Apps screens**. Keep Instagram installed and enabled: disabling the package would also prevent the Nemotron shortcut from launching it.

## Implementation

`ShortcutPickerActivity` includes `com.instagram.android` in the individual shortcut list. For Instagram only, both the picker row and pinned shortcut use Nemotron's installed app icon; the shortcut label remains Instagram and its target remains Instagram. Other apps continue to use their own icons. The bulk shortcut flow applies the same Instagram icon override. `ShortcutActivity` opens the package/activity using the existing Shizuku-aware path.

The setup screen's existing bulk shortcut flow already included Instagram. This addition makes Instagram available in the individual picker too. Device-level removal of the original One UI icon is a launcher setting, not an app APK patch.

## Verification status

Code path reviewed locally. ADB reported no connected device while this change was prepared. The local Gradle build could not run because `JAVA_HOME` points to a missing JDK (`C:\Users\adaredu\develop\jdk-17.0.20.1+1`), so installation and on-device pin/removal behavior still need verification on the A54.
