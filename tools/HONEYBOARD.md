# Nemotron con HoneyBoard

La APK final conserva el paquete y la firma de Nemotron e incorpora las clases,
recursos, diccionarios y bibliotecas nativas del APK de HoneyBoard 5.9.30.88.
El doble toque se detecta en el callback semántico de HoneyBoard: solo dos commits consecutivos del carácter de espacio activan el dictado. No se deduce por coordenadas ni por el área de la tecla. X, B, N y el resto del teclado no lo activan.

Para compilar, configura `JAVA_HOME` y `ANDROID_HOME`, ejecuta `gradlew.bat assembleDebug`
y empaqueta el resultado con:

```powershell
.\tools\merge-honeyboard.ps1 `
  -HoneyBoardApk 'ruta\HoneyBoard-5.9.30.88.apk' `
  -NemotronApk '.\app\build\outputs\apk\debug\app-debug.apk' `
  -OutputApk 'ruta\NemotronKeyboard.apk'
```

El APK de Gradle es intermedio: contiene un stub de `HoneyBoardApplication` para
compilar. El empaquetador lo sustituye por la aplicación original de Samsung,
incorpora sus componentes y conserva el orden de los atributos de sus layouts.
AndroidX, Material y Coroutines de HoneyBoard tienen su propio espacio de nombres
para que no sustituyan las bibliotecas de Nemotron. La APK final usa target SDK 33,
como el teclado parcheado original.

Durante el dictado, las barras de voz blancas reemplazan el texto de idioma de las
teclas de espacio. Usan el nivel RMS del reconocedor y recuperan las etiquetas al
detener. El dictado del teclado solicita `es-MX` y vibra al iniciar y detener;
la selección de idioma del teclado sigue siendo independiente.
