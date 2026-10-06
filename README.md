# Order System — Taller de Arquitectura de Software

## Para continuar el trabajo

Consulta [PENDIENTES.md](PENDIENTES.md): guía para implementar la interfaz, contrato de API, criterios de aceptación y flujo de colaboración.


Backend de comercio electrónico simplificado (productos, inventario, pedidos y pagos) construido como un
**monolito modular Jakarta EE 11** desplegado en **WildFly 41**, con **JPA + JTA + CQRS + SAGA orquestada**
y **PostgreSQL**, todo levantado con **un solo comando de Docker Compose**.

---

## Índice

1. [Objetivo](#1-objetivo)
2. [Arquitectura](#2-arquitectura)
3. [Tecnologías y versiones](#3-tecnologías-y-versiones)
4. [Requisitos](#4-requisitos)
5. [Cómo ejecutar](#5-cómo-ejecutar)
6. [Cómo detener](#6-cómo-detener)
7. [Cómo limpiar completamente el entorno](#7-cómo-limpiar-completamente-el-entorno)
8. [Endpoints](#8-endpoints)
9. [Ejemplos curl](#9-ejemplos-curl)
10. [Demostración: Caso A — happy path](#10-caso-a--happy-path)
11. [Demostración: Caso B — pago rechazado](#11-caso-b--pago-rechazado)
12. [Demostración: Caso C — stock insuficiente](#12-caso-c--stock-insuficiente)
13. [Demostración: Caso D — idempotencia](#13-caso-d--idempotencia)
14. [Revisar la base de datos](#14-revisar-la-base-de-datos)
15. [Decisiones arquitectónicas](#15-decisiones-arquitectónicas)
16. [Uso de JPA](#16-uso-de-jpa)
17. [Uso de JTA](#17-uso-de-jta)
18. [Uso de SAGA](#18-uso-de-saga)
19. [Uso de CQRS](#19-uso-de-cqrs)
20. [Pruebas](#20-pruebas)
21. [Manejo de errores](#21-manejo-de-errores)
22. [Archivos de configuración](#22-archivos-de-configuración)
23. [Limitaciones conocidas](#23-limitaciones-conocidas)

---

## 1. Objetivo

Cuando un cliente compra, el sistema:

1. Crea el pedido en estado `PENDING` (`POST /api/orders`).
2. Al procesarlo (`POST /api/orders/{id}/process`) ejecuta una SAGA que:
   1. reserva el inventario,
   2. procesa el pago (simulado),
   3. si el pago es aprobado, confirma el pedido;
   4. si algo falla, ejecuta las compensaciones (liberar inventario, cancelar pedido).

Se protege contra *overselling* (reservas concurrentes) y contra efectos duplicados (idempotencia).

## 2. Arquitectura

Monolito modular, *package-by-feature*: un único WAR, una única base de datos, tres dominios separados
lógicamente más el orquestador de la SAGA.

```
com.taller.ordersystem
├── inventory            Productos, stock y reservas
│   ├── api              ProductResource (+ DTOs de entrada)
│   ├── application
│   │   ├── command      ProductCommandService, InventoryCommandService (reserve / release)
│   │   └── query        ProductQueryService, InventoryQueryService (+ vistas)
│   ├── domain           Product, InventoryReservation, ReservationItem, excepciones
│   └── persistence      ProductRepository, InventoryReservationRepository
├── order                Pedidos
│   ├── api              OrderResource (crear, consultar, procesar)
│   ├── application
│   │   ├── command      OrderCommandService (create / confirm / cancel)
│   │   └── query        OrderQueryService (+ vistas)
│   ├── domain           Order, OrderItem, OrderStatus, OrderNotFoundException
│   └── persistence      OrderRepository
├── payment              Pagos (simulados)
│   ├── application      PaymentCommandService, PaymentQueryService, SimulatedPaymentGateway
│   ├── domain           Payment, PaymentStatus, PaymentSimulationMode, PaymentRejectedException
│   └── persistence      PaymentRepository
├── saga                 OrderSagaOrchestrator, OrderProcess (estado de la SAGA), OrderProcessService
└── shared
    ├── exception        BusinessException, ErrorType, InvalidRequestException, InvalidStateException
    ├── api              RestApplication (/api), ExceptionMappers, ErrorResponse, RequestValidator
    └── health           GET /api/health (usado por el healthcheck de Docker)
```

Ajustes respecto a la estructura sugerida (todos menores):

* `Product` vive en `inventory`: el stock es responsabilidad del inventario.
* `shared/health`: endpoint de salud que necesita el healthcheck de Docker Compose.
* Los endpoints `/orders/{id}/process` están en `OrderResource` (mismo recurso REST `/orders`); el
  recurso solo delega en `OrderSagaOrchestrator`.

Comunicación entre módulos: **solo a través de servicios de aplicación** (inyección CDI), nunca a través
de entidades de otro módulo. No hay relaciones JPA entre dominios:

| Entidad              | Referencia a otro dominio |
|----------------------|---------------------------|
| `OrderItem`          | `productId` (Long)        |
| `InventoryReservation` | `orderId` (Long)        |
| `ReservationItem`    | `productId` (Long)        |
| `Payment`            | `orderId` (Long)          |
| `OrderProcess`       | `orderId` (Long)          |

Las únicas asociaciones JPA son **dentro** de un mismo agregado (`Order → OrderItem`,
`InventoryReservation → ReservationItem`), unidireccionales, con la FK `order_id` / `reservation_id`.

```
            HTTP
             │
   ┌─────────▼──────────┐
   │  ProductResource   │   OrderResource ─────────────┐
   └───┬───────────┬────┘         │                    │ POST /orders/{id}/process
       │command    │query         │                    ▼
       ▼           ▼              ▼           ┌─────────────────────┐   (sin transacción)
 ProductCommand  ProductQuery  OrderCommand   │ OrderSagaOrchestrator│
 Service         Service       Service        └──┬──────┬──────┬────┘
                                                 │      │      │  cada llamada = 1 transacción JTA
                                                 ▼      ▼      ▼  (REQUIRES_NEW)
                                     InventoryCommand  PaymentCommand  OrderCommand / OrderProcess
                                     Service           Service         Service
                                          │                │                │
                                          └──────── JPA (EntityManager) ────┘
                                                         │
                                         WildFly datasource OrderSystemDS (JTA)
                                                         │
                                                    PostgreSQL
```

## 3. Tecnologías y versiones

| Tecnología | Versión | Notas |
|---|---|---|
| Java | **21** (Eclipse Temurin) | LTS. WildFly 41 es compatible con Jakarta EE 11 sobre Java SE 17 y 21 |
| Jakarta EE | **11** (`jakarta.jakartaee-api:11.0.0`, `provided`) | REST, CDI, JPA 3.2, JTA, Validation, JSON-B |
| Servidor | **WildFly 41.0.1.Final** (`quay.io/wildfly/wildfly:41.0.1.Final-jdk21`) | Ver verificación abajo |
| Base de datos | **PostgreSQL 17** (`postgres:17-alpine`) | Única base de datos |
| Driver JDBC | `org.postgresql:postgresql:42.7.7` | Instalado como **módulo de WildFly**, no en el WAR |
| Build | Maven 3.9 | Empaquetado `war` |
| Contenedores | Docker + Docker Compose | Build multi-stage |
| Tests | JUnit 5, Mockito (unitarios); `java.net.http` + JSON-P (integración) | |

**Verificación de compatibilidad WildFly ↔ Jakarta EE 11.** La versión no se asumió:

* WildFly 39 estándar todavía era Jakarta EE 10; EE 11 solo estaba en *WildFly Preview*.
* **WildFly 40** fue la primera versión que llevó EE 11 a la distribución estándar.
* Las notas de **WildFly 41** dicen que es compatible con EE 11 *Platform*, *Web Profile* y
  *Core Profile* cuando corre sobre Java SE 17 y 21
  ([anuncio de WildFly 41](https://www.wildfly.org/news/2026/07/16/WildFly-41-is-released/),
  [anuncio de WildFly 40](https://www.wildfly.org/news/2026/05/21/WildFly-40-is-released/)).

Por eso se usa la imagen `-jdk21` y no la `latest`, que apunta a JDK 25.

**No se usa**: Spring, Quarkus, Micronaut, Kafka, RabbitMQ, Kubernetes, Event Sourcing ni microservicios.

## 4. Requisitos

Solo se necesita:

* **Git** (o descargar el ZIP).
* **Docker Desktop** (o Docker Engine + plugin Compose v2).

**No** hace falta instalar Java, Maven, WildFly ni PostgreSQL: todo se construye dentro de Docker.
Puertos libres en el host: `8080` (API) y `5433` (PostgreSQL, para inspección). Se pueden cambiar con
un archivo `.env` (ver `.env.example`).

## 5. Cómo ejecutar

```bash
docker compose up --build
```

Ese comando:

1. Levanta PostgreSQL y espera a que su *healthcheck* (`pg_isready`) sea correcto.
2. Compila el proyecto con Maven dentro de un contenedor (stage 1), ejecuta los tests unitarios y genera el WAR.
3. Prepara WildFly 41 (stage 2): instala el driver PostgreSQL como módulo y crea el datasource JTA
   `java:jboss/datasources/OrderSystemDS` con `jboss-cli`.
4. Despliega el WAR y conecta WildFly con PostgreSQL (Hibernate crea las tablas al desplegar).
5. Deja la API en **http://localhost:8080/api**.

La primera vez tarda unos minutos (descarga de imágenes y dependencias Maven). Está lista cuando el log
muestra `Deployed "order-system.war"` / `WildFly ... started`, o cuando responde:

```bash
curl http://localhost:8080/api/health
```

Resultado: `{"database":"UP","status":"UP"}`

Para ejecutarlo en segundo plano: `docker compose up --build -d` y `docker compose ps` (el servicio `app`
aparece como `healthy` cuando la API está lista).

## 6. Cómo detener

```bash
docker compose down
```

Los datos de PostgreSQL se conservan en el volumen `pgdata`.

## 7. Cómo limpiar completamente el entorno

Borra los contenedores, la red, los volúmenes (datos de PostgreSQL y caché de Maven) y la imagen construida:

```bash
docker compose --profile test down --volumes --rmi local
```

## 8. Endpoints

Base URL: `http://localhost:8080/api`

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| POST | `/products` | Crear producto `{name, price, availableStock}` | 201 + producto |
| GET | `/products` | Listar productos | 200 |
| GET | `/products/{id}` | Consultar producto | 200 / 404 |
| PUT | `/products/{id}/price` | Cambiar precio `{price}` | 200 / 400 / 404 |
| PUT | `/products/{id}/stock` | Fijar stock disponible `{availableStock}` | 200 / 400 / 404 |
| DELETE | `/products/{id}` | Borrar producto (409 si tiene reservas) | 204 / 404 / 409 |
| GET | `/products/{id}/inventory` | Stock disponible y unidades reservadas | 200 / 404 |
| POST | `/orders` | Crear pedido `PENDING` `{customerId, items:[{productId, quantity}]}` | 201 / 400 / 404 |
| GET | `/orders/{id}` | Consultar pedido (líneas, `unitPrice`, total) | 200 / 404 |
| POST | `/orders/{id}/process[?payment=APPROVE\|REJECT]` | **Ejecutar la SAGA** | 200 / 404 / 409 / 500 |
| GET | `/orders/{id}/process` | Estado de la SAGA + pedido + reserva + pago | 200 / 404 |
| GET | `/health` | Salud de la app y de la BD | 200 / 503 |

**Simulación del pago (determinista, sin aleatoriedad):**

| Llamada | Resultado del pago |
|---|---|
| `POST /api/orders/{id}/process` | `APPROVED` (por defecto) |
| `POST /api/orders/{id}/process?payment=APPROVE` | `APPROVED` |
| `POST /api/orders/{id}/process?payment=REJECT` | `REJECTED` |

(También se aceptan `APPROVED` / `REJECTED`; cualquier otro valor → 400.)

Respuesta de `/process` (también de `GET /process`):

```json
{
  "createdAt": "2026-10-06T16:00:00.000Z",
  "currentStep": "DONE",
  "lastError": null,
  "orderId": 1,
  "orderStatus": "CONFIRMED",
  "orderTotal": 20.00,
  "paymentAmount": 20.00,
  "paymentStatus": "APPROVED",
  "processStatus": "COMPLETED",
  "reservationStatus": "RESERVED",
  "updatedAt": "2026-10-06T16:00:00.100Z"
}
```

(JSON-B ordena las propiedades alfabéticamente. Un paso que nunca se registró aparece como `null`: por ejemplo,
en el caso C, `paymentStatus` y `reservationStatus` son `null`.)

## 9. Ejemplos curl

Los ejemplos usan variables de shell (bash/zsh; en Windows usar Git Bash o WSL) para capturar los ids
generados, así funcionan aunque ya existan datos.

```bash
API=http://localhost:8080/api

# Crear producto
curl -s -X POST $API/products -H 'Content-Type: application/json' \
  -d '{"name":"Teclado","price":10.00,"availableStock":10}'

# Listar / consultar
curl -s $API/products
curl -s $API/products/1

# Cambiar precio y stock
curl -s -X PUT $API/products/1/price -H 'Content-Type: application/json' -d '{"price":12.50}'
curl -s -X PUT $API/products/1/stock -H 'Content-Type: application/json' -d '{"availableStock":20}'

# Inventario de un producto
curl -s $API/products/1/inventory

# Borrar producto (204; 409 si ya fue usado en reservas)
curl -s -i -X DELETE $API/products/1

# Crear pedido (queda PENDING, NO ejecuta la SAGA)
curl -s -X POST $API/orders -H 'Content-Type: application/json' \
  -d '{"customerId":"cliente-1","items":[{"productId":1,"quantity":2}]}'

# Procesar pedido (SAGA) y consultar su estado
curl -s -X POST $API/orders/1/process
curl -s $API/orders/1/process

# Errores de validación (400, JSON con detalles)
curl -s -X POST $API/products -H 'Content-Type: application/json' \
  -d '{"name":"","price":0,"availableStock":-1}'
```

Funciones auxiliares para los casos A–D. **Pegar primero este bloque en la terminal**:

```bash
API=http://localhost:8080/api
new_product() { curl -s -X POST $API/products -H 'Content-Type: application/json' \
  -d "{\"name\":\"$1\",\"price\":$2,\"availableStock\":$3}" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2; }
new_order() { curl -s -X POST $API/orders -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"cliente-demo\",\"items\":[{\"productId\":$1,\"quantity\":$2}]}" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2; }
stock() { curl -s $API/products/$1 | grep -o '"availableStock":[0-9]*'; }
```

## 10. Caso A — happy path

Stock inicial 10, compra 2, pago APPROVED.

```bash
P=$(new_product "Producto A" 10.00 10); O=$(new_order $P 2); echo "producto=$P pedido=$O"
curl -s $API/orders/$O                      # status PENDING, unitPrice 10.00
curl -s -X POST $API/orders/$O/process      # pago aprobado por defecto
stock $P                                    # "availableStock":8
```

Resultado esperado de `/process`:
`processStatus=COMPLETED`, `orderStatus=CONFIRMED`, `reservationStatus=RESERVED`, `paymentStatus=APPROVED`,
y `availableStock=8`.

**Precio histórico:** si se cambia el precio después de crear el pedido
(`curl -s -X PUT $API/products/$P/price -H 'Content-Type: application/json' -d '{"price":99.99}'`), el
pedido conserva `unitPrice=10.00` y el pago se hace por `20.00`.

## 11. Caso B — pago rechazado

Stock inicial 10, compra 2, pago REJECTED → compensación.

```bash
P=$(new_product "Producto B" 10.00 10); O=$(new_order $P 2); echo "producto=$P pedido=$O"
curl -s -X POST "$API/orders/$O/process?payment=REJECT"
stock $P                                    # "availableStock":10
```

Resultado esperado: `processStatus=COMPENSATED`, `orderStatus=CANCELLED`, `reservationStatus=RELEASED`,
`paymentStatus=REJECTED`, `lastError="Payment for order N was rejected"`, `availableStock=10`.

Durante la SAGA el stock pasó a 8 (reserva confirmada en su propia transacción) y la compensación lo
devolvió a 10. En el log de WildFly (`docker compose logs app`) se ve la secuencia:

```
Inventory reserved for order N
Simulated charge of 20.00 for order N -> REJECTED
Business failure in saga for order N: Payment for order N was rejected
Inventory released for order N
Order N cancelled
Saga compensated for order N
```

## 12. Caso C — stock insuficiente

Stock inicial 1, compra 2.

```bash
P=$(new_product "Producto C" 10.00 1); O=$(new_order $P 2); echo "producto=$P pedido=$O"
curl -s -X POST $API/orders/$O/process
stock $P                                    # "availableStock":1
```

Resultado esperado: `processStatus=COMPENSATED`, `orderStatus=CANCELLED`,
`lastError="Insufficient stock for product P: requested 2, available 1"`, `paymentStatus=null` (no se
intentó cobrar) y `reservationStatus=null` (la transacción de reserva hizo rollback), `availableStock=1`.

## 13. Caso D — idempotencia

Procesar dos veces el mismo pedido.

```bash
P=$(new_product "Producto D" 10.00 10); O=$(new_order $P 2); echo "producto=$P pedido=$O"
curl -s -X POST $API/orders/$O/process                 # 200, COMPLETED
curl -s -i -X POST $API/orders/$O/process              # 409 ORDER_ALREADY_PROCESSED
stock $P                                               # sigue en "availableStock":8
curl -s $API/orders/$O/process                         # un solo pago APPROVED
```

La segunda llamada responde **409** con:

```json
{"code":"ORDER_ALREADY_PROCESSED","details":[],"error":"Conflict",
 "message":"Order N was already processed: process status is COMPLETED, order status is CONFIRMED. No action was taken",
 "path":"/orders/N/process","status":409,"timestamp":"..."}
```

No reserva, no cobra ni cambia el stock. En la base de datos sigue habiendo una sola fila en
`payments`, `inventory_reservations` y `order_processes` para ese pedido (ver sección 14).

**Concurrencia (bonus).** Dos pedidos simultáneos por la última unidad: solo uno se confirma.

```bash
P=$(new_product "Ultima unidad" 10.00 1); O1=$(new_order $P 1); O2=$(new_order $P 1)
curl -s -X POST $API/orders/$O1/process & curl -s -X POST $API/orders/$O2/process & wait
stock $P                                                # "availableStock":0  (nunca negativo)
```

Un pedido termina `COMPLETED` y el otro `COMPENSATED` (stock insuficiente).

## 14. Revisar la base de datos

Con `psql` dentro del contenedor (no requiere instalar nada):

```bash
docker compose exec database psql -U ordersystem -d ordersystem
```

Consultas útiles:

```sql
\dt
SELECT id, name, price, available_stock FROM products ORDER BY id;
SELECT id, customer_id, status, created_at FROM orders ORDER BY id;
SELECT order_id, product_id, quantity, unit_price FROM order_items ORDER BY id;
SELECT id, order_id, status FROM inventory_reservations ORDER BY id;
SELECT reservation_id, product_id, quantity FROM reservation_items ORDER BY id;
SELECT order_id, amount, status FROM payments ORDER BY id;
SELECT order_id, status, current_step, last_error FROM order_processes ORDER BY id;
```

También se puede conectar un cliente gráfico a `localhost:5433` (base `ordersystem`, usuario `ordersystem`,
contraseña `ordersystem`; valores de demostración configurables en `.env`).

## 15. Decisiones arquitectónicas

| # | Decisión | Motivo |
|---|---|---|
| A | **Monolito modular** (1 WAR, módulos por paquete) | Separación de dominios sin la complejidad operativa de microservicios |
| B | **SAGA orquestada** (`OrderSagaOrchestrator`) | El flujo es pequeño y secuencial; un orquestador hace explícitos los pasos y las compensaciones |
| C | **JTA por paso**, sin transacción global | JTA = atomicidad local de cada paso; SAGA = consistencia global con compensaciones |
| D | **CQRS lógico** | Servicios de comando y de consulta separados sobre la misma PostgreSQL |
| E | **JPA administrado por WildFly** | El contenedor gestiona el EntityManager, el proveedor y las transacciones |
| F | **PostgreSQL** como única BD | Un solo almacén relacional para todos los módulos |
| G | **Docker Compose** | Un solo comando reproduce todo el entorno |

Decisiones de detalle:

* **Pago simulado y determinista** mediante `?payment=APPROVE|REJECT` (`SimulatedPaymentGateway`).
  No hay proveedor real ni aleatoriedad, así las pruebas son reproducibles.
* **Bloqueo pesimista** (`PESSIMISTIC_WRITE`) para el stock: es simple y correcto frente a reservas
  concurrentes. Se prefirió frente al bloqueo optimista porque no obliga a reintentar.
* **Fallo técnico después de un pago APPROVED** (por ejemplo, falla `confirmOrder`): el enunciado no define
  refund, así que **no se inventa una regla financiera**. El proceso queda `FAILED` (HTTP 500) y se reintenta
  con el mismo `POST /process`. El reintento no vuelve a reservar ni a cobrar, y confirma el pedido.
  En producción habría que definir con negocio una compensación financiera (refund) o una cola de reintentos.
* **No se borra un producto que tiene reservas** (409 `PRODUCT_IN_USE`): se mantiene la trazabilidad y la
  posibilidad de devolver stock.
* **`PUT /products/{id}/stock` fija el stock disponible** (ajuste administrativo de inventario); no modifica
  las reservas existentes.
* **Una reserva de un pedido confirmado queda `RESERVED`**: representa stock consumido por ese pedido
  (coincide con el caso A esperado). `GET /products/{id}/inventory` muestra esas unidades en `reservedQuantity`.

## 16. Uso de JPA

* Entidades `@Entity` con `@Id` y `@GeneratedValue(strategy = IDENTITY)`, enums como `@Enumerated(STRING)`.
* `persistence.xml` con una unidad `JTA` sobre el datasource de WildFly `java:jboss/datasources/OrderSystemDS`.
  **No se configura Hibernate a mano**: WildFly aporta el proveedor (Hibernate ORM 7 / JPA 3.2), crea el
  `EntityManagerFactory` y lo une a las transacciones JTA.
* `EntityManager` inyectado con `@PersistenceContext` **solo en los repositorios** (`*Repository`), nunca en los
  recursos REST.
* El esquema lo crea o actualiza Hibernate al desplegar (`hibernate.hbm2ddl.auto=update`). Así un redespliegue
  sobre el volumen persistente conserva los datos. Para producción se recomendaría una herramienta de migraciones.
* Las consultas de lectura usan `JOIN FETCH` para devolver DTOs completos sin depender de *lazy loading*
  fuera de transacción.
* Bloqueos: `em.find(Product.class, id, LockModeType.PESSIMISTIC_WRITE)`, que en PostgreSQL es `SELECT … FOR UPDATE`.
* Restricciones `UNIQUE(order_id)` en `inventory_reservations`, `payments` y `order_processes`: una defensa
  de idempotencia a nivel de base de datos.

## 17. Uso de JTA

Principio: **JTA proporciona atomicidad dentro de cada paso; la SAGA, la consistencia del proceso completo.**

* `OrderSagaOrchestrator` usa `@Transactional(NOT_SUPPORTED)` para suspender cualquier transacción
  del llamador. Los pasos se ejecutan sin una transacción global que abarque la SAGA.
* Cada paso es un método `@jakarta.transaction.Transactional(TxType.REQUIRES_NEW)` en **otro** bean CDI. Así
  el interceptor JTA del contenedor siempre se aplica: la llamada pasa por el proxy CDI y no hay
  *self-invocation*.

| Bean CDI | Método | Transacción |
|---|---|---|
| `OrderCommandService` | `create` | `REQUIRED` (operación normal, fuera de la SAGA) |
| `InventoryCommandService` | `reserve` (paso), `release` (compensación) | `REQUIRES_NEW` |
| `PaymentCommandService` | `charge` | `REQUIRES_NEW`, `dontRollbackOn = PaymentRejectedException` |
| `OrderCommandService` | `confirm` (paso), `cancel` (compensación) | `REQUIRES_NEW` |
| `OrderProcessService` | `begin`, `moveTo`, `startCompensation`, `markCompleted`, `markCompensated`, `markFailed` | `REQUIRES_NEW` |
| `ProductCommandService` | `create`, `changePrice`, `changeStock`, `delete` | `REQUIRED` |

`REQUIRES_NEW` deja explícito que cada paso hace commit por sí mismo, aunque en el futuro alguien llame al
orquestador desde un contexto transaccional.

**Rollback de las excepciones de negocio:**

* `InsufficientStockException` / `ProductNotFoundException` dentro de `reserve` → **rollback** de toda la
  reserva, incluidas las líneas ya descontadas de un pedido con varios productos. No queda una reserva parcial.
* `PaymentRejectedException` → **no** hace rollback (`dontRollbackOn`): el rechazo es un resultado de negocio
  y queda registrado como `payment.status = REJECTED`. La excepción solo avisa al orquestador para que compense.
* `OrderAlreadyProcessedException` en `begin` → rollback (no se crea nada).
* Errores técnicos (`RuntimeException` no de negocio) → rollback del paso, y el proceso se marca `FAILED` en otra
  transacción independiente.

## 18. Uso de SAGA

`OrderSagaOrchestrator.process(orderId, paymentMode)`:

1. Valida que el pedido exista → 404 `ORDER_NOT_FOUND`.
2. `OrderProcessService.begin`: valida que esté `PENDING` y registra el `OrderProcess` (`RUNNING`), o reanuda uno
   `FAILED`. Si ya existe un proceso → 409 `ORDER_ALREADY_PROCESSED`.
3. `RESERVE_INVENTORY` → `InventoryCommandService.reserve` y se apila la compensación `RELEASE_INVENTORY`.
4. `PROCESS_PAYMENT` → `PaymentCommandService.charge`.
5. `CONFIRM_ORDER` → `OrderCommandService.confirm`.
6. `OrderProcess` → `COMPLETED`.

Compensaciones: el orquestador mantiene una **pila** (`Deque`) que se llena a medida que los pasos tienen éxito
(`CANCEL_ORDER` al inicio, `RELEASE_INVENTORY` después de reservar). Ante un error de negocio se desapila, así
que se ejecutan en **orden inverso** (LIFO):

```
Éxito:          PENDING → RESERVE_INVENTORY → PROCESS_PAYMENT(APPROVED) → CONFIRM_ORDER → CONFIRMED   [COMPLETED]
Stock:          PENDING → RESERVE_INVENTORY ✗ → (COMPENSATING) CANCEL_ORDER → CANCELLED              [COMPENSATED]  sin pago
Pago rechazado: PENDING → RESERVE_INVENTORY → PROCESS_PAYMENT(REJECTED) ✗ → (COMPENSATING)
                RELEASE_INVENTORY → CANCEL_ORDER → CANCELLED                                         [COMPENSATED]
Fallo técnico:  cualquier paso ✗ técnico → FAILED (500) → reintento con POST /process
```

La tabla `order_processes` registra `status`, `current_step`, `last_error`, `created_at` y `updated_at`.

**Idempotencia (`processOrder`, `confirmOrder`, `cancelOrder`, `releaseInventory`):**

| Operación | Garantía |
|---|---|
| `process` | Un `OrderProcess` por pedido (`UNIQUE(order_id)` + bloqueo de la fila). Proceso terminado o en curso → 409 sin efectos. Dos llamadas simultáneas: se bloquea primero la fila del pedido; la segunda espera y responde 409 al encontrar el proceso existente |
| `reserve` | Si ya existe la reserva del pedido, se devuelve sin descontar stock otra vez |
| `charge` | Si ya existe el pago del pedido, se devuelve su resultado sin volver a cobrar |
| `confirm` | Un pedido `CONFIRMED` no cambia (no-op); uno `CANCELLED` → 409 |
| `cancel` | Un pedido `CANCELLED` no cambia (no-op); uno `CONFIRMED` → 409 |
| `release` | Si la reserva no existe o ya está `RELEASED`, no se devuelve stock (no-op). La fila se bloquea |

Como todos los pasos son idempotentes, un proceso `FAILED` se puede reanudar repitiendo los pasos. Si el fallo
ocurrió durante una compensación, la reanudación solo ejecuta compensaciones. La dirección de compensación se guarda en la misma transacción que `COMPENSATING`, antes de ejecutar el primer paso.

**Concurrencia de inventario (sin overselling):** `reserve` bloquea cada producto con `PESSIMISTIC_WRITE`
(`SELECT … FOR UPDATE`) antes de leer y descontar el stock. Si dos reservas compiten por el último producto, la
segunda espera al commit de la primera, lee el stock actualizado (0) y falla con `InsufficientStockException`.
Los cambios de precio y stock también bloquean el producto para evitar que una actualización sobrescriba una reserva concurrente.
Los productos se bloquean siempre **ordenados por id**, lo que evita *deadlocks* entre pedidos con varios productos.

## 19. Uso de CQRS

Separación **lógica** de comandos y consultas sobre **la misma** base de datos:

| Lado comando (modifica estado, transaccional) | Lado consulta (solo lectura, devuelve DTOs) |
|---|---|
| `ProductCommandService` | `ProductQueryService` → `ProductView` |
| `InventoryCommandService` | `InventoryQueryService` → `ProductInventoryView`, `ReservationView` |
| `OrderCommandService` | `OrderQueryService` → `OrderView`, `OrderItemView` |
| `PaymentCommandService` | `PaymentQueryService` → `PaymentView` |
| `OrderProcessService` | `OrderProcessQueryService` → `OrderProcessView` (compone las consultas de los módulos) |

Las consultas nunca devuelven entidades JPA a la capa REST: devuelven `record`s inmutables.

## 20. Pruebas

Pocas pruebas, pero centradas en la arquitectura:

**Unitarias** (sin contenedor ni BD; se ejecutan en `mvn package` y dentro de `docker compose up --build`):

| Prueba | Qué verifica |
|---|---|
| `OrderSagaOrchestratorTest` | Happy path; pago rechazado → `release` y luego `cancel` (orden inverso); stock insuficiente → sin pago; pedido ya procesado → sin efectos; fallo técnico después del pago → `FAILED` sin compensar; reanudación de compensaciones |
| `InventoryDomainTest` | El stock nunca queda negativo; validaciones; `release` idempotente |
| `OrderTest` | Total con `unitPrice` histórico; `confirm`/`cancel` idempotentes y transiciones inválidas |
| `OrderProcessTest` | Reanudación de un proceso `FAILED` en la fase correcta |
| `OrderProcessServiceTest` | Inicio de la SAGA: proceso existente → 409; pedido ya no `PENDING` → 409; un fallo de BD no se disfraza de "pedido duplicado" |
| `JsonContractTest` | Contrato JSON de los DTOs `record` con JSON-B (Yasson) |

**Integración HTTP** (`OrderSagaIT`, contra WildFly + PostgreSQL reales): casos **A, B, C, D**, procesamiento
concurrente del mismo pedido, dos pedidos concurrentes por la última unidad, rollback JTA de una reserva con varios
productos, cambios de precio concurrentes que no pisan el stock reservado, CRUD de productos y validaciones.

Ejecutarlas sin Java local (con la app levantada):

```bash
docker compose --profile test run --rm integration-tests
```

Con Java 21 y Maven locales (con la app levantada en `localhost:8080`):

```bash
mvn verify -Pit
```

Solo las unitarias:

```bash
mvn clean package
```

## 21. Manejo de errores

Todas las respuestas de error tienen el mismo formato JSON:

```json
{
  "code": "INSUFFICIENT_STOCK",
  "details": [],
  "error": "Conflict",
  "message": "…",
  "path": "/orders/7/process",
  "status": 409,
  "timestamp": "2026-10-06T16:00:00Z"
}
```

| HTTP | `code` | Origen |
|---|---|---|
| 400 | `INVALID_REQUEST` | Jakarta Validation (`details` lista los campos) o reglas de entrada: pedido sin productos, producto repetido, `payment` inválido |
| 400 | `MALFORMED_JSON` | Cuerpo JSON mal formado |
| 404 | `PRODUCT_NOT_FOUND`, `ORDER_NOT_FOUND`, `ORDER_PROCESS_NOT_FOUND` | Recurso inexistente |
| 409 | `ORDER_ALREADY_PROCESSED`, `INVALID_STATE`, `PRODUCT_IN_USE`, `INSUFFICIENT_STOCK` | Conflicto de estado |
| 500 | `TECHNICAL_ERROR` | Fallo técnico inesperado (en la SAGA: proceso `FAILED`, reintentable) |

Los errores de negocio extienden `BusinessException` (con un `ErrorType`: el dominio no conoce HTTP) y los traduce
`BusinessExceptionMapper`. `UnexpectedExceptionMapper` cubre los errores técnicos y respeta los códigos propios de
Jakarta REST (404 de ruta, 405, 415…).

Los fallos de negocio **dentro de la SAGA** (stock insuficiente, pago rechazado) no se devuelven como error HTTP:
la SAGA los maneja compensando, y `POST /process` responde **200** con el resultado (`processStatus=COMPENSATED`
y el motivo en `lastError`). La petición se procesó correctamente y el resultado de negocio está en el cuerpo.

## 22. Archivos de configuración

| Archivo | ¿Necesario? | Propósito |
|---|---|---|
| `pom.xml` | Sí | WAR Jakarta EE 11; APIs `provided` (no se empaquetan); copia el driver JDBC para Docker; perfil `it` |
| `src/main/resources/META-INF/persistence.xml` | Sí | Unidad JTA sobre el datasource de WildFly |
| `src/main/webapp/WEB-INF/beans.xml` | Opcional | En CDI 4.x sin `beans.xml` el descubrimiento ya es `annotated`; se incluye para dejarlo explícito |
| `src/main/webapp/WEB-INF/jboss-web.xml` | Sí | `context-root` `/` para que la API quede en `/api/...` |
| `web.xml` | **No** | `@ApplicationPath` activa Jakarta REST; no hay otra configuración web |
| `docker/wildfly/configure-datasource.cli` | Sí | Driver, datasource `OrderSystemDS` con `${env.*}`, datasource por defecto, liberar `/` |
| `docker/wildfly/modules/org/postgresql/main/module.xml` | Sí | Módulo JBoss del driver PostgreSQL |
| `Dockerfile` | Sí | Multi-stage: Maven/JDK 21 → WildFly 41 |
| `docker-compose.yml` | Sí | `database` (healthcheck + volumen), `app` (depende de BD *healthy*, healthcheck HTTP), `integration-tests` (perfil `test`) |
| `.env.example` | Opcional | Valores configurables. Sin `.env` se usan los mismos valores de demostración |
| `.dockerignore`, `.gitignore` | Sí | Excluyen `target/`, `.env`, archivos de IDE |

Las credenciales no están en el código Java: el datasource las lee de variables de entorno (`DB_USER`,
`DB_PASSWORD`…) que define `docker-compose.yml` (o `.env`).

## 23. Limitaciones conocidas

* Si la JVM se cae **en mitad** de una SAGA, el proceso queda `RUNNING`/`COMPENSATING`, y `POST /process`
  responde 409 (en curso). Haría falta un mecanismo de recuperación (por ejemplo, un timer que reanude los
  procesos atascados), fuera del alcance del taller.
* No hay refund: ver la decisión sobre el fallo técnico después del pago en la sección 15.
* El esquema lo gestiona `hbm2ddl=update`; en producción se usarían migraciones versionadas.
* El datasource `ExampleDS` (H2 en memoria) que trae WildFly por defecto sigue definido, pero **no se usa**:
  la aplicación y el datasource por defecto de Jakarta EE apuntan a PostgreSQL.
