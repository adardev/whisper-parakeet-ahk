#!/system/bin/sh
# Session-only HoneyBoard patch. PackageManager and /system keep Samsung's APK.
set -eu

pkg=com.samsung.android.honeyboard
source=/data/local/tmp/nemotron-honeyboard-root-candidate.apk
candidate_hash=62938ecd57db2f8907182fad9668fcc6663c4ac818eb092c427d8fecb841972c
original_hash=48bc8996a647e32b414bd78aab51b997908f600f1f19b900be6e9fd7b307edff
marker=/data/local/tmp/nemotron-honeyboard.target

zyg=`pidof zygote64`
case "$zyg" in ''|*[!0-9]*) echo no_zygote; exit 1;; esac

get_path() { pm path "$1" | sed -n '1s/^package://p'; }
valid_target() { [ "$1" = /system/app/HoneyBoard/HoneyBoard.apk ]; }
hash_in_ns() { nsenter -t "$1" -m -- sha256sum "$target" | cut -d ' ' -f 1; }
can_enter_ns() { nsenter -t "$1" -m -- true >/dev/null 2>&1; }

target=`get_path "$pkg"`
valid_target "$target" || { echo unsupported_target; exit 1; }

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
    # Match the already verified One UI Home controller on this A54.
    nsenter -t "$zyg" -m -- mount --bind "$source" "$target" || { rm -f "$marker"; echo mount_failed; exit 1; }
    [ "`hash_in_ns "$zyg"`" = "$candidate_hash" ] || {
      nsenter -t "$zyg" -m -- umount -l "$target" || true
      rm -f "$marker"
      echo zygote_verification_failed
      exit 1
    }
    # Only stop HoneyBoard so its next process inherits the private mount.
    am force-stop --user 0 "$pkg" >/dev/null 2>&1 || true
    echo active
    ;;
  disable)
    can_enter_ns "$zyg" || { echo namespace_access_denied; exit 1; }
    [ -f "$marker" ] && target=`cat "$marker"`
    valid_target "$target" || { echo unsupported_target; exit 1; }
    nsenter -t "$zyg" -m -- umount -l "$target" >/dev/null 2>&1 || true
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
    keyboard_pid=`pidof "$pkg" || true`
    if [ -n "$keyboard_pid" ]; then
      for pid in $keyboard_pid; do echo "keyboard[$pid]=`nsenter -t "$pid" -m -- sha256sum "$target" 2>/dev/null | cut -d ' ' -f 1`"; done
    else
      echo keyboard=stopped
    fi
    echo "source=$source"
    echo "candidate=`sha256sum "$source" | cut -d ' ' -f 1`"
    ;;
  *) echo 'usage: enable|disable|status'; exit 2 ;;
esac
