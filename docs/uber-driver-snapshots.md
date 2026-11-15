# Uber Driver tile snapshots

`UberDriverTileService` keeps its existing package freeze/unfreeze behavior and
requests a snapshot after each confirmed state change:

- unfreeze: `enabled`
- hide/freeze: `disabled`

`UberSnapshotClient` posts only the action, a unique event ID, and device time to
the NAS. The NAS owns Uber OAuth credentials, queries the official Driver
Payments API, calculates today's Mexico City MXN total, and stores the verified
snapshot in Firebase. OAuth secrets and payment data do not ship in the APK.

If the NAS/API is offline or returns invalid data, the tile action still
completes. The server evaluator treats missing data as unknown and does not
charge. See the NAS repo's `docs/uber-driver-beeminder-automation.md` for API
approval, OAuth setup, Firestore paths, and the separate weekday commitment.
