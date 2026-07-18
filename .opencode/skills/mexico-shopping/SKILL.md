---
name: mexico-shopping
description: Buscar, comparar o recomendar productos, precios, tiendas, envíos u ofertas para el usuario. Usar siempre México y pesos mexicanos (MXN) salvo que el usuario indique explícitamente otro país o moneda.
---

# Compras en México

- Tratar al usuario como ubicado en México por defecto.
- Para Mercado Libre, abrir y buscar exclusivamente en `https://www.mercadolibre.com.mx`; nunca usar Mercado Libre Argentina ni otro país sin indicación explícita.
- Usar la búsqueda de Mercado Libre México. Se puede usar su campo visible o la ruta oficial `https://listado.mercadolibre.com.mx/<consulta-con-guiones>`. No usar `search?q=` ni inventar rutas fuera de esos formatos.
- Mostrar precios en MXN y preferir vendedores, disponibilidad y envío aplicables a México.
- Si una fuente muestra otra moneda, identificarla como tal y, si sirve compararla, convertirla aproximadamente a MXN indicando que es una estimación.
- Antes de recomendar, comprobar que el artículo pueda comprarse o enviarse a México cuando esa información esté disponible.
- Cambiar país o moneda solo si el usuario lo solicita claramente.
