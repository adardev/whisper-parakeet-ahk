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
# boot_completed can be published while Zygote or PackageManager is still
# restarting. Require a stable Zygote namespace and a responsive PackageManager
# before invoking any controller; otherwise pm path can return an empty target.
last_zyg=''
stable=0
n=0
while [ "$n" -lt 90 ] && [ "$stable" -lt 3 ]; do
  zyg=$(pidof zygote64 2>/dev/null || true)
  case "$zyg" in ''|*[!0-9]*) stable=0;;
    *)
      if [ "$zyg" = "$last_zyg" ] \
        && nsenter -t "$zyg" -m -- true >/dev/null 2>&1 \
        && service check package 2>/dev/null | grep -q 'found' \
        && pm path com.sec.android.app.launcher 2>/dev/null | grep -q '^package:/system/priv-app/TouchWizHome_2017/TouchWizHome_2017.apk$'; then
        stable=$((stable+1))
      else
        stable=0
      fi
      last_zyg="$zyg"
      ;;
  esac
  [ "$stable" -ge 3 ] || sleep 2
  n=$((n+1))
done
[ "$stable" -ge 3 ] || { log framework_not_ready; exit 1; }
log "framework_ready zygote=$zyg"
boot=$(cat /proc/sys/kernel/random/boot_id)
case "$boot" in ''|*[!0-9a-f-]*) log invalid_boot_id; exit 1;; esac
[ -f "$STORE/disabled" ] && { log disabled; exit 0; }
lock="/data/local/tmp/nemotron-session-patches-$boot-$zyg.lock"
mkdir "$lock" 2>/dev/null || { log already_running_or_stale_lock; exit 0; }
trap 'rmdir "$lock" 2>/dev/null' EXIT
prepare() {
 src="$MODDIR/$1"; dst="/data/local/tmp/$1"; expected="$2"
 [ -f "$src" ] || src="$STORE/$1"
 [ "$(file_hash "$src")" = "$expected" ] || { log "source_invalid $1"; return 1; }
 [ "$(file_hash "$dst")" = "$expected" ] && return 0
 cp "$src" "$dst.new" || return 1
 [ "$(file_hash "$dst.new")" = "$expected" ] || return 1
 chmod 0644 "$dst.new" || return 1
 mv -f "$dst.new" "$dst" || return 1
}
# Each controller checks both stock and candidate hashes before mounting.
oneui=nemotron-oneui-home-root-candidate-home100ms.apk
if prepare "$oneui" 406a11a4484c338a2fa2419cc9ca9df25668d6ca9070f828ec4f679342e834c5; then
 sh "$MODDIR/oneui-control.sh" enable >> "$LOG" 2>&1 || log oneui_failed
fi
keyboard=nemotron-honeyboard-root-candidate-waveform.apk
if prepare "$keyboard" ca1bf8a41d7b6af7387296d400b56b3378b241773709ca455423b29c3975b11b; then
 sh "$MODDIR/honeyboard-control.sh" enable >> "$LOG" 2>&1 || log keyboard_failed
fi
settings=nemotron-settings-app-filter-v1.apk
if prepare "$settings" afcc203b80e8278c7000a17cd0651e37ba3b96aa9673d11fafeaee28b8137d6f; then
 sh "$MODDIR/settings-app-filter-control.sh" enable >> "$LOG" 2>&1 || log settings_filter_failed
fi
dialer=nemotron-samsung-dialer-fullscreen.apk
if prepare "$dialer" f90284b44047fb60817cb2911fbb3cb1aba9ee009fa272db920827b8793e60e5; then
 sh "$MODDIR/dialer-control.sh" enable >> "$LOG" 2>&1 || log samsung_dialer_failed
fi
android_auto=nemotron-aa-18_1-rail1dp.apk
if prepare "$android_auto" 6e14eb65f4df0293352ed5da478ada2445341b776956746693e75da205d17cd8; then
 sh "$MODDIR/android-auto-rail-control.sh" enable >> "$LOG" 2>&1 || log android_auto_rail_failed
fi
# Only auto-mount the last documented Waze candidate. Earlier template
# experiments caused Android Auto validation crashes and must never be revived
# just because a stale APK and hash file remain in the persistent store.
waze_expected=738db3b2618d47db57a05a3ab69632b9bfdb161c45616795ebe7d79148f6be25
waze_store_hash=$(cat "$STORE/waze-aa.sha256" 2>/dev/null || true)
waze_store_apk="$STORE/nemotron-waze-aa-candidate-v1.apk"
if [ "$waze_store_hash" = "$waze_expected" ] \
  && [ "$(file_hash "$waze_store_apk")" = "$waze_expected" ]; then
 sh "$MODDIR/waze-control.sh" enable >> "$LOG" 2>&1 || log waze_aa_failed
else
 log waze_aa_candidate_not_v7_skipped
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
