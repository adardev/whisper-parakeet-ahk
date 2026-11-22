# Parches reactivados al arrancar — 22 nov 2026

El módulo `nemotron-session-patches` vuelve a montar los cambios después de que
Android y KernelSU terminen de iniciar. Los montajes son privados al namespace
de Zygote: Package Manager y los APK originales del sistema conservan sus
firmas y hashes.

## Parches del módulo

| Función | Candidato | SHA-256 | Activación |
|---|---|---|---|
| One UI Home: status bar solo en Inicio y tarjetas de Finder opacas | Home 100 ms | `406a11a4484c338a2fa2419cc9ca9df25668d6ca9070f828ec4f679342e834c5` | Hook de arranque; Finder opaco viene del cambio `4083183` |
| Filtro de apps en Settings | Settings filter v1 | `afcc203b80e8278c7000a17cd0651e37ba3b96aa9673d11fafeaee28b8137d6f` | Hook de arranque |
| Samsung Phone: fondo negro del keypad | Dialer fullscreen | `f90284b44047fb60817cb2911fbb3cb1aba9ee009fa272db920827b8793e60e5` | Hook de arranque |
| Android Auto: rail de 1 dp | Android Auto 18.1.164344 | `6e14eb65f4df0293352ed5da478ada2445341b776956746693e75da205d17cd8` | Hook de arranque, solo con la versión original esperada |
| Waze para Android Auto | Candidato v7 | `738db3b2618d47db57a05a3ab69632b9bfdb161c45616795ebe7d79148f6be25` | Hook de arranque solo si APK y hash persistente coinciden con v7 |
| HoneyBoard y SystemUI/fondo | Candidatos conocidos | Hashes fijados en `boot-completed.sh` | Hook de arranque; SystemUI conserva su vigilancia de reversión |

El APK v7 de Waze se conserva en el almacén persistente de KernelSU. El hook
rechaza cualquier candidato distinto: los experimentos anteriores de templates
que causaban pantalla negra no se vuelven a activar. La ruta directa a navegación
tras elegir destino no está resuelta en v7; este montaje conserva la navegación
normal de Waze, además del mapa/toolbar conocido. El botón de Android Auto y el
puente de media de Nemotron conservan sus funciones existentes.

El puente de media no es un APK de sistema montado por Zygote: está en la APK de
Nemotron y selecciona la sesión activa de Salt Player o YouTube Music. Su acceso
de notificaciones queda guardado por Android. La selección de Waze en Android
Auto también pertenece al host/coche; el mount del APK de Waze no cambia el HOME
ni deshabilita otra aplicación.

## Espera de arranque

El error observado tras el reinicio era una carrera: `sys.boot_completed` ya
valía `1`, pero Zygote estaba reiniciándose y Package Manager todavía no
respondía. Los controladores recibían `no_zygote`, `Can't find service: package`
o una ruta de APK vacía. Ahora el hook espera hasta 180 segundos por tres
comprobaciones consecutivas del mismo PID de Zygote, namespace montable, servicio
Package Manager disponible y `pm path` del launcher coincidente con el destino
stock. Después prepara cada candidato por SHA-256 y ejecuta cada controlador.
La espera es acotada y no reinicia el dispositivo.

Los fallos de un candidato se registran con su nombre en
`/data/local/tmp/nemotron-session-patches.log`; un candidato inválido se omite
sin tocar `/system`. No se reinició el A54 para validar el nuevo arranque. La
revisión local encontró el ADB desconectado al preparar esta versión, así que el
paquete documenta el estado de arranque; la carga del módulo actualizado en el
teléfono necesita que KernelSU reciba esta versión del módulo.
