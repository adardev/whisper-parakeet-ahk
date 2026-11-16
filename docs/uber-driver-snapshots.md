# Uber Driver wallet snapshots

`UberDriverTileService` keeps its package freeze/unfreeze behavior and captures
the wallet after each confirmed tile transition:

- unfreeze: `enabled`
- hide/freeze: `disabled`

Uber has one exported `com.ubercab.carbon.core.CarbonActivity`; Billetera is an
in-app screen, not a separate Android activity. The tile opens CarbonActivity,
navigates through Uber's own menu to **Billetera**, reads the exact value next
to **Saldo** twice, and rejects anything that does not parse as MXN. It then
queues the tile event with that reading, attempts immediate delivery, and
returns to Home. Enabling captures after unfreezing. Disabling captures before
freezing, then sends the event. These are the only code paths that take a new
reading; the network job retries delivery of an existing event only.

The phone posts the action, a unique event ID, device time, the wallet value,
and a Firebase ID token for Nemotron's existing Firebase account to the NAS.
The NAS validates that token against the configured health UID and stores
verified wallet snapshots in `uber_wallet_snapshots/{yyyy-MM-dd}` plus immutable
per-toggle events below `events/{eventId}`. OAuth secrets and payment data do
not ship in the APK.

If the NAS is offline, the tile action still completes and the persistent
network job retries the same event. If the UI reading is invalid, the event is
stored without an amount; missing data is unknown and never treated as zero.
See the NAS repo's `docs/uber-driver-beeminder-automation.md` for the weekday
balance-delta commitment and Firestore paths.

The official Uber Driver API integration remains disabled. The tile uses the
visible wallet balance instead; the wallet's daily increase is evaluated on the
NAS. Missing daily snapshots never count as zero and cannot trigger a charge.

Each tile event is committed to a local queue before networking. If the NAS is
unreachable, a persisted `JobScheduler` job retries only when the phone has
network. The original event timestamp is sent on retry, so delayed delivery
queries earnings through the time the tile actually changed. Events outside
Uber's ten-day query window are recorded as unavailable rather than assigned a
made-up amount.
