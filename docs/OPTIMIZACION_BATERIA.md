# Seguimiento de consumo: Nemotron e Instagram

Fecha: 17 de octubre de 2026. Dispositivo: Galaxy A54 (SM-A546E, Android 16).

## Nemotron

Se redujeron las lecturas redundantes del árbol de accesibilidad de Instagram en `AntiScrollAccessibilityService`:

- Los cambios de contenido ya no vuelven a recorrer el árbol en cada evento. La detección de perfiles se limita por una ventana de 750 ms y la detección de pestañas/Direct por 500 ms.
- La lectura de la pestaña Search reutiliza el mismo `rootInActiveWindow` que ya se obtuvo para comprobar Direct, en vez de solicitar otro árbol para cada evento.
- Search y Direct usan el estado de pestaña cacheado entre comprobaciones. Un cambio visual puede tardar hasta 500 ms en reflejarse si Instagram no manda el evento de selección.

También se corrigió el tile de Sleep Mode. El receptor que termina la rutina nocturna al apagar la pantalla ahora persiste el estado activo cuando confirma la rutina y el modo avión. Si una alarma programada llega duplicada con el estado ya correcto, actualiza el estado persistido y solicita refrescar el tile.

### Verificación y lecturas

La APK Debug se compiló desde el commit base `80e9b8d` con JDK 17, se instaló encima de Nemotron con la misma firma y se conservó el estado de la aplicación. No se reinició el teléfono.

Se reprodujo el escenario nocturno con el A54 conectado: el estado persistido cambió de `enabled=false` a `enabled=true` tras apagar la pantalla, el modo avión siguió activo y el equipo se volvió a despertar. El valor guardado que lee el tile quedó en `true`; Android confirmó que el dispositivo seguía despierto al terminar.

| Escenario | CPU del proceso Nemotron | PSS |
|---|---:|---:|
| Inicio quieto, 30.118 s | 0.17 s; 0.56 % de un núcleo | 34,449 KiB |
| En segundo plano mientras Instagram estaba abierto, 30.112 s | 0.13 s; 0.43 % de un núcleo | 31,421 KiB |

Estas son lecturas posteriores al cambio, no una prueba A/B con medidas iniciales equivalentes. No se puede atribuirles una reducción porcentual.

## Instagram

La APK instalada continúa siendo Instagram 460.0.0.37.80, SHA-256 `c5af6f662e445b16ffeef9658e7e9776c18a03212b4c11913ac3a0d7bf31adfb`, publicada desde el repositorio privado Android Helium Browser. Ya contiene el arreglo del bucle Piko, autoplay desactivado, desplazamiento de Reels desactivado y la reducción de dos monitores. No se modificó ni reinstaló Instagram en esta pasada.

En el dispositivo se confirmó `POST_NOTIFICATION: ignore`, `RUN_ANY_IN_BACKGROUND: ignore` y bucket de espera `5` (Restricted). Una muestra de 30.273 s con la pestaña principal abierta, sumando Instagram y `com.instagram.android:fbns`, dio 3.99 s de CPU (**13.18 % de un núcleo**), PSS de 342,294 KiB y RSS de 510,310 KiB.

El teléfono estaba al 100 %, conectado por USB, en modo avión y con ahorro de energía activo. Por lo tanto, esta lectura no representa el comportamiento normal con red y no mide descarga real. Tampoco se compara de forma controlada con mediciones anteriores. No se atribuye un ahorro de batería a este valor.

## Estado de la medición energética

No se midió consumo real en mAh ni porcentaje de batería en una prueba desenchufada. Para saber si estas optimizaciones reducen la batería hace falta repetir una sesión de duración comparable, con misma señal, brillo, modo de energía y uso. No se recomienda quitar más servicios de Instagram sin identificar su función: algunos pueden afectar los mensajes directos.

## Ajuste adicional de batería de Nemotron (18 oct 2026)

La app se estaba agregando a sí misma a la lista de excepciones de Doze al iniciar `AutoFreezeScheduler`, aunque no estuviera en la lista elegida por el usuario. Además, `AutoFreezeScreenReceiver` volvía a añadirla al procesar `ACTION_SCREEN_OFF` y podía forzar Doze antes de terminar el trabajo de congelar las apps seleccionadas.

Se quitó la autoexcepción de `AutoFreezeScheduler`. El receptor ahora respeta exclusivamente la lista de excepciones configurada por el usuario, congela y detiene primero las apps elegidas y después solicita Doze. Si no hay apps elegidas, solicita Doze directamente. Así Android puede incluir Nemotron en reposo profundo cuando corresponde y no interrumpe el trabajo retrasado de congelación.

La corrección se compiló desde Kotlin y también se aplicó al DEX de la variante integrada One UI Home para no perder el launcher ni la animación reconstruida. Se firmó con el mismo certificado debug que la instalación existente y se instaló en el A54 sin reiniciar. Se verificó el rol HOME de Nemotron, que One UI Home sigue habilitado como respaldo y que Nemotron ya no figura en la lista de excepciones de Doze. En tres muestras `top`, el proceso marcó entre 0.0 % y 0.2 % de CPU; el PSS se estabilizó cerca de 297,808 KiB después del arranque (375,925 KiB durante el arranque). Son lecturas puntuales, no una comparación energética controlada ni una medida en mAh. No se probó un ciclo completo de pantalla apagada después de esta instalación.

## Reducción de trabajo repetido en Nemotron (18 oct 2026)

Se aplicaron tres cambios conservadores al launcher/teclado integrado:

- `AddictionGuard` ahora compara las listas y el flag de accesibilidad antes de escribir `Settings.Secure`; evita reescribirlos cuando el estado ya es correcto y provocar reconexiones innecesarias del servicio.
- `NemotronApp` ya no vuelve a persistir en cada arranque dos preferencias que ya se leen como activadas por defecto.
- `CarDetector.refresh()` agrupa las solicitudes simultáneas de arranque/binder y limita las consultas repetidas a una cada 30 s; la consulta se ejecuta en un hilo de prioridad mínima.

Se retiró el controlador de animación reconstruida y su llamada desde `ShortcutCloseTarget`; la ruta de cierre deja de dibujar overlays/capturas y queda a cargo de las transiciones nativas de Android/One UI. Se conservaron la asociación del destino y la opción de lanzamiento que necesita la integración de accesos.

La APK integrada se compiló con Gradle + Apktool 3.0.3, se alineó, firmó con el certificado debug ya instalado y `apksigner verify` pasó. Se instaló por ADB con Nemotron como rol HOME; no se reinició el teléfono. Tras dejarla quieta 15 s con pantalla bloqueada, `top` mostró 0.0 % de CPU para `com.nemotron.voiceime2` y `dumpsys cpuinfo` la redondeó a 0 %; Honeyboard también marcó 0 %. PSS fue 305,961 KiB y RSS 445,550 KiB. Son lecturas puntuales, no una medición de consumo eléctrico; no se midieron mAh ni autonomía.

One UI Home del sistema se deshabilitó por petición del usuario para quitar el diálogo recurrente de selección. Sigue instalado y se puede reactivar por ADB.

## Menos trabajo en segundo plano del teclado integrado (18 oct 2026)

La inspección de `dumpsys jobscheduler` encontró dos trabajos periódicos del HoneyBoard integrado, ambos diarios y condicionados a que el teléfono esté inactivo y la batería no esté baja: `KeyInputStatisticsUpdaterJobService` y `KeysCafeStatisticsJobService`. Se cancelan al iniciar Nemotron y ya no se vuelven a programar. Esto detiene la actualización periódica de estadísticas de escritura de Samsung y las estadísticas de Keys Cafe; el launcher, la entrada de texto y los ajustes visuales del teclado siguen disponibles.

En la muestra tomada antes del cambio, `top` mostró 0.0 % de CPU instantánea para el proceso de Nemotron; después, durante 30 s en reposo, el tiempo de CPU aumentó 0.04 s (aprox. 0.13 % de un núcleo). Android indicaba 0 mAh descargados porque el teléfono estaba conectado por USB y llevaba menos de dos segundos con batería en ese ciclo; por eso no se puede afirmar un ahorro porcentual ni comparar la autonomía con esta lectura. El sistema mantiene el proceso listo porque Nemotron es el launcher predeterminado, el IME activo y conserva su servicio de accesibilidad anti-adicción. Esos vínculos son necesarios para esas funciones y pueden mantener memoria ocupada aunque el proceso esté casi en 0 % de CPU cuando está quieto.

El cambio del teclado quedó en el script de integración y en el commit `cdaa06d`, pero la APK generada durante esa sesión omitió `res/xml/method.xml`; no se dejó instalada. Se restauró la APK anterior válida, se verificó el launcher y teclado Nemotron, y se cancelaron por ADB los dos trabajos diarios ya programados. La cancelación manual puede no persistir si el proceso antiguo vuelve a registrarlos. Antes de instalar el parche se necesita espacio temporal libre para reconstruir y validar la APK completa.
