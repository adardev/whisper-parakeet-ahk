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
