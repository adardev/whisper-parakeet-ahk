# Nemotron — rama `nemotron`

Código Android de Nemotron y herramientas para integrar componentes Samsung.

## One UI Home integrado

**Estado experimental:** instalado en el A54 como launcher predeterminado, con la interfaz de One UI Home y el teclado integrado de Nemotron. El usuario confirmó el bloqueo de altas automáticas. Los ajustes incluyen **Añadir aplicaciones** para elegir accesos manualmente; se permiten páginas adicionales creadas por el usuario.

Las nuevas instalaciones y reactivaciones no crean iconos automáticamente. Instagram y WhatsApp solo aparecen si el usuario añade sus accesos. Los widgets y los iconos existentes conservan su ruta nativa.

**Animaciones:** los accesos propios ya abren la app directamente desde el icono, sin la actividad intermediaria ni el comando de Shizuku cuando la app está habilitada. El cierre por gestos todavía depende de Recientes de Samsung: en el A54 sigue devolviendo un destino nulo. La animación exacta de retorno al icono aún no está resuelta.

- [Documentación, compilación y estado de pruebas](docs/oneui-home-integration.md)
- [Limpieza de intermediarios y archivos conservados](docs/oneui-home-cleanup.md)
- [Script de integración](tools/merge-oneui-home.ps1)
- [Adaptadores smali](tools/oneui-compat)
- [Dependencia HiddenApiBypass 6.2 y procedencia](tools/vendor/hiddenapibypass-6.2/README.md)

Las APK fallidas de prueba no se publican como versiones funcionales. Los cambios se guardan con commit y push antes de limpiar los archivos generados.
