# Reparación de parches del launcher tras reinicio — 22 nov 2026

Se restauraron los dos cambios pedidos, usando el candidato integrado de One UI
Home que ya los contiene:

1. **Finder opaco.** El parche de las tarjetas/contenedores de Finder se agregó
   originalmente en el commit `4083183` (`Keep one Home page and make Finder
   cards opaque`). El constructor lo aplica mediante
   `apps/nemotron-ui/build/patch-finder-opaque-cards.ps1`, llamado desde
   `prepare-stock-oneui-root-candidate.ps1`.
2. **Status bar en Inicio.** El candidato Home de 100 ms corresponde al cambio
   documentado en `4499fc3` (`Reduce One UI Home status bar delay to 100ms`).
   Hash SHA-256: `406a11a4484c338a2fa2419cc9ca9df25668d6ca9070f828ec4f679342e834c5`.

## Causa del fallo tras reiniciar

El hook anterior esperaba `sys.boot_completed=1` y consultaba Zygote una sola
vez. El registro del último arranque muestra que esa condición llegó antes de
que Zygote y Package Manager estuvieran estables (`no_zygote`, `Can't find
service: package`); los controladores fallaron al resolver el APK del launcher.
La política de la barra continuó como `immersive.status=apps`, sin la excepción
para `com.sec.android.app.launcher`.

`boot-completed.sh` ahora espera, con límite de 180 segundos, tres comprobaciones
consecutivas del mismo PID de Zygote, su namespace accesible, Package Manager
disponible y `pm path` resolviendo la ruta stock del launcher. Solo entonces
prepara y activa los controladores. Si el framework no se estabiliza, registra
`framework_not_ready` y sale sin cambiar archivos del sistema.

## Estado aplicado

Se activó el montaje sin reiniciar. One UI Home y Zygote reportan el candidato
`406a11a4…42e834c5`; el APK en `/system` conserva el original
`b9782302…a18d9da1`. Al volver a Inicio, WindowManager reportó el inset
`statusBars` visible y la política fue
`immersive.status=apps,-com.sec.android.app.launcher`. El filtro de Settings
también quedó montado con el candidato `afcc203b…b8137d6f`.

El hook corregido quedó instalado en el módulo KernelSU activo y en el código
fuente del repositorio. No se reinició el teléfono, por lo que el siguiente
arranque natural todavía debe confirmar la nueva espera.
