# Android Auto APK parchado

APK de prueba final para Android Auto 18.1.164344 (`181164344`), con las cuatro dimensiones de la barra inferior reducidas a 1 dp. SHA-256: `6e14eb65f4df0293352ed5da478ada2445341b776956746693e75da205d17cd8`.

Se probó en DHU 800×480 al encender y apagar el tile Auto. La barra inferior dejó de verse y la proyección recuperó prácticamente toda la altura. Falta comprobarlo en el Suzuki.

Android rechaza instalar directamente este APK porque está firmado con una clave local y el Android Auto del sistema lleva la firma de Google. Nemotron lo monta temporalmente en el espacio de zygote64 al encender el tile; el sistema conserva su APK original. Reiniciar elimina el montaje. El método, el script reproducible y los hashes están en `docs/android-auto-offline-patch.md`.
