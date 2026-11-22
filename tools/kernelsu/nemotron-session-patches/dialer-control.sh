#!/system/bin/sh
# Session-only Samsung Dialer fullscreen patch. PackageManager and /system keep Samsung's APK.
set -eu

pkg=com.samsung.android.dialer
source=/data/local/tmp/nemotron-samsung-dialer-fullscreen.apk
source_name=nemotron-samsung-dialer-fullscreen.apk
candidate_hash=f90284b44047fb60817cb2911fbb3cb1aba9ee009fa272db920827b8793e60e5
original_hash=77cd0151f57712887522f947a8bebcf7fc668ef53adb89c3104b00f1d8112285
marker=/data/local/tmp/nemotron-samsung-dialer.target

zyg=`pidof zygote64`
case "$zyg" in ''|*[!0-9]*) echo no_zygote; exit 1;; esac

# KernelSU's default su shell may have uid 0 but no CAP_SYS_ADMIN. Retry only
# this mount controller in mount-master mode when entering Zygote's namespace
# fails; keep the retry bounded to avoid recursion.
if ! nsenter -t "$zyg" -m -- true >/dev/null 2>&1; then
  if [ "${NEMOTRON_MOUNT_MASTER_RETRY:-0}" = 1 ]; then
    echo namespace_access_denied
    exit 1
  fi
  exec su -mm -c "NEMOTRON_MOUNT_MASTER_RETRY=1 sh '$0' '${1:-}'"
fi

get_path() { pm path "$1" | sed -n '1s/^package://p'; }
valid_target() { [ "$1" = /system/priv-app/SamsungDialer/SamsungDialer.apk ]; }
valid_source() { [ "$1" = "$source" ]; }
hash_in_ns() { nsenter -t "$1" -m -- sha256sum "$target" | cut -d ' ' -f 1; }
can_enter_ns() { nsenter -t "$1" -m -- true >/dev/null 2>&1; }
process_hash() {
  pid="$1"
  case "$pid" in ''|*[!0-9]*) echo no_dialer_process; return 1;; esac
  nsenter -t "$pid" -m -- sha256sum "$target" 2>/dev/null | cut -d ' ' -f 1
}

target=`get_path "$pkg"`
valid_target "$target" || { echo unsupported_target; exit 1; }
valid_source "$source" || { echo unsupported_candidate_path; exit 1; }

case "${1:-}" in
  enable)
    can_enter_ns "$zyg" || { echo namespace_access_denied; exit 1; }
    system_hash=`sha256sum "$target" | cut -d ' ' -f 1`
    source_hash=`sha256sum "$source" | cut -d ' ' -f 1`
    [ "$system_hash" = "$original_hash" ] || { echo original_changed; exit 1; }
    [ "$source_hash" = "$candidate_hash" ] || { echo candidate_changed; exit 1; }
    chown system:system "$source"
    chmod 0644 "$source"
    chcon u:object_r:apk_data_file:s0 "$source"
    zygote_hash=`hash_in_ns "$zyg"`
    if [ "$zygote_hash" = "$candidate_hash" ]; then
      printf '%s\n' "$target" > "$marker"
      echo already_active
      exit 0
    fi
    [ "$zygote_hash" = "$original_hash" ] || { echo unexpected_existing_mount; exit 1; }
    printf '%s\n' "$target" > "$marker"
    nsenter -t "$zyg" -m -- mount --bind "$source" "$target" || { rm -f "$marker"; echo mount_failed; exit 1; }
    [ "`hash_in_ns "$zyg"`" = "$candidate_hash" ] || {
      nsenter -t "$zyg" -m -- umount -l "$target" || true
      rm -f "$marker"
      echo zygote_verification_failed
      exit 1
    }
    # Restart only the dialer process so its new child inherits the private
    # Zygote mount. SystemUI and PackageManager continue seeing the stock APK.
    am force-stop --user 0 "$pkg" >/dev/null 2>&1 || true
    echo active
    ;;
  disable)
    can_enter_ns "$zyg" || { echo namespace_access_denied; exit 1; }
    [ -f "$marker" ] && target=`cat "$marker"`
    valid_target "$target" || { echo unsupported_target; exit 1; }
    # Zygote owns the mount from which newly forked Samsung processes inherit.
    # Remove that mount first, then detach copies already inherited by apps.
    nsenter -t "$zyg" -m -- umount -l "$target" >/dev/null 2>&1 || true
    # Unmount in Zygote and every already-forked app namespace that inherited it.
    for info in `grep -l -F "$target" /proc/[0-9]*/mountinfo 2>/dev/null || true`; do
      grep -F "$target" "$info" >/dev/null 2>&1 || continue
      pid=${info#/proc/}; pid=${pid%/mountinfo}
      nsenter -t "$pid" -m -- umount -l "$target" >/dev/null 2>&1 || true
    done
    am force-stop --user 0 "$pkg" >/dev/null 2>&1 || true
    system_hash=`sha256sum "$target" | cut -d ' ' -f 1`
    zygote_hash=`hash_in_ns "$zyg"`
    [ "$system_hash" = "$original_hash" ] && [ "$zygote_hash" = "$original_hash" ] || {
      echo restore_unverified
      exit 1
    }
    rm -f "$marker"
    echo restored
    ;;
  status)
    echo "system=`sha256sum "$target" | cut -d ' ' -f 1`"
    if can_enter_ns "$zyg"; then
      echo "zygote=`hash_in_ns "$zyg"`"
      echo namespace_access=available
    else
      echo zygote=unavailable
      echo namespace_access=denied
    fi
    dialer_pid=`pidof "$pkg" || true`
    if [ -n "$dialer_pid" ]; then
      for pid in $dialer_pid; do echo "dialer[$pid]=`process_hash "$pid" || true`"; done
    else
      echo dialer=stopped
    fi
    echo "source=$source"
    echo "candidate=`sha256sum "$source" | cut -d ' ' -f 1`"
    ;;
  *) echo 'usage: enable|disable|status'; exit 2 ;;
esac
