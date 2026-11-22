#!/system/bin/sh
# Root mount for filtering selected entries only in Settings > Apps.
set -eu

pkg=com.android.settings
target=/system/priv-app/SecSettings/SecSettings.apk
source=/data/local/tmp/nemotron-settings-app-filter-v1.apk
original_hash=d2708e14c50e8ec7f142774dfb000bebda1e94bdf4f209c8323746882155a93a
candidate_hash=afcc203b80e8278c7000a17cd0651e37ba3b96aa9673d11fafeaee28b8137d6f
marker=/data/local/tmp/nemotron-settings-app-filter.target
zygote=$(pidof zygote64)

case "$zygote" in ''|*[!0-9]*) echo no_zygote; exit 1;; esac
hash_path() { sha256sum "$1" | cut -d ' ' -f 1; }
hash_in_zygote() { nsenter -t "$zygote" -m -- sha256sum "$target" | cut -d ' ' -f 1; }
can_enter_zygote() { nsenter -t "$zygote" -m -- true >/dev/null 2>&1; }

if ! can_enter_zygote; then
  if [ "${NEMOTRON_MOUNT_MASTER_RETRY:-0}" = 1 ]; then echo namespace_access_denied; exit 1; fi
  exec su -mm -c "NEMOTRON_MOUNT_MASTER_RETRY=1 sh '$0' '${1:-}'"
fi

case "${1:-}" in
  enable)
    [ "$(pm path "$pkg" | sed -n '1s/^package://p')" = "$target" ] || { echo unsupported_target; exit 1; }
    [ "$(hash_path "$target")" = "$original_hash" ] || { echo original_changed; exit 1; }
    [ "$(hash_path "$source")" = "$candidate_hash" ] || { echo candidate_changed; exit 1; }
    chmod 0644 "$source"
    # Samsung's KernelSU shell can apply system_file but rejects apk_data_file.
    chcon u:object_r:system_file:s0 "$source"
    current=$(hash_in_zygote)
    if [ "$current" = "$candidate_hash" ]; then echo already_active; exit 0; fi
    [ "$current" = "$original_hash" ] || { echo unexpected_existing_mount; exit 1; }
    printf '%s\n' "$target" > "$marker"
    nsenter -t "$zygote" -m -- mount --bind "$source" "$target"
    if [ "$(hash_in_zygote)" != "$candidate_hash" ]; then
      nsenter -t "$zygote" -m -- umount -l "$target" || true
      rm -f "$marker"
      echo verification_failed
      exit 1
    fi
    am force-stop --user 0 "$pkg" >/dev/null 2>&1 || true
    echo active
    ;;
  disable)
    if [ -f "$marker" ]; then target=$(cat "$marker"); fi
    [ "$target" = /system/priv-app/SecSettings/SecSettings.apk ] || { echo unsupported_target; exit 1; }
    nsenter -t "$zygote" -m -- umount -l "$target" >/dev/null 2>&1 || true
    for info in $(grep -l -F "$target" /proc/[0-9]*/mountinfo 2>/dev/null || true); do
      grep -F "$target" "$info" >/dev/null 2>&1 || continue
      pid=${info#/proc/}; pid=${pid%/mountinfo}
      nsenter -t "$pid" -m -- umount -l "$target" >/dev/null 2>&1 || true
    done
    am force-stop --user 0 "$pkg" >/dev/null 2>&1 || true
    [ "$(hash_path "$target")" = "$original_hash" ] || { echo system_original_mismatch; exit 1; }
    [ "$(hash_in_zygote)" = "$original_hash" ] || { echo restore_unverified; exit 1; }
    rm -f "$marker"
    echo restored
    ;;
  status)
    echo "system=$(hash_path "$target")"
    echo "zygote=$(hash_in_zygote)"
    echo "candidate=$(hash_path "$source")"
    ;;
  *) echo 'usage: enable|disable|status'; exit 2;;
esac
