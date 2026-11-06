#!/system/bin/sh
# Session-only Android Auto resource patch. Never touches the system namespace.
set -eu

pkg=com.google.android.projection.gearhead
source=/data/local/tmp/nemotron-aa-18_1-rail1dp.apk
source_name=nemotron-aa-18_1-rail1dp.apk
marker=/data/local/tmp/nemotron-aa-rail.target
original=f3e90ea898469991785dbf91500e1d50db4c637e0e67483065ee0813e3b59c73
patched=6e14eb65f4df0293352ed5da478ada2445341b776956746693e75da205d17cd8

zyg=`pidof zygote64`
case "$zyg" in ''|*[!0-9]*) echo no_zygote; exit 1;; esac

get_target() {
  path=`pm path "$pkg" | sed -n '1s/^package://p'`
  if [ -z "$path" ]; then
    path=`cmd package list packages -u -f "$pkg" | sed -n 's/^package:\(.*\/base.apk\)=com\.google\.android\.projection\.gearhead$/\1/p'`
  fi
  printf '%s\n' "$path"
}

valid_target() {
  case "$1" in /data/app/*/com.google.android.projection.gearhead-*/base.apk) return 0;; *) return 1;; esac
}

case "${1:-}" in
  enable)
    target=`get_target`
    valid_target "$target" || { echo unsupported_path; exit 1; }
    [ -f "$source" ] || { echo candidate_missing; exit 1; }
    system_hash=`sha256sum "$target" | cut -d ' ' -f 1`
    source_hash=`sha256sum "$source" | cut -d ' ' -f 1`
    [ "$system_hash" = "$original" ] || { echo original_changed; exit 1; }
    [ "$source_hash" = "$patched" ] || { echo candidate_changed; exit 1; }
    # Android Auto processes must be able to read the mounted APK, including
    # after a reboot or restorecon changes the staging file's label.
    chown system:system "$source"
    chmod 0644 "$source"
    chcon u:object_r:apk_data_file:s0 "$source"
    zygote_hash=`nsenter -t "$zyg" -m -- sha256sum "$target" | cut -d ' ' -f 1`
    if [ "$zygote_hash" = "$patched" ]; then
      printf '%s\n' "$target" > "$marker"
      echo already_active
      exit 0
    fi
    [ "$zygote_hash" = "$original" ] || { echo unexpected_mount; exit 1; }
    printf '%s\n' "$target" > "$marker"
    am force-stop --user 0 "$pkg" >/dev/null
    if ! nsenter -t "$zyg" -m -- mount --bind "$source" "$target"; then
      rm -f "$marker"
      echo mount_failed
      exit 1
    fi
    zygote_hash=`nsenter -t "$zyg" -m -- sha256sum "$target" | cut -d ' ' -f 1`
    if [ "$zygote_hash" != "$patched" ]; then
      nsenter -t "$zyg" -m -- umount -l "$target" || true
      rm -f "$marker"
      echo verification_failed
      exit 1
    fi
    echo active
    ;;
  disable)
    if [ -f "$marker" ]; then target=`cat "$marker"`; else target=`get_target`; fi
    valid_target "$target" || { echo unsupported_path; exit 1; }
    # Each Android process may retain its own inherited mount namespace.
    # Detach only entries referencing our uniquely named candidate and target.
    for info in `grep -l -F "$source_name" /proc/[0-9]*/mountinfo 2>/dev/null || true`; do
      grep -F "$target" "$info" >/dev/null 2>&1 || continue
      pid=${info#/proc/}; pid=${pid%/mountinfo}
      nsenter -t "$pid" -m -- umount -l "$target" >/dev/null 2>&1 || true
    done
    system_hash=`sha256sum "$target" | cut -d ' ' -f 1`
    zygote_hash=`nsenter -t "$zyg" -m -- sha256sum "$target" | cut -d ' ' -f 1`
    [ "$system_hash" = "$original" ] && [ "$zygote_hash" = "$original" ] || { echo restore_unverified; exit 1; }
    rm -f "$marker"
    echo restored
    ;;
  *) echo 'usage: enable|disable'; exit 2;;
esac
