# Port de Nemotron Keyboard al Samsung Keyboard original (A54)

Fecha: 2026-11-16
Rama: `nemotron-root`

## Objetivo

Portar al HoneyBoard original las funciones que ya usa Nemotron Keyboard:

- Dictado en español al pulsar espacio dos veces.
- Salida del dictado en minúsculas.
- La autocapitalización automática respeta `SETTINGS_DEFAULT_AUTO_CAPS`; con esa opción apagada, se ignora `EditorInfo.CAP_SENTENCES` y Shift manual continúa disponible.
- Conservar el APK, paquete, componentes y recursos nativos Samsung como base.

## Donante y artefactos

El equipo conectado se identificó como `SM-A546E`; el APK instalado es HoneyBoard
5.9.30.88 en `/system/app/HoneyBoard/HoneyBoard.apk`, paquete
`com.samsung.android.honeyboard`.

Aunque antes no aparecía en la lista normal de IME, no estaba desinstalado:
`pm path` resuelve el APK de sistema y `dumpsys package` muestra
`installed=true`. Se volvió a habilitar para el usuario 0 (`enabled=1`); ahora
aparece en `ime list -s` junto con Nemotron. Este HoneyBoard es el teclado que se
conservará; Nemotron es únicamente la referencia de comportamiento.

| Artefacto | SHA-256 | Estado |
|---|---|---|
| APK Samsung leído del teléfono | `48bc8996a647e32b414bd78aab51b997908f600f1f19b900be6e9fd7b307edff` | Sin modificar en `/system` |
| Candidato Nemotron | `62938ecd57db2f8907182fad9668fcc6663c4ac818eb092c427d8fecb841972c` | Compilado, copiado a `/data/local/tmp/nemotron-honeyboard-root-candidate.apk` y montado durante la sesión |

El candidato fue construido a partir de la copia exacta del APK Samsung. Apktool
reconstruyó los cuatro DEX originales; el puente Java se añadió como
`classes5.dex`. `apksigner verify` y `aapt dump badging` confirmaron la firma de
trabajo y el paquete `com.samsung.android.honeyboard`. El binario se conserva en
el teléfono y en el directorio temporal local del trabajo; no se versiona el APK
OEM de 138 MB.

## Cambios implementados en el candidato

1. `patch-auto-caps.ps1` localiza la clase `cl/a` sin leer todos los archivos del
   APK. Cambia la rama que decide si se ignora `CAP_SENTENCES`; no desactiva
   Shift. En el teléfono, ambas preferencias Samsung y Nemotron ya estaban en
   `SETTINGS_DEFAULT_AUTO_CAPS=false`.
2. `stock-host/NemotronInlineDictation.java` maneja el doble espacio desde el
   proceso del IME. Solo acepta commits compuestos de espacios; un texto con
   letras reinicia el contador. Detecta el segundo commit dentro de 750 ms,
   elimina el primer espacio y consume el segundo para no dejar puntuación
   accidental.
3. `HoneyBoardService.onCreate()` enlaza el servicio existente con el puente.
   El puente usa `SpeechRecognizer` con idioma `es-MX`, vuelve a escuchar entre
   resultados parciales y pega la frase final en minúsculas con un espacio al
   final.
4. El manifest original ya declara `RECORD_AUDIO`. No se agrega un servicio
   Android nuevo, un permiso adicional ni un proceso de grabación autónomo. La
   función solo se activa con el gesto de doble espacio.

## Preparación reproducible

El código está en:

- `apps/nemotron-keyboard/build/stock-host/NemotronInlineDictation.java`
- `apps/nemotron-keyboard/build/prepare-stock-keyboard-root-candidate.ps1`
- `apps/nemotron-keyboard/build/patch-auto-caps.ps1`
- `apps/nemotron/src/main/res/raw/nemotron_honeyboard_control.sh`

El generador verifica la identidad del paquete y el SHA-256 del APK donante, hace
los cambios sobre un árbol temporal, compila Java a DEX, reconstruye, alinea,
firma y valida el APK. El controlador valida ambos hashes y apunta al paquete
original. Su objetivo es un montaje temporal en el namespace de Zygote; al
reiniciar, el APK del sistema sigue intacto y el montaje desaparece.

## Activación y verificación en el A54 (16 nov 2026)

- KernelSU Root de `com.nemotron.core` está autorizado. La sesión `adb shell su`
  tiene un perfil distinto y reporta `CapEff=0`; por eso sus intentos de
  `nsenter` fallan. Ejecutar `su` desde un shell del UID de Core
  (`run-as com.nemotron.core` → `su`) sí conserva las capacidades completas y
  permite entrar al namespace. No hizo falta cambiar perfiles de KernelSU.
- Se revisó el procedimiento de One UI Home registrado en los commits
  `385ad9b` y `ed3d588`: el mismo montaje temporal de Zygote y la misma
  comprobación de hashes. El controlador de HoneyBoard ahora usa la sintaxis
  `mount --bind` verificada en One UI Home.
- El paquete original estaba instalado pero deshabilitado para el usuario 0.
  Se volvió a habilitar (`enabled=1`), sin desinstalarlo ni modificar su APK.
- El controlador root devolvió `active`; `status` confirmó el candidato en
  Zygote y en el proceso del teclado, con el hash exacto de compilación.
  `/system` conserva el hash OEM.
- Se seleccionó `com.samsung.android.honeyboard/.service.HoneyBoardService`
  como IME predeterminado. `dumpsys input_method` confirmó que es el IME actual
  y el teclado se abrió en un campo de texto.
- Las teclas están en minúsculas con la opción automática de mayúsculas
  desactivada; el texto de comprobación visible quedó en minúsculas.
- Se concedió `RECORD_AUDIO` y AppOp `allow`, necesarios para el dictado
  solicitado. El doble espacio mostró “Escuchando…”; al detener sin hablar,
  mostró “No te escuché”. El reconocedor instalado es
  `com.google.android.tts`. Falta verificar una frase hablada real y su
  inserción final en minúsculas.
- Nemotron Keyboard permanece disponible para volver atrás si hiciera falta.
- No se instaló un módulo de arranque, no se escribió en `/system` y no se
  reinició el teléfono. El montaje dura esta sesión root y desaparece al
  reiniciar o terminarla. Después habrá que iniciar el root temporal y ejecutar
  `enable` otra vez para recuperar el parche.

El port está **montado y activo como HoneyBoard predeterminado**. El inicio del
dictado quedó verificado; la inserción de una frase real requiere una prueba
hablada en el teléfono.
