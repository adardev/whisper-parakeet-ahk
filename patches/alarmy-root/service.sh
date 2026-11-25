#!/system/bin/sh
MODDIR=${0%/*}
LOG=/data/adb/alarmy-root-patch.log
exec >> "$LOG" 2>&1
PKG=droom.sleepIfUCan
SOURCE="$MODDIR/alarmy.apk"
EXPECTED_ORIGINAL=4568eebab6b2517cbe57dc94121b2baff5638f0367026980e67c83a6735f3d8a
PREVIOUS_PATCHED=3bf934a5c0ad640c2730b84bd9c71551fc56a75b0975dd46dd9d6aea947adc31
PREVIOUS_PATCHED_ALT=5aef1479ba32749db264d9a819c95fc1700280ed7382a2372ca124f8c4ee7d11
EXPECTED_PATCHED=c5702363c328db853ebf0f1ad517c52d38937c36dd6e303787d6c4c46e841744
BB=/data/adb/magisk/busybox
set -- /data/app/droom.sleepIfUCan-*/base.apk
[ -f "$1" ] || { echo "skip: installed APK path not found"; exit 1; }
TARGET=$1
[ -f "$SOURCE" ] || { echo "skip: patch apk missing"; exit 1; }
orig_line=$($BB sha256sum "$TARGET"); orig=${orig_line%% *}
[ "$orig" = "$EXPECTED_ORIGINAL" ] || { echo "skip: stock APK hash mismatch $orig"; exit 1; }
patch_line=$($BB sha256sum "$SOURCE"); patch=${patch_line%% *}
[ "$patch" = "$EXPECTED_PATCHED" ] || { echo "skip: patch APK hash mismatch $patch"; exit 1; }
zyg_line=$(ps | $BB grep '[z]ygote$' | $BB head -n 1); set -- $zyg_line; zyg=$2
ss_line=$(ps | $BB grep '[s]ystem_server$' | $BB head -n 1); set -- $ss_line; system_server=$2
case "$zyg:$system_server" in *[!0-9:]*|:*) echo "skip: process ids unavailable"; exit 1;; esac
chown system:system "$SOURCE"
chmod 0644 "$SOURCE"
chcon u:object_r:apk_data_file:s0 "$SOURCE" || { echo "skip: APK label failed"; exit 1; }
mount_candidate() {
  pid="$1"
  line=$($BB nsenter -t "$pid" -m -- $BB sha256sum "$TARGET") || return 1
  hash=${line%% *}
  if [ "$hash" = "$PREVIOUS_PATCHED" ] || [ "$hash" = "$PREVIOUS_PATCHED_ALT" ]; then
    $BB nsenter -t "$pid" -m -- $BB umount -l "$TARGET" || return 1
    line=$($BB nsenter -t "$pid" -m -- $BB sha256sum "$TARGET") || return 1
    hash=${line%% *}
  fi
  if [ "$hash" = "$EXPECTED_PATCHED" ]; then return 0; fi
  [ "$hash" = "$EXPECTED_ORIGINAL" ] || { echo "skip: unexpected APK hash in pid $pid: $hash"; return 1; }
  $BB nsenter -t "$pid" -m -- $BB mount -o bind "$SOURCE" "$TARGET" || return 1
  line=$($BB nsenter -t "$pid" -m -- $BB sha256sum "$TARGET") || return 1
  hash=${line%% *}
  [ "$hash" = "$EXPECTED_PATCHED" ] || { echo "error: hash verification failed in pid $pid: $hash"; return 1; }
}
mount_candidate "$system_server" || { echo "error: system_server mount failed"; exit 1; }
mount_candidate "$zyg" || { echo "error: zygote mount failed"; exit 1; }
APPDIR=${TARGET%/base.apk}
rm -f "$APPDIR/oat/arm/base.odex"
am force-stop --user 0 "$PKG" >/dev/null 2>&1 || true
am broadcast -a android.intent.action.BOOT_COMPLETED -n "$PKG/.internal.AlarmInitReceiver" >/dev/null 2>&1 || true
echo "active: expected_patch=$EXPECTED_PATCHED system_server=$system_server zygote=$zyg"
