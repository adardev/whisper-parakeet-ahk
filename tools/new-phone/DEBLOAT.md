# Inventario y criterio de paquete

`package-policy.json` empieza con una lista de eliminación vacía. Las aplicaciones protegidas no se podrán añadir por accidente. Tras ejecutar el inventario en el teléfono objetivo, comparar la lista candidata manualmente, revisar dependencias Samsung/Android/microG y registrar el motivo y método de restauración antes de aprobar un paquete. El comparador solo informa cuáles están instalados y cuáles ya faltan; no hace cambios.

Una automatización posterior debe separar `pm uninstall --user 0` (reversible con `cmd package install-existing`) de la eliminación física de una partición, que aquí no se habilitará por defecto. Antes de cualquier eliminación real se debe guardar el inventario, ruta del APK, estado enabled/suspended y dependientes del paquete.
