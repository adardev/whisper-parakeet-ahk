# Waze horizontal position in Android Auto — 2026-11-06

## Observed issue

The Suzuki head-unit photo shows the Waze vehicle arrow noticeably right of the
center of the projected map. The user wants its horizontal position at the red
X; the current vertical position is acceptable. This is specific to Waze while
projected through Android Auto. The Waze phone map itself showed the vehicle
marker centered.

The existing Android Auto 18.1 rail patch changes only four vertical dimensions
(`car_rail_height`, `cielo_rail_height`, `floating_rail_space_height`, and
`rail_height`) to 1 dp. It does not modify screen width, Waze, or the map camera,
so it does not provide a horizontal marker offset control. See
[`android-auto-offline-patch.md`](android-auto-offline-patch.md).

## Waze setting changed

In Waze on the A54, opened **Settings → Map display** and enabled **Keep map
north up**. The option was off before the change. This preference is used by
Waze's Android Auto projection; it changes map orientation behavior and is the
available Waze-side setting relevant to landscape framing. Waze's community
notes that landscape mode can intentionally offset the vehicle marker to leave
more of the road ahead visible, and suggests trying the alternate map
orientation. There is no horizontal-anchor slider in the available settings.

The change was made through Waze's UI, not by editing its APK or private data.
During setup, device location was temporarily enabled because Waze's first-run
screen required it. Location was restored to **off** after saving the setting;
no Waze data was cleared, and the app was force-stopped when finished.

## Verification status

- Waze package on the phone: `com.waze`, version `5.26.0.0`.
- Before: **Keep map north up** was unchecked.
- After: the setting's switch was checked in Waze's Map display screen.
- Device-wide location was verified off after the change.
- The uploaded Suzuki photo predates this setting change. Android Auto was not
  projecting during the ADB session, so the new horizontal framing has **not yet
  been verified on the physical head unit**. Reconnect Android Auto while
  parked and compare the arrow against the map's horizontal midpoint. If north-up
  changes the vertical presentation undesirably or does not center the arrow,
  restore the original setting; exact horizontal placement would require a
  Waze-side code change, not the Android Auto rail-height patch.

## References

- [Waze community: icon not centered in landscape mode](https://support.google.com/waze/thread/457327758/icon-not-centered-when-using-phone-in-landscape-mode?hl=en)
- [Waze community: vehicle not centered in Android Auto](https://www.reddit.com/r/waze/comments/1sipxu1/not_central_when_usong_android_auto_waze/)
