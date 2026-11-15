# Uber Driver tile snapshots

`UberDriverTileService` keeps its existing package freeze/unfreeze behavior and
requests a snapshot after each confirmed state change:

- unfreeze: `enabled`
- hide/freeze: `disabled`

`UberSnapshotClient` posts the action, a unique event ID, device time, and a
Firebase ID token for Nemotron's existing Firebase account to the NAS. The NAS
validates that token against the configured health UID. It owns Uber OAuth
credentials, queries the official Driver
Payments API, calculates today's Mexico City MXN total, and stores the verified
snapshot in Firebase. OAuth secrets and payment data do not ship in the APK.

If the NAS/API is offline or returns invalid data, the tile action still
completes. The server evaluator treats missing data as unknown and does not
charge. See the NAS repo's `docs/uber-driver-beeminder-automation.md` for API
approval, OAuth setup, Firestore paths, and the separate weekday commitment.

The deployed endpoint currently returns HTTP 503 because Uber API access is
disabled. Uber must approve the Driver API `partner.payments` scope and the
driver account must complete OAuth before a toggle can produce an earnings
snapshot. Missing API access never counts as zero earnings and cannot trigger a
charge. The server still records each authenticated tile event in Firebase
with `valid=false` and no amount while the earnings source is unavailable.
