# Asistente local de voz para Windows

Automatización personal para Windows basada en **AutoHotkey v2**, **Handy** (dictado local), **OpenCode CLI** y voz. El archivo principal es `wintab.ahk`.

El sistema tiene dos usos:

- `Win + S`: dicta con Handy y pega la transcripción directamente donde esté el cursor.
- `Win + O`: dicta una petición para OpenCode, pega su respuesta en la ventana que estaba activa.
  - Mantén `Win` presionado y pulsa `O` una vez: respuesta escrita/pegada.
  - Mantén `Win` presionado y pulsa `O` dos veces rápidamente: respuesta escrita/pegada y leída en voz alta.
  - Mientras está grabando para OpenCode, pulsa `Win + O` una vez para detener la grabación y enviar la petición.

Handy y el servidor de OpenCode se mantienen vivos tres minutos tras el último uso y se cierran solos para no gastar RAM, GPU y batería. El script también cierra el proceso WebView de Handy, pero no otros WebView del sistema.

## Arquitectura

```text
Win+S ──> Handy ──> transcripción ──> pega en la aplicación activa

Win+O ──> Handy ──> historial de Handy ──> OpenCode CLI ──> pega respuesta
                                                   └──────> TTS (solo doble O)
```

`wintab.ahk` cambia temporalmente el método de pegado de Handy:

- Para `Win + S` usa `direct`: Handy pega la transcripción.
- Para `Win + O` usa `none`: el script lee la transcripción desde el historial de Handy, la manda a OpenCode y evita pegar el prompt del usuario.
- Al cerrarse Handy por inactividad, restaura `direct`.

## Archivos importantes

| Ruta | Función |
| --- | --- |
| `wintab.ahk` | Script principal y demás automatizaciones personales. Ejecutar este archivo. |
| `VoiceAssistant\` | Runtime empaquetado usado por el script. No requiere Python para ejecutarse. |
| `VoiceAssistant\run_opencode_tts\run_opencode_tts.exe` | Envía el prompt a OpenCode y opcionalmente inicia voz. |
| `VoiceAssistant\tts_speak\tts_speak.exe` | Reproduce la respuesta con Edge TTS. |
| `VoiceAssistant\handy_history\handy_history.exe` | Lee la última transcripción no vacía de `history.db`. |
| `VoiceAssistant\handy_mode\handy_mode.exe` | Cambia `paste_method` de Handy entre `direct` y `none`. |
| `VoiceAssistant\runtime\npm-global\opencode.cmd` | OpenCode empaquetado para el servidor local; `wintab.ahk` lo usa sin una ruta fija de usuario. |
| `VoiceAssistant\find_program\find_program.exe` | Busca accesos directos del menú Inicio; el agente lo usa para abrir programas. |
| `.opencode\skills\mexico-shopping\SKILL.md` | Skill local que fija México/MXN y Mercado Libre México. |
| `handy_history.py`, `handy_mode.py`, `run_opencode_tts.py`, `tts_speak.py`, `find_program.py` | Código fuente de los helpers. Solo necesario si se quieren reconstruir los `.exe`. |

## Requisitos en otra PC

### Obligatorios

1. Windows 10/11.
2. [AutoHotkey v2](https://www.autohotkey.com/) instalado. No sirve AutoHotkey v1.
3. [Handy](https://handy.computer/) instalado en la ubicación normal:

   ```text
   C:\Users\<USUARIO>\AppData\Local\Handy\handy.exe
   ```

4. Modelo local de Handy descargado y seleccionado. Esta instalación usa:

   ```text
   handy-computer/nemotron-3.5-asr-streaming-0.6b-gguf/nemotron-3.5-asr-streaming-0.6b-Q8_0.gguf
   ```

   El archivo suele quedar en:

   ```text
   C:\Users\<USUARIO>\models\nemotron-3.5-asr-streaming-0.6b-Q8_0.gguf
   ```

   No borres ese modelo ni la caché/modelos de Handy: sin él Handy puede grabar audio pero guardar transcripciones vacías.

5. Microfono permitido para Handy.
6. Una cuenta/configuración funcional de OpenCode CLI. La autenticación de OpenCode no se guarda en este repositorio; configúrala en la nueva PC.

### Para control de navegador y búsquedas

7. Node.js LTS instalado. La voz y el servidor de OpenCode empaquetado no dependen del Node global, pero Playwright/OpenCode lo usan para navegar y buscar productos.
8. Un navegador Chromium instalado (Chrome, Edge, Helium, etc.). Actualiza la ruta del navegador en `opencode.jsonc`.

### No necesarios para ejecutar

- Python: no es requisito. Los helpers ya están empaquetados como `.exe`.
- Tesseract OCR: no lo usa el agente actual. Instálalo solo si quieres OCR local de imágenes/PDF escaneados.
- Git: no hace falta para usar el agente; solo sirve para versionar/subir el proyecto.

## Instalación paso a paso

1. Copia esta carpeta completa a una ruta sin espacios problemáticos, por ejemplo:

   ```text
   D:\VoiceAgent\autohotkey
   ```

2. Instala Handy, descarga el modelo Q8 indicado y configura su atajo de transcripción como `Ctrl + Espacio`.

3. Copia la carpeta `VoiceAssistant` completa. Es grande porque incluye OpenCode y sus dependencias; no omitas ninguno de sus subdirectorios.

4. Instala AutoHotkey v2 y ejecuta `wintab.ahk` con doble clic. El script se elevará a administrador automáticamente para que los atajos funcionen sobre aplicaciones elevadas.

5. Crea la configuración global de OpenCode en:

   ```text
   C:\Users\<USUARIO>\.config\opencode\opencode.jsonc
   ```

   Ejemplo mínimo (cambia las rutas de Node y navegador):

   ```jsonc
   {
     "$schema": "https://opencode.ai/config.json",
     "permission": "allow",
     "agent": {
       "build": {
         "prompt": "ACCION MINIMA. Sin saludos ni explicaciones largas. UBICACION PERMANENTE: México. Para búsquedas, compras, precios o tiendas, usar México y MXN por defecto. Mercado Libre siempre es https://www.mercadolibre.com.mx; nunca Argentina salvo que el usuario lo pida. Usar el campo de búsqueda o la ruta oficial https://listado.mercadolibre.com.mx/<consulta-con-guiones>. No usar search?q= ni inventar rutas.",
         "permission": { "edit": "allow", "bash": "allow" }
       }
     },
     "skills": {
       "paths": ["D:\\VoiceAgent\\autohotkey\\.opencode\\skills"]
     },
     "mcp": {
       "playwright": {
         "type": "local",
         "command": ["C:\\Program Files\\nodejs\\npx.cmd", "--yes", "@playwright/mcp", "--executable-path", "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", "--headed"],
         "enabled": true
       }
     }
   }
   ```

   `permission: allow` concede al agente permisos amplios. Úsalo únicamente en una PC y con una configuración en la que confíes.

6. Ajusta el modelo de OpenCode y completa su login siguiendo la documentación de OpenCode. Comprueba en una terminal que `opencode` responde antes de probar `Win + O`.

7. Prueba en este orden:

   - `Ctrl + Espacio` en Handy: debe transcribir.
   - `Win + S`: debe dictar y pegar.
   - `Win + O`, habla, y `Win + O` de nuevo: debe pegar la respuesta de OpenCode.
   - Mantén `Win` y pulsa `O` dos veces: debe pegar y también hablar.

## Ajustes de localización México

La skill `mexico-shopping` y el prompt de OpenCode establecen:

- México como país por defecto.
- Precios en MXN.
- Mercado Libre México: `mercadolibre.com.mx`.
- Ruta válida de búsqueda: `https://listado.mercadolibre.com.mx/<consulta-con-guiones>`.

Ejemplo correcto:

```text
https://listado.mercadolibre.com.mx/repelentes-para-cucarachas-insecticida
```

No se debe usar Mercado Libre Argentina salvo que se solicite expresamente.

## Solución de problemas

### Handy graba pero no transcribe

Normalmente falta el modelo Q8 o Handy lo tiene sin seleccionar. Abre Handy y selecciona de nuevo el modelo `nemotron-3.5-asr-streaming-0.6b-Q8_0.gguf`. Revisa que exista el archivo en `C:\Users\<USUARIO>\models`.

### `Win + O` no devuelve respuesta

1. Confirma que Handy transcribe con `Ctrl + Espacio`.
2. Confirma que OpenCode funciona en terminal y su cuenta/modelo está configurado.
3. Asegura que `VoiceAssistant\runtime\npm-global\opencode.cmd` exista.
4. Espera tres minutos si quieres que el servidor anterior se cierre y la siguiente petición lo reinicie.

### El agente abre el país equivocado

Comprueba que `opencode.jsonc` tenga el prompt de México y que su arreglo `skills.paths` apunte a `.opencode\skills`. Reinicia la instancia de OpenCode o espera su cierre por inactividad.

### Handy pega el prompt en vez de la respuesta

El modo de pegado debe ser `none` durante `Win + O`; `wintab.ahk` lo gestiona automáticamente. Si Handy se cerró forzosamente, usa `Win + S` una vez para que el script restaure `direct`.

## Subirlo a un repositorio

No necesitas tener Git instalado localmente. Puedes crear un repositorio vacío desde la web de GitHub y arrastrar los archivos allí desde el navegador. Para actualizaciones frecuentes, instalar Git for Windows sí resulta mucho más cómodo.

### Qué sí subir

- `wintab.ahk`
- `README.md`
- `.opencode\skills\mexico-shopping\SKILL.md`
- Los archivos fuente `*.py` si quieres conservar cómo se construyeron los helpers.
- Una configuración de ejemplo, sin rutas personales ni tokens.

### Qué no subir directamente

- `VoiceAssistant\` pesa aproximadamente 800 MB y contiene binarios/dependencias. Publícalo como un archivo ZIP adjunto a un **Release** de GitHub, o usa Git LFS si sabes administrarlo.
- `C:\Users\...\.config\opencode\opencode.jsonc` real: puede tener rutas personales y configuración privada.
- Tokens, claves, sesiones, cachés de navegador, historiales de Handy, bases de datos y modelos descargados.
- `.git`, `.agents`, `bin`, archivos `temp_*`, logs y archivos de compilación.

### Flujo recomendado sin Git instalado

1. En GitHub: **New repository** → crea el repo privado.
2. Sube los archivos pequeños indicados mediante **Add file → Upload files**.
3. Comprime `VoiceAssistant` como ZIP y súbelo en **Releases → Create a new release**.
4. En el README del repo, indica que hay que descargar y descomprimir ese ZIP junto a `wintab.ahk`.

Si después instalas Git for Windows, el flujo habitual es `git init`, `git add`, `git commit` y `git push`.

## Notas de mantenimiento

- No borres `VoiceAssistant` si quieres que funcione sin Python.
- No borres el modelo de Handy.
- Para actualizar los helpers `.py`, hace falta Python solo en la máquina de desarrollo y hay que volver a empaquetar sus `.exe`.
- El archivo `wintab.ahk` reúne otras automatizaciones personales además de voz; evita reemplazarlo por una versión reducida sin revisar esas funciones.
