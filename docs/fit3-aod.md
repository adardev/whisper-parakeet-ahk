# Fit3 AOD schedule

At 23:00 Nemotron turns off Always On Display and Raise to wake on the paired
Galaxy Fit3 over SAP/BLE; at 08:00 it turns both on. It reads back the full
settings reply twice, including fragmented replies, and a BLE write by itself
is not counted as success. The manual night and morning test buttons call only
these same display actions. They do not change Airplane mode.
At the same scheduled boundaries, the separate airplane-mode routine keeps
its existing behavior; it only changes that setting when its current state
differs from the scheduled state.
Before a BLE change Nemotron temporarily unfreezes the Fit3 tile packages and
turns Bluetooth on if needed. After sending the settings at either boundary,
it freezes the Fit3 tile again and turns Bluetooth off only if no other
peripherals remain connected.

During the night, manually disabling airplane mode with the screen on pauses
the watch's night settings too: AOD and Raise to wake are enabled temporarily.
The next screen-off sends both settings off again before restoring airplane
mode, even if airplane mode was already enabled manually. Watch actions from
these broadcasts run in order, so a quick lock cannot leave an older daytime
request running after the night request. Failed night restoration remains
pending for the existing Shizuku-available retry, without polling.
Between 05:00 and 08:00, disabling airplane mode still means the user is awake
for the day: both watch settings remain enabled after locking the phone.

The small codec uses protocol details inspected at commit
`1ac39f063fe5d0bcae72c71acf40d40218752365` of
[Fit3-App](https://github.com/yuriyurin/Fit3-App): service 11 (settings),
message 6 / parameter 9 for AOD, and message 32 for the full settings readback.
Only capability negotiation, these two settings messages, and SAP framing are
implemented here; there is no scan, persistent connection, reconnect loop, or
periodic poll. Each BLE operation has a 30-second bound.

The Samsung Accessory GATT client may occupy the Fit3 connection. Nemotron
briefly hands off the connection and restores the Samsung packages afterward.
When the watch does not answer Nemotron's SAP session, Nemotron reports that
failure instead of claiming AOD changed.
