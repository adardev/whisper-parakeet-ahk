#!/system/bin/sh
# KernelSU late_start hook. Mount only the verified Waze Android Auto patch;
# the main boot-completed hook remains independently managed.
MODDIR=${0%/*}
STORE=/data/adb/nemotron-session-patches
LOG=/data/local/tmp/nemotron-session-patches.log
PATH=/system/bin:/system/xbin:/vendor/bin
export PATH
log() { echo "$(date '+%F %T') waze_service $*" >> "$LOG"; }

(
  [ -f "$STORE/disabled" ] && { log disabled; exit 0; }
  expected=738db3b2618d47db57a05a3ab69632b9bfdb161c45616795ebe7d79148f6be25
  apk="$STORE/nemotron-waze-aa-candidate-v1.apk"
  [ "$(cat "$STORE/waze-aa.sha256" 2>/dev/null)" = "$expected" ] || { log candidate_hash_file_invalid; exit 0; }
  [ "$(sha256sum "$apk" 2>/dev/null | cut -d' ' -f1)" = "$expected" ] || { log candidate_apk_invalid; exit 0; }

  last_zyg=''
  stable=0
  n=0
  while [ "$n" -lt 90 ] && [ "$stable" -lt 3 ]; do
    zyg=$(pidof zygote64 2>/dev/null || true)
    case "$zyg" in
      ''|*[!0-9]*) stable=0 ;;
      *)
        if [ "$zyg" = "$last_zyg" ] \
          && nsenter -t "$zyg" -m -- true >/dev/null 2>&1 \
          && service check package 2>/dev/null | grep -q 'found' \
          && pm path com.waze 2>/dev/null | grep -q '^package:/data/app/[^ ]*/com\.waze-[^ ]*/base\.apk$'; then
          stable=$((stable + 1))
        else
          stable=0
        fi
        last_zyg="$zyg"
        ;;
    esac
    [ "$stable" -ge 3 ] || sleep 2
    n=$((n + 1))
  done
  [ "$stable" -ge 3 ] || { log framework_or_waze_not_ready; exit 1; }
  sh "$MODDIR/waze-control.sh" boot-enable >> "$LOG" 2>&1 || { log activation_failed; exit 1; }
  log activation_check_complete
) </dev/null >> "$LOG" 2>&1 &
exit 0
