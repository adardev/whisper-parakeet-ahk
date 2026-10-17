# Limpieza de compilaciones One UI Home

Antes de borrar intermediarios se guardaron código y documentación en la rama `nemotron` de GitHub. Las APK fallidas no se publican como versiones funcionales.

Se eliminaron los árboles generados `app/build/oneui-home-validation`, `-2` y `-3`, junto con APK firmadas, alineadas y sin firmar de pruebas anteriores. C: pasó de unos 468 MB libres a más de 5 GB libres, conservando el árbol de trabajo actual `-4`.

Se eliminaron los árboles temporales de `E:/NemotronBuild/intermediates/.nemotron-keyboard-build-*` excepto `8086b65478e7483e80fce5ed34b8329d`, que es la base limpia utilizada para reconstruir. Las salidas Apktool eliminadas ocupaban unos 10 GB de contenido; E: quedó con aproximadamente 12,4 GB libres. Las APK originales de entrada, el JDK, el framework A54 y la clave debug permanecen disponibles.

El código reproducible se conserva en `tools/merge-oneui-home.ps1` y `tools/oneui-compat`. El estado experimental y las limitaciones están en `oneui-home-integration.md`. No se borran proyectos ni datos de aplicaciones del teléfono durante esta limpieza.

Después del push 4bc500f se eliminaron también oneui-home-validation-4 y la reconstrucción fallida oneui-repro. C: quedó con 7,2 GB libres antes de iniciar la siguiente reconstrucción. La nueva salida utiliza de nuevo oneui-repro; la APK base limpia y los originales se conservaron.


Temporales adicionales revisados: honeyboard-resource-donor es una decodificación de inspección que ya no usa el integrador. HoneyBoard-original.apk es idéntico byte a byte a A54-HoneyBoard.apk (SHA-256 48bc8996a647e32b414bd78aab51b997908f600f1f19b900be6e9fd7b307edff). Se conserva A54-HoneyBoard.apk y se retiran el duplicado y esa decodificación, unos 488 MB de contenido. Los árboles de clon anteriores se mantienen hasta revisar sus diferencias.


También se retiran las salidas experimentales antiguas Nemotron-OneUIHome* de app/build/outputs/apk/debug: clones aligned/unsigned, final aligned/unsigned, Compat, Filtered, KotlinFiltered y sus APK/idsig obsoletos. Son intentos fallidos anteriores, no la reconstrucción actual de app/build/oneui-repro. Se conservan las APK NemotronKeyboard y las APK originales de entrada. Antes de esta retirada se hizo commit y push de scripts, adaptadores y documentación.


Resultado de la última retirada: 20 archivos obsoletos y 1.151 MB de contenido eliminados. Espacio libre observado al terminar esa limpieza: C: 6,47 GB; E: 11,56 GB. La reconstrucción actual y su árbol de trabajo permanecen disponibles para continuar la validación.

