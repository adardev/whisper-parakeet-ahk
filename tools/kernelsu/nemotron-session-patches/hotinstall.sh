#!/system/bin/sh
set -eu
MODDIR=${0%/*}
candidate=nemotron-oneui-home-root-candidate-v83.apk
expected=fc48b7df73308343eddfa7694d3bfa634d5a7818f004c96476867e72ae6a882e
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
  chcon u:object_r:system_file:s0 "$settings_dst.new"
  mv -f "$settings_dst.new" "$settings_dst"
fi
sh "$MODDIR/settings-app-filter-control.sh" enable
