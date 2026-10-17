# Nemotron — rama `nemotron`

Código Android de Nemotron y herramientas para integrar componentes Samsung.

## One UI Home integrado

**Estado experimental:** el APK compila y se instala en el A54; el arranque del launcher sigue en validación. El inicio del sistema se mantiene como predeterminado durante las pruebas.

El objetivo es conservar la interfaz de One UI Home dentro de Nemotron, impedir la inserción automática de nuevas aplicaciones y ocultar las entradas nativas de Instagram y WhatsApp conservando los accesos de Nemotron.

- [Documentación, compilación y estado de pruebas](docs/oneui-home-integration.md)
- [Limpieza de intermediarios y archivos conservados](docs/oneui-home-cleanup.md)
- [Script de integración](tools/merge-oneui-home.ps1)
- [Adaptadores smali](tools/oneui-compat)
- [Dependencia HiddenApiBypass 6.2 y procedencia](tools/vendor/hiddenapibypass-6.2/README.md)

Las APK fallidas de prueba no se publican como versiones funcionales. Los cambios se guardan con commit y push antes de limpiar los archivos generados.
