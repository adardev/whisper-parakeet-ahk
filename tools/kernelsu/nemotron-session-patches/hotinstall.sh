#!/system/bin/sh
set -eu
MODDIR=${0%/*}
candidate=nemotron-oneui-home-root-candidate-v81.apk
expected=193b3d20f286c44b14284000d8406fab5e0b8e9a7275dc8718dfa2ddd5d8b96a
dst="/data/local/tmp/$candidate"
sha() { sha256sum "$1" | cut -d ' ' -f 1; }
[ "$(sha "$MODDIR/$candidate")" = "$expected" ] || { echo candidate_hash_mismatch; exit 1; }
cp "$MODDIR/$candidate" "$dst.new"
[ "$(sha "$dst.new")" = "$expected" ] || { rm -f "$dst.new"; echo candidate_copy_mismatch; exit 1; }
chmod 0644 "$dst.new"
chcon u:object_r:apk_data_file:s0 "$dst.new"
mv -f "$dst.new" "$dst"
# Drop the previous Zygote mount, then activate the same validated build.
sh "$MODDIR/oneui-control.sh" disable
sh "$MODDIR/oneui-control.sh" enable
