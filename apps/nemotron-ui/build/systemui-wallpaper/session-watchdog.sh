#!/system/bin/sh
target=/system/system_ext/priv-app/SystemUI/SystemUI.apk
zyg="$(pidof zygote64)"
[ -n "$zyg" ] || exit 1
[ "$(sha256sum "$target" | cut -d' ' -f1)" = 0b53ae0d66a94ada8188a3c3ed5bfb44f7e62efec70b56539c4de42bc9ce6642 ] || exit 2
[ "$(sha256sum /data/local/tmp/systemui-wallpaper-candidate.apk | cut -d' ' -f1)" = 73df730fd66944febf64867d32dc9ce032cba5afa5bf642054d2d2d6430e8b4e ] || exit 3
nsenter -t "$zyg" -m -- mount --bind /data/local/tmp/systemui-wallpaper-candidate.apk "$target" || exit 4
( sleep 45; if [ ! -f /data/local/tmp/systemui-wallpaper-confirmed ]; then nsenter -t "$zyg" -m -- umount -l "$target"; kill -9 $(pidof com.android.systemui) 2>/dev/null; fi ) >/data/local/tmp/systemui-wallpaper-watchdog.log 2>&1 </dev/null &
kill -9 $(pidof com.android.systemui)
echo started