# Preparación reproducible de un Galaxy para Nemotron

Este flujo documenta cómo reconstruir el teléfono desde cero. Se divide en inventario, respaldo, instalación de root temporal, debloat revisado, sustitución systemless por microG y despliegue de Nemotron UI, Keyboard y Core. **Por ahora solo está implementada la fase de inventario de solo lectura.** No ejecuta instalaciones, flasheos ni desinstalaciones.

## Rama de respaldo

La rama `backup/pre-new-phone-automation-2026-11-06` conserva el commit de Nemotron previo a esta automatización. La rama de trabajo sigue siendo `nemotron-root`.

## Orden objetivo

1. Leer modelo, firmware, estado del bootloader, paquetes instalados y módulos. Guardar inventario fechado y comparar paquetes con el manifiesto.
2. Respaldar configuración y archivos de usuario; generar un plan explícito para los paquetes que realmente existan. El modo inicial de cualquier fase destructiva será simulación.
3. Obtener DFRoot y KernelSU desde las fuentes indicadas, verificar firma/hash y confirmar root temporal. No parchear ni flashear boot/init_boot.
4. Aplicar debloat con paquetes aprobados uno por uno, con verificación de dependencias y procedimiento de restauración.
5. Instalar OverlayFS MetaModule y Noogle Catfish microG para Android 16, volver a registrar servicios y validar Self-Check, GMS/GSF/Phonesky y apps dependientes antes de continuar.
6. Construir y desplegar por separado Nemotron UI/Home, Nemotron Keyboard y Nemotron Core con los APK de referencia correctos; preservar HOME, teclado, bases y accesos existentes. Verificar cada paquete y función tras instalación.

## Inventario

Con un solo dispositivo conectado y ADB autorizado:

```powershell
.\tools\new-phone\Inventory.ps1
```

Para especificar ADB o serial:

```powershell
.\tools\new-phone\Inventory.ps1 -Adb C:\Users\adaredu\adb\adb.exe -Serial SERIAL
```

Cada ejecución crea `tools/new-phone/runs/<fecha>/` con paquetes, propiedades e inventario JSON. Los resultados son específicos del dispositivo y no se versionan. El inventario contiene identificadores sensibles; revisar antes de compartir.

## Fases todavía pendientes

No se automatiza el root, el debloat, la instalación de Noogle ni la instalación de APKs hasta completar un manifiesto de paquetes del modelo objetivo, localizar los artefactos versionados y definir controles de rollback. El A54 SM-A546E con Android 16 sirve como referencia observada, no como garantía para otro modelo. No incluir credenciales, llaves privadas, dumps personales ni datos de apps en Git.
