# Guía de entrega para el frontend

## Estado actual

El backend está implementado: productos/inventario, pedidos y pagos simulados; CQRS lógico y SAGA orquestada con transacciones JTA independientes. Se validaron 19 pruebas unitarias y 10 de integración con WildFly y PostgreSQL el 6 de octubre de 2026. Todavía no hay interfaz gráfica.

Stack acordado: Java 21, Jakarta EE 11, WildFly, Jakarta REST, CDI, JPA, JTA, PostgreSQL, Maven/WAR y Docker Compose. Mantener el monolito modular; no introducir Spring, microservicios ni un broker de mensajes.

## 1. Descargar y ejecutar

Clonar este repositorio desde GitHub (botón Code → copiar URL) o descargar el ZIP y descomprimirlo. Para colaborar es preferible clonar, pues el ZIP no incluye el historial Git.

Desde la carpeta del proyecto, con Docker Desktop abierto:

```sh
docker compose up --build -d
docker compose ps
```

Comprobar `http://localhost:8080/api/health`: debe devolver `{"database":"UP","status":"UP"}`. Si el puerto 8080 está ocupado, configurar APP_PORT en un archivo `.env` a partir de `.env.example` y usar ese puerto al navegar. No subir `.env` ni contraseñas reales.

## 2. Trabajo principal: interfaz visual

Propuesta simple, sin dependencias adicionales: HTML, CSS y JavaScript dentro del mismo WAR.

```text
src/main/webapp/
  index.html          ← pantalla inicial
  css/styles.css      ← estilos
  js/api.js           ← fetch y manejo de errores
  js/app.js           ← pantallas e interacción
  WEB-INF/            ← configuración existente; conservar
```

Los archivos públicos deben ir fuera de WEB-INF. Usar rutas relativas `/api/...` para compartir el origen con el backend y evitar configuración CORS. La interfaz quedará en `http://localhost:8080/`. Después de editar, reconstruir con `docker compose up --build -d`.

### Pantallas y criterios de aceptación

- [ ] Catálogo: listar productos, mostrar nombre/precio/stock y seleccionar cantidades.
- [ ] Gestión de productos: crear producto, editar precio o stock y eliminar; mostrar el conflicto si el producto tiene reservas.
- [ ] Carrito y creación de pedido: pedir customerId y enviar productId/quantity. Guardar el id devuelto; el precio y total los calcula el backend.
- [ ] Procesamiento: dos acciones explícitas para simular pago APPROVE o REJECT. Crear un pedido y procesarlo son llamadas distintas.
- [ ] Resultado: mostrar estado del pedido, estado de la SAGA, reserva, pago, importe y motivo de fallo.
- [ ] Consultar pedido por id. No existe GET /api/orders para listar todos: conservar los ids creados en la sesión o permitir introducir uno. No llamar a un endpoint inexistente.
- [ ] Estados de carga, lista vacía y errores visibles. Deshabilitar el botón mientras se procesa para evitar doble clic.
- [ ] Actualizar stock y estado después del proceso. No simular descuentos ni compensaciones en JavaScript.
- [ ] Diseño usable en móvil y escritorio, campos etiquetados y navegación por teclado.

La SAGA es síncrona: POST /process devuelve el resultado final cuando completa o compensa; no fingir una línea de tiempo en vivo. GET /orders/{id}/process permite consultar el estado persistido. Un pedido recién creado todavía no tiene proceso y esa consulta puede devolver 404.

## 3. Contrato de API

Todas las rutas tienen prefijo `/api`. Para cuerpos JSON enviar `Content-Type: application/json`.

| Acción | Método y ruta | Cuerpo / resultado |
|---|---|---|
| Listar productos | GET /products | Lista de id, name, price, availableStock |
| Crear producto | POST /products | `{"name":"Teclado","price":50.00,"availableStock":10}`; devuelve 201 |
| Consultar producto | GET /products/{id} | Producto |
| Cambiar precio | PUT /products/{id}/price | `{"price":55.00}` |
| Cambiar stock disponible | PUT /products/{id}/stock | `{"availableStock":15}`; reemplaza el valor, no suma |
| Eliminar producto | DELETE /products/{id} | 204 sin cuerpo; 409 si tiene reservas |
| Consultar inventario | GET /products/{id}/inventory | availableStock y reservedQuantity |
| Crear pedido | POST /orders | `{"customerId":"cliente-1","items":[{"productId":1,"quantity":2}]}`; devuelve 201 y estado PENDING |
| Consultar pedido | GET /orders/{id} | Pedido, líneas y totalAmount |
| Aprobar pago | POST /orders/{id}/process?payment=APPROVE | Sin cuerpo; ejecuta la SAGA |
| Rechazar pago | POST /orders/{id}/process?payment=REJECT | Sin cuerpo; ejecuta compensaciones |
| Consultar SAGA | GET /orders/{id}/process | orderStatus, processStatus, currentStep, lastError, reservationStatus, paymentStatus, paymentAmount, orderTotal |

Usar los ids reales devueltos; el id 1 es solo un ejemplo. No repetir productId dentro del mismo pedido: agrupar cantidades en el carrito.

Estados relevantes: pedido PENDING/CONFIRMED/CANCELLED; proceso RUNNING/COMPENSATING/COMPLETED/COMPENSATED/FAILED. Los valores de reserva/pago pueden ser null si el paso no ocurrió.

Un HTTP 200 puede representar COMPENSATED: revisar el cuerpo, no solo el código HTTP. Un segundo procesamiento devuelve 409 sin efectos nuevos. No reintentar automáticamente POST /orders: no tiene clave idempotente y podría crear otro pedido. Tras un error de red durante /process, consultar su estado antes de decidir el siguiente paso.

Errores: 400 entrada inválida, 404 recurso inexistente, 409 conflicto, 500 error técnico. El cuerpo incluye `code`, `message` y `details`. Para un DELETE 204 no intentar leer JSON. Renderizar texto recibido con textContent, evitando insertar contenido del usuario como HTML.

## 4. Demostración mínima y pruebas

- [ ] Pago aprobado: pedido CONFIRMED, proceso COMPLETED, stock disminuido exactamente una vez.
- [ ] Pago rechazado: pedido CANCELLED, proceso COMPENSATED, stock restituido, pago REJECTED.
- [ ] Stock insuficiente: pedido cancelado, inventario intacto y sin pago.
- [ ] Doble procesamiento: 409 y sin doble reserva/cobro.
- [ ] Mostrar errores del backend en pantalla y verificar recarga de catálogo.

Antes de entregar:

```sh
docker compose --profile test run --rm integration-tests
```

Esperado en la versión revisada: 19 pruebas unitarias y 10 de integración sin fallos. Estas pruebas cubren el backend; validar también las pantallas manualmente. No ejecutar dos reconstrucciones simultáneas sobre la misma carpeta: pueden competir al recrear el contenedor.

## 5. Colaboración y entrega

Con acceso de escritura al repositorio:

```sh
git switch -c feature/interfaz-web
# implementar y probar
git add src/main/webapp README.md PENDIENTES.md
git commit -m "Añadir interfaz web para productos y pedidos"
git push -u origin feature/interfaz-web
```

Abrir un pull request hacia main para revisión antes de integrar. Si no se tiene acceso de escritura, solicitarlo al propietario o trabajar desde un fork cuando sea posible. Evitar subir target/, .env, archivos de IDE o datos locales.

- [ ] Actualizar README con capturas y pasos para usar la interfaz.
- [ ] Añadir/verificar los diagramas de arquitectura, secuencia SAGA (éxito y compensación) y despliegue; no afirmar que están entregados hasta incorporarlos.
- [ ] Revisar los requisitos exactos del enunciado del profesor antes de cerrar la entrega.

## 6. Límites conocidos del backend

No son requisitos nuevos para construir las pantallas: documentarlos y acordar si el taller los exige.

- Una caída abrupta de la JVM puede dejar RUNNING/COMPENSATING bloqueado. Se puede reintentar FAILED mediante POST /process, pero no hay recuperación automática tras reinicio.
- Pago simulado, sin proveedor externo ni reembolsos. No pedir datos de tarjeta reales.
- Sin autenticación/roles; pensado para demostración local, no publicación como tienda real.
- Esquema actualizado por Hibernate; no hay migraciones versionadas.
- Sin listado general de pedidos ni búsqueda/paginación. Añadirlos solo si se acuerdan como alcance adicional.

La lógica de negocio, transacciones y compensaciones debe permanecer en los servicios del backend. La interfaz consume la API y presenta su resultado.
