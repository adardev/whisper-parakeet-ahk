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
| Candidato inicial | `62938ecd57db2f8907182fad9668fcc6663c4ac818eb092c427d8fecb841972c` | Respaldo local de la revisión sin ondas |
| Candidato con ondas | `ca1bf8a41d7b6af7387296d400b56b3378b241773709ca455423b29c3975b11b` | Montado en Zygote y probado en el proceso HoneyBoard |

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
5. `NemotronKeyboardWaveform.java` oculta la etiqueta de idioma en la barra
   espaciadora mientras se escucha y dibuja nueve barras blancas dentro de su
   misma área. La amplitud responde a `RecognitionListener.onRmsChanged`; al
   detener o terminar, limpia el indicador y restaura el texto original.
6. Se eliminaron todos los `Toast` propios del puente (inicio, error y frase
   vacía). Las ondas son la única señal visual durante la escucha.

## Preparación reproducible

El código está en:

- `apps/nemotron-keyboard/build/stock-host/NemotronInlineDictation.java`
- `apps/nemotron-keyboard/build/stock-host/NemotronKeyboardWaveform.java`
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
- El candidato inicial se desmontó con el controlador reversible y se confirmó
  el hash original en Zygote. Después se montó el candidato con ondas desde
  `/data/local/tmp/nemotron-honeyboard-root-candidate-waveform.apk`.
- `status` confirmó el nuevo hash en Zygote y en el proceso activo HoneyBoard;
  `/system` conservó el hash OEM durante el reemplazo.
- Se seleccionó `com.samsung.android.honeyboard/.service.HoneyBoardService`
  como IME predeterminado. `dumpsys input_method` confirmó que es el IME actual
  y el teclado se abrió en un campo de texto.
- Las teclas están en minúsculas con la opción automática de mayúsculas
  desactivada; el texto de comprobación visible quedó en minúsculas.
- Se concedió `RECORD_AUDIO` y AppOp `allow`, necesarios para el dictado
  solicitado. El reconocedor instalado es `com.google.android.tts`.
- Durante la escucha, las nueve barras sustituyeron visualmente a “Español
  (US)” en la tecla espaciadora; no apareció ningún `Toast`. Al detener, las
  ondas desaparecieron y la etiqueta de idioma volvió a mostrarse.
- Se habló una frase real: el reconocedor la insertó correctamente en minúsculas.
  La frase se usó solo como borrador de prueba y se borró sin enviarla.
- No se instaló un módulo de arranque, no se escribió en `/system` y no se
  reinició el teléfono. El montaje dura esta sesión root y desaparece al
  reiniciar o terminarla. Después habrá que iniciar el root temporal y ejecutar
  `enable` otra vez para recuperar el parche.

El port está **montado y activo como HoneyBoard predeterminado**. Se verificaron
el indicador animado, la restauración de la etiqueta, la ausencia de Toast y la
inserción de voz real en minúsculas.

## Desinstalación de Nemotron Keyboard del A54

Después de validar el port, se desinstaló del teléfono la aplicación separada
Nemotron Keyboard (`com.nemotron.keyboard`) mediante:

```powershell
adb uninstall com.nemotron.keyboard
```

ADB respondió `Success`. Se comprobó después que `pm path
com.nemotron.keyboard` no devuelve una ruta y que el método de entrada
predeterminado sigue siendo
`com.samsung.android.honeyboard/.service.HoneyBoardService`. `ime list -s`
también muestra HoneyBoard como el único método de entrada instalado. Así que
la app independiente se quitó sin cambiar el teclado predeterminado.

La desinstalación afectó solo al usuario del dispositivo: no se borró el código
ni el APK versionado del repositorio (`apps/nemotron-keyboard/`), ni se tocó
Nemotron Core, Nemotron UI, el APK OEM de HoneyBoard en `/system` o el candidato
parcheado activo. No se hizo un reinicio. El archivo de la app independiente
permanece disponible en el repositorio por si se desea reinstalarla; el port
actual usa el paquete OEM de HoneyBoard y no necesita esa app separada.

### Estado final verificado

- `com.nemotron.keyboard`: desinstalado del teléfono (`adb uninstall` devolvió
  `Success`; `pm path` vacío).
- IME predeterminado: Samsung HoneyBoard, paquete
  `com.samsung.android.honeyboard`.
- `ime list -s`: HoneyBoard aparece como único IME.
- HoneyBoard OEM sigue en `/system/app/HoneyBoard/HoneyBoard.apk`; su hash
  original no se modificó.
- El candidato con ondas se instaló como montaje temporal en el namespace de
  Zygote, no como reemplazo permanente de `/system`. Sobrevive mientras dure la
  sesión root; después de reiniciar hay que iniciar DFRoot y ejecutar el
  controlador `enable` de nuevo.
- No se reinició el teléfono durante la desinstalación.
