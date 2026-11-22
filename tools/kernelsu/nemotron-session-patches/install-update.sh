#!/system/bin/sh
set -eu
stage=/data/local/tmp/nemotron-session-patches-update
mod=/data/adb/modules/nemotron-session-patches
store=/data/adb/nemotron-session-patches
[ -d "$store" ] || { echo candidate_store_missing; exit 1; }
for f in boot-completed.sh oneui-control.sh honeyboard-control.sh; do sh -n "$stage/$f"; done
for pair in 'nemotron-oneui-home-root-candidate.apk 45ab667243b1b0192267687848c82662ab38c06fcd5a96e55803eefcb635ce4c' 'nemotron-honeyboard-root-candidate-waveform.apk ca1bf8a41d7b6af7387296d400b56b3378b241773709ca455423b29c3975b11b' 'systemui-wallpaper-candidate.apk 73df730fd66944febf64867d32dc9ce032cba5afa5bf642054d2d2d6430e8b4e'; do
 set -- $pair
 [ "$(sha256sum "$store/$1" | cut -d ' ' -f1)" = "$2" ] || { echo candidate_hash_mismatch; exit 1; }
done
mkdir -p "$mod"
for f in module.prop oneui-control.sh honeyboard-control.sh boot-completed.sh; do cp "$stage/$f" "$mod/$f.new"; done
chmod 644 "$mod/module.prop.new"
chmod 755 "$mod/oneui-control.sh.new" "$mod/honeyboard-control.sh.new" "$mod/boot-completed.sh.new"
for f in module.prop oneui-control.sh honeyboard-control.sh boot-completed.sh; do mv -f "$mod/$f.new" "$mod/$f"; done
echo installed_no_reboot
