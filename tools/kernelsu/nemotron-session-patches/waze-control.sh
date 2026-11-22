#!/system/bin/sh
# Privately mount the Waze Car App patch inside Zygote's mount namespace.
# PackageManager, the installed APK, its signature, and phone-side UI stay stock.
set -eu

pkg=com.waze
store=/data/adb/nemotron-session-patches
source=/data/local/tmp/nemotron-waze-aa-candidate-v1.apk
hash_file=$store/waze-aa.sha256
original_hash=3ea14a2669439b240bb59aaa28b7a8557829d125cf103ebde196bc5606c0aefc
marker=/data/local/tmp/nemotron-waze-aa.target

zyg=$(pidof zygote64)
case "$zyg" in ''|*[!0-9]*) echo no_zygote; exit 1;; esac

if ! nsenter -t "$zyg" -m -- true >/dev/null 2>&1; then
  if [ "${NEMOTRON_MOUNT_MASTER_RETRY:-0}" = 1 ]; then
    echo namespace_access_denied
    exit 1
  fi
  exec su -mm -c "NEMOTRON_MOUNT_MASTER_RETRY=1 sh '$0' '${1:-}'"
fi

target=$(pm path "$pkg" | sed -n '1s/^package://p')
case "$target" in /data/app/*/com.waze-*/base.apk) ;; *) echo unsupported_target; exit 1;; esac
candidate_hash=$(cat "$hash_file" 2>/dev/null || true)
case "$candidate_hash" in ''|*[!0-9a-f]*) echo candidate_hash_invalid; exit 1;; esac
[ "${#candidate_hash}" -eq 64 ] || { echo candidate_hash_invalid; exit 1; }
file_hash() {
  [ -f "$1" ] || return 1
  sha256sum "$1" | cut -d ' ' -f 1
}
ns_hash() { nsenter -t "$zyg" -m -- sha256sum "$target" 2>/dev/null | cut -d ' ' -f 1; }

case "${1:-}" in
  enable|boot-enable)
    action=${1:-}
    [ "$(file_hash "$target")" = "$original_hash" ] || { echo original_changed; exit 1; }
    if [ "$(file_hash "$source" 2>/dev/null || true)" != "$candidate_hash" ]; then
      staged="$source.new"
      [ "$(file_hash "$store/nemotron-waze-aa-candidate-v1.apk")" = "$candidate_hash" ] || { echo candidate_store_mismatch; exit 1; }
      cp "$store/nemotron-waze-aa-candidate-v1.apk" "$staged"
      [ "$(file_hash "$staged")" = "$candidate_hash" ] || { rm -f "$staged"; echo candidate_copy_mismatch; exit 1; }
      chmod 0644 "$staged"
      mv -f "$staged" "$source"
    fi
    [ "$(file_hash "$source")" = "$candidate_hash" ] || { echo candidate_changed; exit 1; }
    chmod 0644 "$source"
    chcon u:object_r:system_file:s0 "$source"
    active=$(ns_hash)
    if [ "$active" = "$candidate_hash" ]; then
      printf '%s\n' "$target" > "$marker"
      echo already_active
      exit 0
    fi
    [ "$active" = "$original_hash" ] || { echo unexpected_existing_mount; exit 1; }
    nsenter -t "$zyg" -m -- mount --bind "$source" "$target" || { echo mount_failed; exit 1; }
    [ "$(ns_hash)" = "$candidate_hash" ] || {
      nsenter -t "$zyg" -m -- umount -l "$target" || true
      echo zygote_verification_failed
      exit 1
    }
    printf '%s\n' "$target" > "$marker"
    # A manual enable is only used while parked and restarts Waze so its next
    # CarAppService process inherits this mount. At boot, avoid interrupting
    # Android Auto if the user connected before KernelSU's late_start hook ran;
    # that process will pick up the mount the next time Waze starts naturally.
    if [ "$action" = enable ]; then
      am force-stop --user 0 "$pkg" >/dev/null 2>&1 || true
    elif pidof "$pkg" >/dev/null 2>&1; then
      echo mounted_restart_deferred
      exit 0
    fi
    echo active
    ;;
  disable)
    [ -f "$marker" ] && target=$(cat "$marker")
    case "$target" in /data/app/*/com.waze-*/base.apk) ;; *) echo unsupported_target; exit 1;; esac
    nsenter -t "$zyg" -m -- umount -l "$target" >/dev/null 2>&1 || true
    for info in $(grep -l -F "$target" /proc/[0-9]*/mountinfo 2>/dev/null || true); do
      grep -F "$target" "$info" >/dev/null 2>&1 || continue
      pid=${info#/proc/}; pid=${pid%/mountinfo}
      nsenter -t "$pid" -m -- umount -l "$target" >/dev/null 2>&1 || true
    done
    am force-stop --user 0 "$pkg" >/dev/null 2>&1 || true
    [ "$(file_hash "$target")" = "$original_hash" ] || { echo stock_hash_changed; exit 1; }
    [ "$(ns_hash)" = "$original_hash" ] || { echo restore_unverified; exit 1; }
    rm -f "$marker"
    echo restored
    ;;
  status)
    echo "stock=$(file_hash "$target")"
    echo "zygote=$(ns_hash)"
    echo "candidate=$(file_hash "$source" 2>/dev/null || file_hash "$store/nemotron-waze-aa-candidate-v1.apk" 2>/dev/null || true)"
    echo "expected=$candidate_hash"
    ;;
  *) echo 'usage: enable|boot-enable|disable|status'; exit 2 ;;
esac
