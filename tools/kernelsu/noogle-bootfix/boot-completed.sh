#!/system/bin/sh
MODDIR=${0%/*}
LOG=/data/local/tmp/nemotron-noogle-bootfix.log
BOOT_ID=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)
MARKER="$MODDIR/last_boot_id"
EXPECTED=/product/priv-app/GmsCoreMG/GmsCoreMG.apk

[ -n "$BOOT_ID" ] || exit 0
[ -f "$EXPECTED" ] || exit 0
[ "$(cat "$MARKER" 2>/dev/null)" = "$BOOT_ID" ] && exit 0

sleep 8
GMS_PATH=$(pm path com.google.android.gms 2>/dev/null)
printf '%s boot=%s gms=%s\n' "$(date '+%F %T')" "$BOOT_ID" "$GMS_PATH" >> "$LOG"
case "$GMS_PATH" in
  *"$EXPECTED"*) exit 0 ;;
esac

# Prevent a restart loop: this marker survives an Android userspace soft restart
# because the Linux boot ID and KernelSU module directory remain unchanged.
printf '%s\n' "$BOOT_ID" > "$MARKER"
KSUD=$(command -v ksud 2>/dev/null)
[ -n "$KSUD" ] || [ ! -x /data/adb/ksu/bin/ksud ] || KSUD=/data/adb/ksu/bin/ksud
[ -n "$KSUD" ] || [ ! -x /data/adb/ksud ] || KSUD=/data/adb/ksud
if [ -n "$KSUD" ]; then
  echo "$(date '+%F %T') running $KSUD soft-reboot" >> "$LOG"
  "$KSUD" soft-reboot >> "$LOG" 2>&1
else
  echo "$(date '+%F %T') ksud not found; manual KernelSU Soft restart required" >> "$LOG"
fi
