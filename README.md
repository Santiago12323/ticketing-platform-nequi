#  Reactive Ticketing Ecosystem: High-Concurrency & Event-Driven

## Visión General
Este ecosistema de microservicios es una solución de **misión crítica** diseñada para la venta masiva de entradas. La arquitectura resuelve los problemas de consistencia y latencia ante picos de demanda masivos, eliminando bloqueos de hilos y garantizando integridad transaccional mediante un modelo reactivo de punta a punta.

---

##  Decisiones de Arquitectura y Patrones de Diseño

### 1. Garantía de Inventario: ¿Cómo evitamos la sobreventa?
Para resolver el problema de "dos personas comprando el mismo asiento", implementamos una estrategia de **Bloqueo Optimista (Optimistic Locking)**:
* **Escrituras Condicionales en DynamoDB:** Cada intento de reserva incluye una `ConditionExpression`. El sistema solo permite el cambio a `RESERVED` si el estado actual es estrictamente `AVAILABLE`. Si dos peticiones llegan al mismo milisegundo, la base de datos rechaza la segunda a nivel atómico.
* **Máquina de Estados Finita:** Los tickets y órdenes siguen un flujo de estados estricto. Un ticket en estado `SOLD` es final e inmutable, protegido por la lógica del dominio, evitando regresiones de estado.



### 2. Patrón Saga: Atomicidad Distribuida
Gestionamos transacciones que involucran múltiples microservicios mediante una **Saga Coreografiada**:
* **Consistencia "Todo o Nada":** Al procesar una orden de múltiples tickets, el **Inventory Service** valida la disponibilidad del conjunto completo. Si un solo ticket falla, se dispara un evento de compensación que revierte cualquier reserva previa y marca la orden como `FAILED`.
* **Idempotencia:** Cada mensaje en las colas lleva un `OrderId` como clave de idempotencia. Si un consumidor procesa dos veces el mismo mensaje por un reintento de red, el sistema detecta el duplicado y evita duplicar la lógica de negocio.



### 3. Resiliencia y Manejo de Backpressure
* **Ingesta por Buffering:** El *Order Service* funciona como un amortiguador. Recibe la petición, valida el esquema y la moneda (**USD/COP**), y la envía a un stream de **Redis/SQS**. Respondemos con un `202 Accepted` de inmediato, liberando los hilos del servidor para seguir recibiendo tráfico (Backpressure control).
* **Aislamiento de Fallos:** Si el servicio de inventario se ralentiza, los mensajes se acumulan de forma segura en **SQS**, evitando que el *Order Service* sufra caídas por agotamiento de recursos.

### 4. Eficiencia con Redis y SNS Fan-out
* **Caché de Respuesta Rápida:** Implementamos **Redis** para servir la disponibilidad de eventos en milisegundos, reduciendo drásticamente la latencia y la carga en DynamoDB.
* **Patrón Fan-out para TTL:** Al expirar el tiempo de reserva (10 min), un tópico de **SNS** notifica simultáneamente a ambos microservicios. El *Order Service* marca la orden como `EXPIRED` y el *Inventory Service* libera los tickets a `AVAILABLE` de forma sincronizada.

---

## Estrategia de Testing (Pirámide de Calidad)
Hemos aplicado una metodología **AAA (Arrange-Act-Assert)** con los siguientes niveles de cobertura:

* **60% Unit Testing (JUnit 5 & Mockito):** Pruebas exhaustivas de la lógica de dominio, validaciones de moneda y transiciones de la Máquina de Estados.
* **25% Integration Testing (StepVerifier):** Pruebas de flujo reactivo sin bloqueo, verificando la integración con DynamoDB Local y LocalStack.
* **15% E2E & Smoke Tests:** Validación del flujo completo desde el `RouterFunction/Handler` hasta la persistencia final.
* **Gestión Global de Errores:** Un `GlobalExceptionHandler` captura excepciones reactivas y las transforma en respuestas estandarizadas con códigos de negocio (ej. `EVT-004` para conflictos de concurrencia), cumpliendo con el estándar de Webflux.

---

## 🛠️ Stack Tecnológico
* **Lenguaje:** Java 25 (Uso de Records, Sealed Classes y Pattern Matching).
* **Framework:** Spring Boot 4.x & WebFlux (Handlers/Routers funcionales).
* **Persistencia:** DynamoDB (NoSQL de alta escala).
* **Mensajería:** AWS SQS & SNS (vía LocalStack).
* **Cache/Streaming:** Redis 7.2.
* **IaC:** **Terraform** (Arquitectura modular con `main.tf`, `variables.tf` y `outputs.tf`).

---

## 🚀 Despliegue y Ejecución Local

### Infraestructura (Docker Compose)
El entorno local emula completamente una arquitectura de nube mediante los siguientes contenedores:
1.  **DynamoDB Local:** Persistencia principal en puerto `8000`.
2.  **LocalStack:** Emulación de SQS y SNS en puerto `4566`.
3.  **Redis:** Caché y streams con persistencia de datos.
4.  **Init-Scripts:** Scripts automáticos que crean las tablas, colas y tópicos al iniciar el stack.

**Comando de inicio:**
```bash
docker-compose up -d
```

## Ejecución de Servicios
```bash
# Inventory Service (Puerto 8081)
./mvnw spring-boot:run
```
```bash
# Order Service (Puerto 8080)
./mvnw spring-boot:run
```

---

##  Roadmap y Escalabilidad Cloud

Aunque el ambiente de desarrollo es local, la arquitectura ha sido diseñada bajo principios **Cloud-Ready**, asegurando una transición fluida hacia un entorno de producción real en AWS:

###  Infraestructura como Código (IaC)
Se incluyen módulos de **Terraform** listos para producción que permiten desplegar la infraestructura completa de forma automatizada:
* **Computo:** Configuración para **AWS ECS Fargate**, permitiendo un escalado elástico de los microservicios sin gestionar servidores.
* **Mensajería:** Transición transparente de LocalStack a **Amazon SQS** (colas estándar y FIFO) y **Amazon SNS** para el abanico de eventos (*Fan-out*).
* **Persistencia:** Configuración de **DynamoDB con Global Tables**, garantizando alta disponibilidad y replicación multi-región.

###  Observabilidad y Trazabilidad Distribuida
El sistema está preparado para una operación transparente en la nube:
* **Tracing:** Implementación lista para inyectar `traceId` en los headers de cada petición, facilitando la trazabilidad de punta a punta en **AWS X-Ray** o **CloudWatch ServiceLens**.
* **Logs Estructurados:** Formateo de logs compatible con **CloudWatch Logs Insights** para realizar consultas complejas sobre el comportamiento del sistema en tiempo real.

### Resiliencia Inteligente y Análisis de Errores
* **DLQ** Los mensajes que terminan en la **Dead Letter Queue (DLQ)** mantienen su contexto original.
* **Estrategia de Retries:** Implementación de *Exponential Backoff* y *Jitter* para evitar tormentas de reintentos sobre servicios críticos de la nube.

---