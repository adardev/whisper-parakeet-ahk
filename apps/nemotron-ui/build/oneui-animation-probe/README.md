# Diagnóstico de animaciones del A54

`AnimationProbe.java` se ejecuta como shell mediante `app_process`, fuera del APK instalado. Enumera las APIs de animación disponibles en el firmware. Con `--next paquete` registra un callback de diagnóstico para la siguiente apertura de ese llamador y finaliza su proceso tras 15 segundos. Si recibe un callback, lo termina inmediatamente: **no implementa la animación del producto**.

No cambia el componente Recientes, no registra un controlador global de transiciones y no instala una app. La opción `--next` es para comprobaciones supervisadas; no debe ejecutarse como servicio permanente.

## Compilar y ejecutar

Usar un JDK con `javac`, Android SDK 36 y D8. Ejemplo PowerShell desde la raíz, con `$jdk` y `$sdk` apuntando a las instalaciones locales:

```powershell
$probe = 'apps/nemotron-ui/build/animation-probe'
New-Item -ItemType Directory -Force "$probe/classes", "$probe/dex" | Out-Null
& "$jdk/bin/javac.exe" --release 8 -cp "$sdk/platforms/android-36/android.jar" -d "$probe/classes" apps/nemotron-ui/build/oneui-animation-probe/AnimationProbe.java
$classes = (Get-ChildItem "$probe/classes" -Filter '*.class').FullName
& "$jdk/bin/java.exe" -cp "$sdk/build-tools/36.0.0/lib/d8.jar" com.android.tools.r8.D8 --lib "$sdk/platforms/android-36/android.jar" --output "$probe/dex" $classes
& "$jdk/bin/jar.exe" cf "$probe/probe.jar" -C "$probe/dex" classes.dex
adb push "$probe/probe.jar" /data/local/tmp/nemotron-animation-probe.jar
adb shell 'CLASSPATH=/data/local/tmp/nemotron-animation-probe.jar app_process /system/bin AnimationProbe'
```

La prueba puntual usa `AnimationProbe --next com.sec.android.app.launcher`, seguido de un gesto de Inicio dentro del plazo. En el A54 se aceptó el registro con UID 2000, pero el gesto no llamó al runner. Esto no prueba que una transición por apertura de actividad falle: son rutas distintas.

## Grabar el comportamiento real

```powershell
scrcpy --no-playback --no-audio --max-fps=60 --time-limit=20 --record=apps/nemotron-ui/build/oneui-repro/gesture-before.mp4
```

Abrir la app desde su icono y volver mediante el gesto habitual durante la grabación. El video tiene frecuencia variable: el límite de 60 fps no significa que se hayan capturado 60 fotogramas cada segundo. Examinar los fotogramas alrededor del cierre, no solo la pantalla final.

Los videos pueden contener chats, widgets y otros datos personales. Se guardan bajo `apps/nemotron-ui/build`, que está ignorado por Git; no forman parte del código publicado.
