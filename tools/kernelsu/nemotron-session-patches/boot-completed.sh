#!/system/bin/sh
# Run after KernelSU has loaded modules and Android has completed startup.
# Never change battery settings or reboot the device.
MODDIR=${0%/*}
STORE=/data/adb/nemotron-session-patches
LOG=/data/local/tmp/nemotron-session-patches.log
PATH=/system/bin:/system/xbin:/vendor/bin
export PATH
log() { echo "$(date '+%F %T') $*" >> "$LOG"; }
file_hash() { sha256sum "$1" 2>/dev/null | cut -d' ' -f1; }
ns_hash() { nsenter -t "$zyg" -m -- sha256sum "$1" 2>/dev/null | cut -d' ' -f1; }
[ "$(getprop ro.boot.safemode)" = 1 ] && exit 0
[ "$(getprop sys.safemode)" = 1 ] && exit 0
n=0
while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$n" -lt 90 ]; do sleep 2; n=$((n+1)); done
[ "$(getprop sys.boot_completed)" = 1 ] || { log boot_not_completed; exit 1; }
zyg=$(pidof zygote64)
case "$zyg" in ''|*[!0-9]*) log no_zygote; exit 1;; esac
nsenter -t "$zyg" -m -- true || { log namespace_denied; exit 1; }
boot=$(cat /proc/sys/kernel/random/boot_id)
case "$boot" in ''|*[!0-9a-f-]*) log invalid_boot_id; exit 1;; esac
[ -f "$STORE/disabled" ] && { log disabled; exit 0; }
lock="/data/local/tmp/nemotron-session-patches-$boot-$zyg.lock"
mkdir "$lock" 2>/dev/null || { log already_running_or_stale_lock; exit 0; }
trap 'rmdir "$lock" 2>/dev/null' EXIT
prepare() {
 src="$STORE/$1"; dst="/data/local/tmp/$1"; expected="$2"
 [ "$(file_hash "$src")" = "$expected" ] || { log "source_invalid $1"; return 1; }
 [ "$(file_hash "$dst")" = "$expected" ] && return 0
 cp "$src" "$dst.new" || return 1
 [ "$(file_hash "$dst.new")" = "$expected" ] || return 1
 chmod 0644 "$dst.new" || return 1
 mv -f "$dst.new" "$dst" || return 1
}
# Each controller checks both stock and candidate hashes before mounting.
oneui=nemotron-oneui-home-root-candidate.apk
if prepare "$oneui" 91065022a24e092a9174b503d26850aa99ff78c3971dd4e12b4fc53db11e84a2; then
 sh "$MODDIR/oneui-control.sh" enable >> "$LOG" 2>&1 || log oneui_failed
fi
keyboard=nemotron-honeyboard-root-candidate-waveform.apk
if prepare "$keyboard" ca1bf8a41d7b6af7387296d400b56b3378b241773709ca455423b29c3975b11b; then
 sh "$MODDIR/honeyboard-control.sh" enable >> "$LOG" 2>&1 || log keyboard_failed
fi
# SystemUI is session mounted only after stock/candidate validation. A failed
# startup is rolled back and never retried until its failure marker is cleared.
target=/system/system_ext/priv-app/SystemUI/SystemUI.apk
stock=0b53ae0d66a94ada8188a3c3ed5bfb44f7e62efec70b56539c4de42bc9ce6642
patched=73df730fd66944febf64867d32dc9ce032cba5afa5bf642054d2d2d6430e8b4e
candidate=systemui-wallpaper-candidate.apk
[ "$(file_hash "$target")" = "$stock" ] || { log systemui_stock_changed; exit 0; }
active=$(ns_hash "$target")
[ "$active" = "$patched" ] && { log systemui_already_active; exit 0; }
[ "$active" = "$stock" ] || { log systemui_unknown_mount; exit 0; }
[ -f "$STORE/systemui-failed" ] && { log systemui_failure_requires_review; exit 0; }
prepare "$candidate" "$patched" || { log systemui_source_invalid; exit 0; }
chcon u:object_r:system_file:s0 "/data/local/tmp/$candidate" || { log systemui_context_failed; exit 0; }
printf '%s\n' pending > "$STORE/systemui-failed" || exit 1
nsenter -t "$zyg" -m -- mount --bind "/data/local/tmp/$candidate" "$target" || { log systemui_mount_failed; exit 1; }
# Rollback remains armed unless the replacement survives the startup checks.
ok="$STORE/systemui-ok-$boot-$zyg"
rm -f "$ok"
( sleep 8; seen=''; n=0
  while [ "$n" -lt 40 ] && [ ! -f "$ok" ]; do
    current=$(pidof com.android.systemui)
    if [ -n "$seen" ] && [ "$current" != "$seen" ]; then break; fi
    [ -n "$current" ] && seen="$current"
    sleep 1; n=$((n+1))
  done
  if [ ! -f "$ok" ]; then
    nsenter -t "$zyg" -m -- umount -l "$target"
    kill -9 $(pidof com.android.systemui) 2>/dev/null
    log systemui_rollback
  fi
) </dev/null >> "$LOG" 2>&1 &
kill -9 $(pidof com.android.systemui) 2>/dev/null
sleep 20
pid=$(pidof com.android.systemui)
case "$pid" in ''|*[!0-9]*) log systemui_not_running; exit 1;; esac
[ "$(ns_hash "$target")" = "$patched" ] || { log systemui_mount_lost; exit 1; }
sleep 10
[ "$(pidof com.android.systemui)" = "$pid" ] || { log systemui_unstable; exit 1; }
# Successful startup makes later retries possible only after a new Zygote.
printf 'ok\n' > "$ok" || exit 1
rm -f "$STORE/systemui-failed"
log systemui_active
