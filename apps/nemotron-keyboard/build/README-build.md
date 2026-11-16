# Nemotron con HoneyBoard

La APK final conserva el paquete y la firma de Nemotron e incorpora las clases,
recursos, diccionarios y bibliotecas nativas del APK de HoneyBoard 5.9.30.88.
El doble toque se detecta en el callback semántico de HoneyBoard: solo dos commits consecutivos del carácter de espacio activan el dictado. No se deduce por coordenadas ni por el área de la tecla. X, B, N y el resto del teclado no lo activan.

Para generar la entrada intermedia, configura `JAVA_HOME` y `ANDROID_HOME`, ejecuta `gradlew.bat :apps:nemotron:assembleDebug -PnemotronComponent=integrated`
y empaqueta el resultado con:

```powershell
.\apps\nemotron-keyboard\build\merge-honeyboard.ps1 `
  -HoneyBoardApk 'ruta\HoneyBoard-5.9.30.88.apk' `
  -NemotronApk '.\apps\nemotron\build\outputs\apk\debug\nemotron-debug.apk' `
  -OutputApk 'ruta\NemotronKeyboard.apk'
```

No instales esa entrada intermedia: usa el identificador de Nemotron UI y
reemplazaría el launcher. Una compilación sin la propiedad genera únicamente
Core (`com.nemotron.core`). El APK integrado de Gradle contiene un stub de `HoneyBoardApplication` para
compilar. El empaquetador lo sustituye por la aplicación original de Samsung,
incorpora sus componentes y conserva el orden de los atributos de sus layouts.
AndroidX, Material y Coroutines de HoneyBoard tienen su propio espacio de nombres
para que no sustituyan las bibliotecas de Nemotron. La APK final usa target SDK 33,
como el teclado parcheado original.

Durante el dictado, las barras de voz blancas reemplazan el texto de idioma de las
teclas de espacio. Usan el nivel RMS del reconocedor y recuperan las etiquetas al
detener. El dictado del teclado solicita `es-MX` y vibra al iniciar y detener;
la selección de idioma del teclado sigue siendo independiente.

La compilación también aplica `patch-auto-caps.ps1` al HoneyBoard integrado. Si
`SETTINGS_DEFAULT_AUTO_CAPS` está desactivado, el teclado ignora la sugerencia
`CAP_SENTENCES` que algunas apps envían al campo de texto. La tecla Shift manual
sigue funcionando. Esto evita que chats inicien frases o palabras con mayúscula
por decisión del editor aunque la preferencia del teclado esté apagada.

## Port temporal al Samsung Keyboard original

`prepare-stock-keyboard-root-candidate.ps1` toma una copia local del APK de
HoneyBoard del teléfono y genera un candidato del mismo paquete
`com.samsung.android.honeyboard`. Conserva el APK Samsung como base y agrega
`stock-host/NemotronInlineDictation.java` al DEX, sin añadir componentes al
manifest: el dictado se ejecuta dentro del `HoneyBoardService` ya registrado.
El callback `ii/a.commitText` detecta dos commits de espacios en una ventana de
750 ms; al completar dictado el texto se inserta en minúsculas. No inicia
grabación en segundo plano ni activa el micrófono por sí solo. HoneyBoard ya
declara `RECORD_AUDIO`; Android pide que el usuario conceda ese permiso.

La preparación requiere el APK Samsung local, Apktool 2.12.1, SDK Android 36,
Java 17 y una debug keystore. Para el A54, el script verifica el SHA-256 del
donante antes de editarlo. El controlador `nemotron_honeyboard_control.sh`
comprueba hashes y está pensado para montar el candidato solo en el namespace
de Zygote, sin instalarlo, modificar `/system` ni alterar PackageManager.
Consulta [el estado del port en el A54](../../../docs/samsung-keyboard-nemotron-port-2026-11-16.md);
la bitácora distingue el candidato compilado del parche realmente activado.
