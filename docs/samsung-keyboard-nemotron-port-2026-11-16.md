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

| Artefacto | SHA-256 | Estado |
|---|---|---|
| APK Samsung leído del teléfono | `48bc8996a647e32b414bd78aab51b997908f600f1f19b900be6e9fd7b307edff` | Sin modificar en `/system` |
| Candidato Nemotron | `62938ecd57db2f8907182fad9668fcc6663c4ac818eb092c427d8fecb841972c` | Compilado y copiado a `/data/local/tmp/nemotron-honeyboard-root-candidate.apk`; sin montar |

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

## Estado del teléfono al cierre

- El IME predeterminado sigue siendo
  `com.nemotron.keyboard/com.samsung.android.honeyboard.service.HoneyBoardService`.
- `/system/app/HoneyBoard/HoneyBoard.apk` conserva el SHA-256 original.
- El candidato está en `/data/local/tmp`, pero **no está montado ni activo**.
- No se instaló ningún módulo de arranque, no se tocó `/system` y no se reinició.
- La prueba de `nsenter` no pudo abrir `/proc/<zygote>/ns/mnt`. El `su` de esta
  sesión reporta UID 0, pero `CapEff=0`; KernelSU/SELinux bloquea la entrada al
  namespace y no hay `CAP_SYS_ADMIN` para montar. Para evitar un montaje global
  o un reemplazo al arranque con firma OEM distinta, se dejó el sistema intacto.
- El permiso de micrófono del paquete Samsung se devolvió a su estado previo
  (`RECORD_AUDIO` denegado; AppOp `ignore`) porque el candidato no se activó.
- La última consulta de ADB reinició el daemon y ya no encontró el A54; no se
  volvió a intentar ninguna acción en el teléfono después de eso.

Por tanto, el port está **preparado y compilado**, pero no se afirma que el
teclado Samsung original ya esté ejecutando las funciones. Para activarlo hay
que recuperar acceso de montaje únicamente al namespace de Zygote desde
KernelSU; no se debe instalar este APK como actualización normal, ni sustituir
el APK del sistema, ni convertirlo en módulo de arranque sin una vía de retorno
probada.
