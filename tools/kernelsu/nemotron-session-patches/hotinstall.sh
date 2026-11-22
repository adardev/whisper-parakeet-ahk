#!/system/bin/sh
set -eu
MODDIR=${0%/*}
candidate=nemotron-oneui-home-root-candidate-v81.apk
expected=193b3d20f286c44b14284000d8406fab5e0b8e9a7275dc8718dfa2ddd5d8b96a
dst="/data/local/tmp/$candidate"
sha() { sha256sum "$1" | cut -d ' ' -f 1; }
[ "$(sha "$MODDIR/$candidate")" = "$expected" ] || { echo candidate_hash_mismatch; exit 1; }
if [ "$(sha "$dst" 2>/dev/null || true)" != "$expected" ]; then
  echo home_candidate_not_staged
  exit 1
fi
# Drop the previous Zygote mount, then activate the same validated build.
sh "$MODDIR/oneui-control.sh" disable
sh "$MODDIR/oneui-control.sh" enable
settings=nemotron-settings-app-filter-v1.apk
settings_expected=afcc203b80e8278c7000a17cd0651e37ba3b96aa9673d11fafeaee28b8137d6f
[ "$(sha "$MODDIR/$settings")" = "$settings_expected" ] || { echo settings_hash_mismatch; exit 1; }
settings_dst="/data/local/tmp/$settings"
if [ "$(sha "$settings_dst" 2>/dev/null || true)" != "$settings_expected" ]; then
  cp "$MODDIR/$settings" "$settings_dst.new"
  [ "$(sha "$settings_dst.new")" = "$settings_expected" ] || { echo settings_copy_mismatch; exit 1; }
  chmod 0644 "$settings_dst.new"
  chcon u:object_r:apk_data_file:s0 "$settings_dst.new"
  mv -f "$settings_dst.new" "$settings_dst"
fi
sh "$MODDIR/settings-app-filter-control.sh" enable
