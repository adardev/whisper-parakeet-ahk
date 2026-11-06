# RETIRADO: Android Auto No Rail

**No compilar, instalar ni reactivar este módulo.** La versión 0.3 añadió un
`/product/overlay/config/config.xml` personalizado. En el Galaxy A54 causó un
crash de SystemServer (`config_servicesExtensionPackage` esperaba
`android.ext.services`) y varios arranques fallidos. El teléfono se recuperó
deshabilitando DFRoot; el módulo debe retirarse del dispositivo antes de volver
a iniciar KernelSU.

La fuente se conserva únicamente para el registro forense y está archivada en
`backups/kernelsu-recovery-2026-11-06.zip`. El recurso nunca llegó a ocultar la
barra de Android Auto y el APK original de Google no se reemplazó.

El overlay lleva `dimen/car_rail_height` y `dimen/cielo_rail_height` de 80 dp a
0 dp. No cambia la barra lateral vertical ni los controles multimedia
independientes. Está apuntado a Android Auto 18.1; Google puede cambiar estos
recursos en una actualización y entonces habrá que revisar el APK de nuevo.

## Recuperación

Consulta `docs/kernelsu-recovery-2026-11-06.md`. No uses el antiguo
`build.ps1`: ahora se detiene deliberadamente para evitar generar otra copia
instalable de este módulo.
