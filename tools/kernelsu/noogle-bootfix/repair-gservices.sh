#!/system/bin/sh
# Recover only the observed Google v4 -> microG v3 cache incompatibility.
# Accounts, auth tokens and all other databases are deliberately untouched.
EXPECTED=/product/priv-app/GmsCoreMG/GmsCoreMG.apk
LOG=/data/local/tmp/nemotron-noogle-bootfix.log
DB=/data/user/0/com.google.android.gms/databases/gservices.db
LOCK=/dev/.nemotron-gservices-repair.lock
log() { echo "$(date '+%F %T') gservices: $*" >> "$LOG"; }
version() {
  [ "$(od -An -tx1 -N16 "$DB" 2>/dev/null | tr -d ' \n')" = '53514c69746520666f726d6174203300' ] || return 1
  od -An -tu1 -j60 -N4 "$DB" 2>/dev/null | tr -s ' ' | sed 's/^ //;s/ $//'
}

case "$(pm path com.google.android.gms 2>/dev/null)" in
  *"$EXPECTED"*) ;;
  *) exit 0 ;;
esac
# Pin the schema assumption to the installed build that was actually verified.
dumpsys package com.google.android.gms | grep 'versionName=0.3.17.252432' >/dev/null || exit 0
[ "$(version)" = '0 0 0 4' ] || exit 0
mkdir "$LOCK" 2>/dev/null || exit 0
trap 'rmdir "$LOCK" 2>/dev/null' EXIT
am force-stop com.google.android.gms || exit 1
[ "$(version)" = '0 0 0 4' ] || exit 0
DEST=/data/adb/nemotron-backups/gservices-$(date '+%Y%m%d-%H%M%S')-$$
mkdir -p "$DEST" || exit 1
chmod 700 "$DEST"
# Preserve the whole SQLite file set while its owner is stopped.
for FILE in "$DB" "$DB-wal" "$DB-shm" "$DB-journal"; do
  [ -f "$FILE" ] || continue
  NAME=${FILE##*/}
  cp -p "$FILE" "$DEST/$NAME" || exit 1
  SRC=$(sha256sum "$FILE") || exit 1; SRC=${SRC%% *}
  COPY=$(sha256sum "$DEST/$NAME") || exit 1; COPY=${COPY%% *}
  [ "$SRC" = "$COPY" ] || { log 'backup mismatch; repair aborted'; exit 1; }
done
# No permanent deletion: move the verified originals into the private backup.
for FILE in "$DB-wal" "$DB-shm" "$DB-journal" "$DB"; do
  [ -f "$FILE" ] || continue
  mv "$FILE" "$DEST/${FILE##*/}.original" || exit 1
done
log "archived incompatible v4 cache to $DEST; microG will recreate schema v3"
# Start provider to recreate immediately; this reads configuration, not accounts.
content query --uri content://com.google.android.gsf.gservices --projection name:value >/dev/null 2>&1
if [ "$(version)" = '0 0 0 3' ]; then
  log 'confirmed schema v3'
else
  log 'cache archived; provider recreation awaits next client query'
fi
exit 0
