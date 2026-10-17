# Limpieza de compilaciones One UI Home

Antes de borrar intermediarios se guardaron código y documentación en la rama `nemotron` de GitHub. Las APK fallidas no se publican como versiones funcionales.

Se eliminaron los árboles generados `app/build/oneui-home-validation`, `-2` y `-3`, junto con APK firmadas, alineadas y sin firmar de pruebas anteriores. C: pasó de unos 468 MB libres a más de 5 GB libres, conservando el árbol de trabajo actual `-4`.

Se autoriza limpiar los árboles temporales de `E:/NemotronBuild/intermediates/.nemotron-keyboard-build-*` excepto `8086b65478e7483e80fce5ed34b8329d`, que es la base limpia utilizada para reconstruir. Los demás son salidas Apktool de intentos anteriores y ocupan unos 10 GB. Las APK originales de entrada, el JDK, el framework A54 y la clave debug permanecen disponibles.

El código reproducible se conserva en `tools/merge-oneui-home.ps1` y `tools/oneui-compat`. El estado experimental y las limitaciones están en `oneui-home-integration.md`. No se borran proyectos ni datos de aplicaciones del teléfono durante esta limpieza.
