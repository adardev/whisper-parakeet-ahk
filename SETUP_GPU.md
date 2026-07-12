# Guía de Configuración Rápida (Whisper GPU)

Para clonar y usar este repositorio en una computadora nueva con la rama `whisper-gpu`, sigue estos pasos sencillos:

## 1. Descargar el Modelo Whisper (Una sola vez)
El script busca el modelo Whisper Small en la caché local de tu computadora.
1. Crea la siguiente ruta de carpetas si no existe:
   `C:\Users\<TuUsuario>\AppData\Local\eddy\models\whisper-small-int8-ov`
2. Descarga los archivos del modelo Whisper Small OpenVINO INT8 y colócalos dentro de esa carpeta.
   *(Los archivos incluyen: `openvino_encoder_model.xml`, `openvino_encoder_model.bin`, `openvino_tokenizer.xml`, `tokenizer.json`, etc.)*

## 2. Clonar el Repositorio
Clona este repositorio asegurándote de usar la rama `whisper-gpu`:
```bash
git clone -b whisper-gpu https://github.com/adardev/whisper-parakeet-ahk.git
```

## 3. Ejecutar y listo
1. Haz doble clic en el archivo `wintab.ahk` para iniciar el script.
2. Si te pide permisos de Administrador (UAC), haz clic en **Sí** (esto es necesario para interceptar los atajos globales y correr los procesos de audio).
3. Presiona `Win + S` para comenzar a dictar. El primer dictado cargará y compilará el modelo en tu GPU en menos de 2 segundos (gracias al warm-up y al caché dinámico local).

---

### ¿Cómo funciona la compatibilidad automática?
* **Micrófono DirectShow**: El script consulta el registro de Windows al pulsar la tecla rápida para usar el ID del micrófono correcto según la laptop que uses.
* **Caché de Compilación**: Los archivos temporales de compilación GPU se crean localmente en tu carpeta `./cache` (que está excluida de Git), evitando conflictos entre diferentes computadoras.
* **Ruta de tempfiles.vbs**: El script busca la ruta `F:\tempfiles\tempfiles.vbs` y, de no existir, cambia automáticamente a `D:\tempfiles\tempfiles.vbs`.
