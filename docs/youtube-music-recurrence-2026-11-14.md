# Recurrent YouTube Music crash after temporary root startup

## Confirmed incident

On 2026-11-14 microG 0.3.17.252432 was registered at
`/product/priv-app/GmsCoreMG/GmsCoreMG.apk`. Launching the installed
`com.google.android.apps.youtube.music` failed with
`SQLiteException: Can't downgrade database from version 4 to 3` originating
in `org.microg.gms.gservices.DatabaseHelper`. This is a microG GServices
configuration database problem, not a corrupt YouTube Music database.

The active GServices database was 245760 bytes and had been modified during
the preceding period when stock GMS was registered before KernelSU late load.
This supports stock GMS reopening/upgrading the shared private database as the
recurrence mechanism; a traced cold boot proving the writer is still pending.
Temporary DFRoot loads Noogle after Android's initial package scan, so the
stock and replacement implementations can access the same package data at
different stages of a boot.

## Recovery verified on device

Stopped Music and GMS, copied only the GServices database to
`/data/adb/nemotron-backups/gservices-20261114/gservices.db`, and verified both
SHA-256 hashes as
`907b17d7bfe3cffa32c2d2434027a8a95987b468f26c8d39d53faf3999dd4c4b`.
Renamed the active file to `gservices.db.incompatible-20261114-2115`.
Music then opened to `MusicActivity` with a live process after eight seconds
and no new AndroidRuntime/DatabaseUtils errors in that observation.
The new database was 45056 bytes with SQLite user_version 3.
Accounts, authentication databases and Music data were not cleared.
Playback was not verified during the initial manual recovery.

## Recurrence protection in source

`tools/kernelsu/noogle-bootfix/repair-gservices.sh` runs from the existing
boot-completed hook when Package Manager reports the expected microG APK.
It is pinned to verified microG version 0.3.17.252432 and only repairs SQLite
user_version 4. Other versions are left alone. It stops GMS, preserves the
database plus any WAL/SHM/journal files under a private directory, compares
SHA-256 for every copy, and moves the originals into that backup directory.
A provider query requests cache recreation. It never resets account data.
There is no background polling service or additional restart in the helper.

The boot hook now checks the microG path and invokes this helper BEFORE its
one-restart-per-Linux-boot guard. Previously that guard skipped all work after
the initial recovery restart. The existing automatic restart behavior remains
bounded to one attempt per Linux boot.

Shell syntax validation passed. The helper was tested on the real healthy
database (no mutation) and an isolated copy of the actual version 4 database
(verified copy and archival). The fixture replaces only database/lock/backup
paths and disables force-stop/provider calls, so it never damages the active
database. This verifies the file recovery branch, not full cold-boot behavior.

## Installation state and remaining verification

After ADB reconnection, the v1.1 installer completed and verified SHA-256 of
all three installed files: `boot-completed.sh`, `repair-gservices.sh`,
`module.prop`. Shell syntax checks passed. Running the installed boot hook
against the healthy database preserved its hash
`aa8c3c0fcfe96ceb7c416029afe605f6e7e815fbecbcdec13e241aae5b0808cf`.
Music was stopped and relaunched successfully with a live process (16822)
and no AndroidRuntime/DatabaseUtils errors in the observation window.
## Full reboot and subsequent root activation: passed

Executed `adb reboot`. Before DFRoot, `su` was unavailable and Package Manager
reported the original `/product/priv-app/GmsCore/GmsCore.apk`. DFRoot's boot
receiver remained disabled, so root was launched manually from its button.
Confirmed `uid=0`. At 21:23:46 the installed hook detected the stock GMS path
and performed its single permitted soft restart. At 21:24:16 it detected the
microG path in the same Linux boot (`03364fba-ccde-43c2-b542-8ca315a15ccc`).
At 21:24:17 the new helper automatically archived the incompatible database.

Archive: `/data/adb/nemotron-backups/gservices-20261114-212417-14668`.
Both preserved database copies have SHA-256
`749ee6db2bc070d5a165b7a90e273d50e7e242766ef9dc39563f2a5c7b48a0b4`.
The accompanying empty journal was also preserved and verified.
After launching Music, the active database header reported user_version 3.
Music remained alive (PID 15979) at `MusicActivity`, with no new SQLite or
AndroidRuntime error in the sampled log, and a screenshot confirmed the fully
rendered signed-in home screen with recommendations and the previous track.
This validates automatic recovery after a real reboot followed by temporary
root activation. A physical power-off/power-on was not separately tested;
audio playback was not exercised. ZygiskNext stayed disabled from the earlier
Openbank experiment; this recovery does not require it. DFRoot automatic
activation at boot remains disabled, so the user still activates temporary root.

The repeatable installer is `tools/kernelsu/install-noogle-recovery.ps1`:

```powershell
./tools/kernelsu/install-noogle-recovery.ps1 -Serial RZCWB0BHEQX
```

It requires an existing root session, verifies staged and installed SHA-256
hashes, validates shell syntax, preserves the previous hook and module metadata,
and publishes the helper before the hook through adjacent-file renames. It
does not restart Android or change the root bootstrap settings. The PowerShell
parser check and actual device installation passed.

The helper lock lives in `/dev` so a cold boot clears an interrupted repair.
SQLite magic is checked before interpreting the user_version bytes. These
additional guards were included in the installed version tested above.

This protection addresses the observed v4/v3 recurrence; it cannot promise
that future unrelated Music errors will never occur. If microG changes its
schema/version, review the version gate before updating it.
