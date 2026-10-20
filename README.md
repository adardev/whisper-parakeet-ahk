# Nemotron — rama `nemotron`

Código Android de Nemotron y herramientas para integrar componentes Samsung.

## One UI Home integrado

**Estado experimental:** instalado en el A54 como launcher predeterminado, con la interfaz de One UI Home y el teclado integrado de Nemotron. El usuario confirmó el bloqueo de altas automáticas. Los ajustes incluyen **Añadir aplicaciones** para elegir accesos manualmente; se permiten páginas adicionales creadas por el usuario.

Las nuevas instalaciones y reactivaciones no crean iconos automáticamente. Instagram y WhatsApp solo aparecen si el usuario añade sus accesos. Los widgets y los iconos existentes conservan su ruta nativa.

**Animaciones:** se retiró el overlay que imitaba el retorno al icono. La integración conserva el lanzamiento y cierre nativos de One UI; el mapeo de paquetes de los accesos Nemotron ayuda a identificar la app, pero no garantiza que Samsung encuentre el icono de destino al cerrar. Véanse [implementación y resultados](docs/oneui-home-integration.md).

**Batería y compatibilidad bancaria:** el 18 de octubre se redujeron los eventos y lecturas de interfaz del servicio anti-scroll. Tras confirmar que BBVA bloqueaba el acceso con el servicio de Accesibilidad activo, el guard quedó como opción manual apagada por defecto y el monitor DNS se desacopló de Accesibilidad. La APK integrada se reconstruyó e instaló en el A54; Nemotron sigue como Inicio y teclado. Véase [el registro de optimización](docs/battery-optimization.md).

- [Documentación, compilación y estado de pruebas](docs/oneui-home-integration.md)
- [Limpieza de intermediarios y archivos conservados](docs/oneui-home-cleanup.md)
- [Código fuente de Nemotron Core](apps/nemotron)
- [Integración y compatibilidad de Nemotron UI](apps/nemotron-ui/build)
- [Integración de Nemotron Keyboard](apps/nemotron-keyboard/build)
- [Dependencia HiddenApiBypass 6.2 y procedencia](apps/nemotron-ui/build/vendor/hiddenapibypass-6.2/README.md)

Las APK fallidas de prueba no se publican como versiones funcionales. Los cambios se guardan con commit y push antes de limpiar los archivos generados.
