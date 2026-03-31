# 🎟️ Ticketing System – Backend Reactivo

## Contexto
La empresa de venta de entradas enfrenta problemas de escalabilidad y consistencia en su sistema actual. Durante picos de demanda, se producen sobreventas, duplicados y timeouts.

La solución implementada es un **backend reactivo y basado en eventos**, dividido en microservicios, que garantiza consistencia del inventario y tiempos de respuesta bajos bajo alta concurrencia.

---

## Objetivos
- Gestionar disponibilidad y procesamiento de compras en tiempo real.
- Procesar órdenes de forma asíncrona mediante colas de mensajes.
- Garantizar que no se vendan más entradas de las disponibles.
- Exponer una API reactiva con **Spring WebFlux**.
- Persistir datos en **DynamoDB** con *conditional writes*.
- Orquestar flujos con **SQS/Kafka** para resiliencia.

---

##  Arquitectura
Se sigue **Clean Architecture / Hexagonal**:

- **Domain**: núcleo del negocio (agregados `Order`, `Event`, objetos de valor, máquina de estados, excepciones).
- **Application**: casos de uso (`CreateOrderUseCase`, `InventoryService`).
- **Infrastructure**: adaptadores concretos (repositorios DynamoDB, publishers SQS, mappers, configuración).
- **Web**: API reactiva con WebFlux (`OrderHandler`, `EventHandler`).

Separación en **microservicios**:
- **Order Service**: gestiona ciclo de vida de órdenes.
- **Inventory Service**: gestiona disponibilidad y control de concurrencia.

Comunicación entre servicios vía **mensajería (SQS/Kafka)**.

---

## Estructura de Carpetas

### Order Service

 ```bash
order-service/
└── src/main/java/com/nequi/ticketing_service
├── application/usecase
├── domain/model/order
├── domain/valueobject
├── domain/exception
├── domain/port/in
├── domain/port/out
├── domain/statemachine
├── infrastructure/persistence/dynamo/entity
├── infrastructure/persistence/mapper
├── infrastructure/messaging
├── infrastructure/config
└── web/handler
```

### Inventory Service

 ```bash

inventory-service/
└── src/main/java/com/nequi/inventory_service
├── application/usecase
├── domain/model/event
├── domain/valueobject
├── domain/exception
├── domain/port/in
├── domain/port/out
├── infrastructure/persistence/dynamo/entity
├── infrastructure/persistence/mapper
├── infrastructure/messaging
├── infrastructure/config
```

---

## Endpoints Principales

### Order Service
- `POST /orders` → Crear orden (reserva inicial).
- `GET /orders/{id}` → Consultar estado de orden.

### Inventory Service
- `GET /events/{id}/availability` → Consultar disponibilidad en tiempo real.
- `POST /events/{id}/reserve` → Reservar entradas temporalmente.
- `POST /events/{id}/confirm` → Confirmar compra.
- `POST /events/{id}/release` → Liberar reserva expirada.

---

## Decisiones Técnicas
- **Spring WebFlux**: API reactiva, no bloqueante, ideal para alta concurrencia.
- **DynamoDB**: persistencia NoSQL con *conditional writes* para evitar sobreventa.
- **SQS/Kafka**: procesamiento asíncrono de órdenes, garantizando *at-least-once delivery*.
- **State Machine**: transiciones de estado atómicas y auditables.
- **Error Handling Global**: `BusinessException` + `GlobalExceptionHandler` con `ErrorResponse` estandarizado (timestamp, errorCode, status, message, path).
- **Docker Compose**: levantar app + DynamoDB Local + LocalStack (SQS).

---

## Cómo levantar el proyecto
1. Clonar repositorio.
2. Ejecutar `docker-compose up` para levantar infraestructura (DynamoDB Local, LocalStack).
3. Levantar cada microservicio con:

   ```bash
   ./mvnw spring-boot:run
    ```


## Acceso a la API

- **Order Service** → [http://localhost:8080](http://localhost:8080)
- **Inventory Service** → [http://localhost:8081](http://localhost:8081)

---

## Observabilidad

- **Logs estructurados** con `errorCode` y `orderId`.
- **Métricas de concurrencia y errores** (`ORD-001`, `EVT-002`).
- **Auditoría** de transiciones de estado para trazabilidad completa.

---

## Catálogo de Códigos de Error

| Código   | Servicio   | Descripción                                | HTTP Status        |
|----------|------------|--------------------------------------------|--------------------|
| ORD-001  | Order      | Evento no aceptado en máquina de estados   | 409 Conflict       |
| ORD-002  | Order      | Orden no encontrada                        | 404 Not Found      |
| ORD-003  | Order      | Fallo en el pago                           | 402 Payment Required|
| ORD-004  | Order      | Cancelación inválida                       | 400 Bad Request    |
| EVT-001  | Inventory  | Evento no encontrado                       | 404 Not Found      |
| EVT-002  | Inventory  | Asientos no disponibles                    | 409 Conflict       |
| EVT-003  | Inventory  | Reserva expirada                           | 410 Gone           |
| EVT-004  | Inventory  | Error de concurrencia (optimistic locking) | 409 Conflict       |

---

## Estado Final

El sistema está completo y cumple con todos los requisitos:

- ✔️ Arquitectura hexagonal con separación clara de capas.
- ✔️ Microservicios independientes para órdenes e inventario.
- ✔️ API reactiva con WebFlux.
- ✔️ Persistencia en DynamoDB con control de concurrencia.
- ✔️ Procesamiento asíncrono con SQS/Kafka.
- ✔️ Manejo global de errores estandarizado.
- ✔️ Infraestructura lista con Docker Compose.
- ✔️ Observabilidad y auditoría implementadas.  
