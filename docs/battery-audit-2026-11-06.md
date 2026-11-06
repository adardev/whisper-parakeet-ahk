# Battery audit — 2026-11-06

## Scope and limits

Captured from the Galaxy A54 with `dumpsys batterystats` at 10:12 local time. The stats covered roughly 2.5 hours on battery and 1 h 50 min screen-off, but also contained plugged-in intervals. UID power totals below are Android estimates across that mixed interval; they are useful for finding candidates, not a clean overnight A/B comparison. Do not sum them as a share of the reported 374 mAh discharge.

## Highest estimated UID totals

| UID / package | Estimated total | Screen-off CPU | Notes |
| --- | ---: | ---: | --- |
| Android shared system UID (`1000`) | 113 mAh | 21.6 mAh | Shared by Android and several Samsung packages; cannot attribute this to one app. |
| BBVA (`com.bancomer.mbanking`) | 91.6 mAh | 23.5 mAh | Historic in this capture; Wallet role was removed separately. Re-measure after a clean discharge window. |
| Kernel (`uid 0`) | 79.8 mAh | 22.1 mAh | Kernel/system work, not an app package. |
| WhatsApp (`com.whatsapp`) | 69.8 mAh | 4.33 mAh | 5.53 mAh estimated screen-off mobile-radio use. |
| Waze (`com.waze`) | 58.5 mAh | ~0 mAh | GPS/network activity is expected during navigation; capture included plugged-in time. |
| Google Play services / microG UID (`com.google.android.gms`) | 46.7 mAh | 0.327 mAh CPU; 26.9 mAh GNSS attribution | GNSS attribution is not proof GmsCore itself kept GPS active; Android may attribute brokered location here. |
| System UI (`com.android.systemui`) | 32.7 mAh | 9.73 mAh | Most measured screen-off CPU was attributed to foreground-service time. |

Samsung Health's package UID total was 5.02 mAh in this capture. A short live CPU sample showed Samsung Health's remote process briefly busy, but it was taken during active diagnostics and is not evidence of sustained idle drain.

## Nemotron apps

| App | Estimated total | Screen-off CPU |
| --- | ---: | ---: |
| Nemotron Home (`com.nemotron.voiceime2`) | 14.8 mAh | 1.17 mAh |
| Nemotron Keyboard (`com.nemotron.keyboard`) | 6.90 mAh | 1.32 mAh |
| Nemotron Core (`com.nemotron.core`) | 2.27 mAh | 0.50 mAh |

These are low compared with the leading UIDs in this window. No feature was disabled. Core's open conversation list was polling its server every second, so polling is now every 10 seconds while that screen is visible; it still refreshes immediately on entry and after in-app actions, and the loop stops when the screen pauses. This lowers unnecessary network and wakeup work without changing background features.

## Follow-up measurement

To identify overnight idle drain accurately, capture a fresh interval after unplugging and using the phone normally, then read BatteryStats before charging. Compare screen-off CPU, mobile-radio, GNSS, and partial-wakelock attribution separately. Do not reset BatteryStats before preserving the current baseline.

## Fit3 tile check (10:30–10:35)

The Core app's `POST_NOTIFICATION` app-op was `ignore`, which suppressed the tile's ongoing connection notification. It was restored to `allow` and the runtime notification permission was granted. The connection notification no longer uses deferred foreground-service display, so the progress indicator can appear as soon as a tile action begins.

A status-bar tile click restored Samsung Health, Fit3 Plugin, and Samsung Accessory to visible/enabled state. The tile's 25-second connection check still ended with `conectado=false`; Bluetooth was on but the adapter reported `STATE_DISCONNECTED` and zero connected profiles. Samsung's plugin also logged background service starts denied during its receiver-based startup. Therefore the notification suppression is fixed, but this capture does not prove the band reconnected; keep the Fit3 nearby/on and verify the Bluetooth link separately.
