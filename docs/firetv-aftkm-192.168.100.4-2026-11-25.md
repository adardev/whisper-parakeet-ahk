# Fire TV AFTKM — 25 de noviembre de 2026

## Dispositivo

- Modelo reportado por ADB: `AFTKM` (`karat`).
- Destino: `192.168.100.4:5555`.
- Se desconectó la sesión ADB anterior a `192.168.100.6:5555` y se autorizó la depuración en el Fire TV `.4`.

## Aplicaciones

| Aplicación | Paquete | Resultado |
| --- | --- | --- |
| Amazon Music | `com.amazon.bueller.music` | La desinstalación para el usuario falló con `DELETE_FAILED_INTERNAL_ERROR`; quedó desactivada para el usuario 0. |
| Amazon Silk Browser | `com.amazon.cloud9` | La desinstalación para el usuario falló con `DELETE_FAILED_INTERNAL_ERROR`; quedó desactivada para el usuario 0. |
| Prime Video | `com.amazon.firebat` | Se quitó la versión descargada. El stub protegido de sistema permanece desactivado para el usuario 0; Fire OS rechazó quitarlo (`DELETE_FAILED_INTERNAL_ERROR`). El icono de Prime Video sigue visible en el launcher. |
| YouTube Music | — | No apareció instalada en `pm list packages -u`. `com.amazon.firetv.youtube` sí está instalado, pero corresponde a YouTube de Fire TV y se dejó intacto. |
| Xuper TV NF | `com.android.mgstxNF` | Instalación completada correctamente; versión `4.35.1`. |

Fire OS conserva los paquetes de sistema de Amazon aunque estén desinstalados o desactivados para el usuario 0. Las aplicaciones desactivadas se pueden restaurar con `adb shell pm enable --user 0 <package>`.

Prime Video está integrado además en `com.amazon.tv.launcher` mediante `PrimeVideoScreenDeepLinkActivityDI`. El launcher está protegido: Fire OS rechazó desactivar ese componente y ocultarlo con `pm hide` requiere `MANAGE_USERS`. Reiniciar el dispositivo no quitó el icono de la fila **Mis apps y canales**. El acceso del launcher, por tanto, sigue presente aunque el stub de Prime Video esté desactivado.

## APK instalada

- Archivo de origen: `XuperTv-NF-v4.35.1-ModByNF.apk`.
- Tamaño: `51,374,730` bytes.
- SHA-256: `0B76F3891D858FA55B9AD6FFFEF4F47A4CDEEE910F036D6B3322287130B9B333`.
- Comando: `adb -s 192.168.100.4:5555 install -r <ruta-al-APK>`.

## Comandos de referencia

```powershell
adb disconnect 192.168.100.6:5555
adb connect 192.168.100.4:5555
adb -s 192.168.100.4:5555 shell pm uninstall --user 0 com.amazon.bueller.music
adb -s 192.168.100.4:5555 shell pm disable-user --user 0 com.amazon.bueller.music
adb -s 192.168.100.4:5555 shell pm uninstall --user 0 com.amazon.cloud9
adb -s 192.168.100.4:5555 shell pm disable-user --user 0 com.amazon.cloud9
adb -s 192.168.100.4:5555 shell pm uninstall --user 0 com.amazon.firebat
adb -s 192.168.100.4:5555 shell pm disable-user --user 0 com.amazon.firebat
```

El comando de desinstalación de `com.amazon.firebat` quitó la versión descargada; el stub que queda en `/system/system_ext/priv-app/com.amazon.firebat-stub` no se pudo desinstalar para el usuario. El icono del launcher permaneció visible.
