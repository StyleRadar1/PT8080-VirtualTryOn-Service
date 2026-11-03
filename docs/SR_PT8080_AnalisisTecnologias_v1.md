# StyleRadar · Análisis de Tecnologías
## SQUAD PORTAL8080 (PT8080) · DOSW · SEMESTRE 6 · 2026

**Microservicio Asignado:** `PT8080-VirtualTryOn-Service` (Puerto `8092` · Paquete 5)  
**Asignatura:** Diseño y Operación de Software (DOSW)  
**Entrega:** Sprint 01 · Semestre 6 · Noviembre de 2026  

---

### Integrantes del Squad Portal8080 (PT8080)
| Nombre Completo | Rol Oficial en el Squad | Área de Responsabilidad |
|---|---|---|
| **Daniel Valero Quiroga** | **Líder del Equipo** | Coordinación general, diseño del servicio y gestión de entregables. |
| **Jose Alejandro Martinez Arias** | **Desarrollador Backend** | Lógica de dominio hexagonal, casos de uso, validaciones y suite de pruebas. |
| **Juan David Munar** | **Desarrollador Backend** | Adaptadores de persistencia JPA, configuración Docker Compose y RabbitMQ. |
| **Kevin** | **Desarrollador Frontend** | Integración de interfaz de usuario, subida de fotografía frontal y visualización VTON. |

---

## 00. Estructura del Documento

| Sección | Título y Alcance | Descripción Técnica de los Contenidos |
|---|---|---|
| **01** | **Contexto y Drivers del Microservicio** | Planteamiento del problema de calce en moda, drivers ISO/IEC 25010 y restricciones técnicas de StyleRadar. |
| **02** | **Metodología de Evaluación Multicriterio** | Cinco criterios ponderados (C1 a C5), escala objetiva de 1 a 5 y estándar formal de ADRs. |
| **03** | **Matrices de Decisión: Backend & Runtime** | ADR-001 (Lenguaje: Java 21 LTS) y ADR-002 (Framework: Spring Boot 3.3.4) con justificaciones extensas de ingeniería. |
| **03** | **Matrices de Decisión: Persistencia & Storage** | ADR-003 (Base de Datos: PostgreSQL 16.3) y ADR-004 (Caching: Redis 7.2 + Azure Blob Storage con SAS < 30s por Ley 1581). |
| **03** | **Matrices de Decisión: Mensajería & Gateway** | ADR-005 (Broker: RabbitMQ 3.13.2 con AMQP y DLQ) y ADR-006 (Perímetro: Spring Cloud Gateway 2023.0.3 / 4.1.x). |
| **03** | **Matrices de Decisión: Motor de IA VTON** | ADR-007 (FASHN AI API + Patrón Strategy con Mock local sin costo para CI/CD a $0 USD). |
| **04** | **Matriz de Riesgos Técnicos y Mitigación** | Riesgos reales de desarrollo: cuotas de IA, latencias > 30s, sanciones de Habeas Data y fallos de red. |
| **05** | **Conclusiones Tecnológicas y Equipo** | Síntesis del stack consolidado definitivo con versiones fijas y datos del Squad Portal8080. |

---

## 01. Contexto y Drivers del Microservicio

### 1.1 Planteamiento del Problema y Justificación de Microservicios
En el comercio electrónico de ropa vintage y marcas independientes, los compradores dudan sobre cómo lucirá la prenda puesta sobre su propio cuerpo. Nuestro microservicio **PT8080-VirtualTryOn-Service** resuelve esto implementando un probador virtual asistido por inteligencia artificial generativa (Virtual Try-On / VTON).

Para evitar que la carga pesada de inferencia de IA (que toma entre 12 y 25 segundos) congele el catálogo o la pasarela de pagos de StyleRadar, adoptamos el principio de **Database-per-Service** y límites de contexto basados en Domain-Driven Design (DDD): nuestro microservicio opera de forma totalmente asíncrona (respondiendo de inmediato `HTTP 202 Accepted` con un `jobId`) y gestiona de manera autónoma su esquema relacional `probador_db`.

### 1.2 Atributos de Calidad Clave (ISO/IEC 25010)
| Atributo ISO 25010 | Métrica / Requisito | Impacto Técnico en el Microservicio |
|---|---|---|
| **Rendimiento (Eficiencia)** | Latencia < 30s (SR-RNF-05) | Ejecución asíncrona no bloqueante con WebClient y polling ligero desacoplado en Redis 7.2. |
| **Seguridad y Privacidad** | Habeas Data (SR-RF-26 / Ley 1581) | Fotos corporales nunca públicas; URLs firmadas con SAS Tokens (< 30s) y purga física obligatoria. |
| **Disponibilidad y Resiliencia** | Fallo tolerante ante caída de IA | Implementación del patrón Strategy con fallback local `MockAiStrategy` a $0 USD de costo. |
| **Mantenibilidad** | Arquitectura Hexagonal estricta | Dominio 100% puro desacoplado de frameworks e infraestructura, probado con ArchUnit. |
| **Auditabilidad** | Trazabilidad de consentimiento | Registro inmutable de fecha, IP y versión de términos aceptados previo a cualquier inferencia. |

---

## 02. Metodología de Evaluación Multicriterio

### 2.1 Criterios de Selección y Ponderaciones
| ID | Criterio de Calidad | Peso (%) | Justificación Técnica en el Contexto del Microservicio |
|---|---|---|---|
| **C1** | **Rendimiento y Latencia** | **25 %** | Capacidad de procesar peticiones concurrentes sin retener conexiones JDBC ni bloquear hilos de servidor durante los 15-25 segundos que tarda la IA. |
| **C2** | **Madurez y Estabilidad LTS** | **20 %** | Ecosistema respaldado por soporte a largo plazo (LTS), documentación consolidada y baja tasa de rotura por actualizaciones menores. |
| **C3** | **Velocidad del Equipo** | **20 %** | Curva de aprendizaje controlada para los integrantes del squad, tipado estricto para prevenir bugs en compilación y alta productividad con tooling Java/Spring. |
| **C4** | **Operabilidad y Ecosistema** | **15 %** | Facilidad de empaquetado en Docker, orquestación en Docker Compose local, healthchecks nativos y observabilidad con Spring Boot Actuator. |
| **C5** | **Costo y Viabilidad Académica** | **20 %** | Posibilidad de ejecutar la totalidad del microservicio localmente a costo $0 USD, usando tiers gratuitos o emuladores locales sin depender de tarjetas cloud de pago. |

### 2.2 Escala de Calificación
- **5 (Excelente):** Cumple con creces el criterio; soporte nativo, sin dependencias complejas y óptimo para el sprint.
- **4 (Bueno):** Cumple adecuadamente con bajo impacto en complejidad operativa.
- **3 (Aceptable):** Cumple pero exige esfuerzo de integración manual o consumo medio de recursos.
- **2 (Deficiente):** Presenta limitaciones serias en concurrencia, costos o curva de aprendizaje pronunciada.
- **1 (Inviable):** Bloquea el desarrollo local, requiere pago obligatorio en la nube o incumple las directrices del proyecto.

### 2.3 Estándar de Architectural Decision Records (ADR)
Cada decisión técnica se documenta bajo el formato formal de ADR (Michael Nygard):

$$\text{Puntaje Total} = \sum_{i=1}^{5} ( \text{Calificación}_i \times \text{Peso}_i )$$

---

## 03. Matrices de Decisión de Diseño (ADRs)

### 3.1 Lenguaje & Runtime de Ejecución Backend (ADR-001)

| Criterio (Peso) | Java 21 LTS (Temurin) | Node.js 20 LTS (TypeScript) | Go 1.22 (Golang) | Python 3.12 (FastAPI) |
|---|---|---|---|---|
| C1: Rendimiento (25 %) | ★★★★★ (5) | ★★★★☆ (4) | ★★★★★ (5) | ★★★☆☆ (3) |
| C2: Madurez & LTS (20 %) | ★★★★★ (5) | ★★★★☆ (4) | ★★★★☆ (4) | ★★★★☆ (4) |
| C3: Velocidad Dev (20 %) | ★★★★☆ (4) | ★★★★★ (5) | ★★★☆☆ (3) | ★★★★★ (5) |
| C4: Operabilidad (15 %) | ★★★★★ (5) | ★★★★☆ (4) | ★★★★★ (5) | ★★★☆☆ (3) |
| C5: Costo & TCO (20 %) | ★★★★★ (5) | ★★★★★ (5) | ★★★★★ (5) | ★★★★★ (5) |
| **Total Ponderado** | **4.75 / 5.00 (Seleccionado)** | 4.40 / 5.00 | 4.45 / 5.00 | 3.95 / 5.00 |

> **Justificación Técnica del Equipo:**  
> Como estudiantes de ingeniería evaluamos la evolución del lenguaje y seleccionamos **Java 21 LTS** por dos motivos técnicos decisivos: los *Records* (que nos permiten definir Value Objects inmutables como `UsuarioId`, `PrendaId` y `SasSignedUrl` sin código repetitivo) y los *Virtual Threads* de Project Loom, que facilitan manejar peticiones concurrentes de larga duración sin agotar el pool de hilos del sistema operativo. Aunque Python es el estándar de facto en scripts de IA generativa, para el desarrollo de un microservicio de misión crítica con Domain-Driven Design (DDD) y Arquitectura Hexagonal, el tipado estricto de Java previene errores de tipos en tiempo de compilación. Node.js con TypeScript ofrecía agilidad, pero la gestión de memoria en payloads pesados de imágenes multipart y la falta de un framework empresarial tan consolidado como Spring inclinaron la balanza definitivamente hacia Java 21 LTS con el compilador Eclipse Temurin.

---

### 3.2 Framework Backend & Microservicios (ADR-002)

| Criterio (Peso) | Spring Boot 3.3.4 | FastAPI (Python) | NestJS (Node.js) | Quarkus 3.8 |
|---|---|---|---|---|
| C1: Rendimiento (25 %) | ★★★★★ (5) | ★★★☆☆ (3) | ★★★★☆ (4) | ★★★★★ (5) |
| C2: Madurez & LTS (20 %) | ★★★★★ (5) | ★★★☆☆ (3) | ★★★★☆ (4) | ★★★☆☆ (3) |
| C3: Velocidad Dev (20 %) | ★★★★★ (5) | ★★★★☆ (4) | ★★★★☆ (4) | ★★★☆☆ (3) |
| C4: Operabilidad (15 %) | ★★★★★ (5) | ★★★☆☆ (3) | ★★★★☆ (4) | ★★★★★ (5) |
| C5: Costo & TCO (20 %) | ★★★★★ (5) | ★★★★★ (5) | ★★★★★ (5) | ★★★★☆ (4) |
| **Total Ponderado** | **5.00 / 5.00 (Seleccionado)** | 3.55 / 5.00 | 4.20 / 5.00 | 4.05 / 5.00 |

> **Justificación Técnica del Equipo:**  
> Adoptamos formalmente **Spring Boot 3.3.4** porque nos permite implementar una Arquitectura Hexagonal estricta: nuestro dominio (modelos, agregados y puertos) es código Java puro sin acoplamiento al framework, mientras que la infraestructura aprovecha la inyección de dependencias desacoplada mediante `@Configuration` e interfaces Java. Además, Spring Boot 3.3.4 integra nativamente el cliente HTTP reactivo `WebClient` (Spring WebFlux), indispensable para invocar la API asíncrona de FASHN AI mediante polling no bloqueante. También nos entrega métricas de observabilidad listas para producción con Spring Boot Actuator (`/actuator/health` y métricas Prometheus), facilitando las pruebas de humo en Docker. Descartamos FastAPI porque su ecosistema ORM (SQLAlchemy) carece del nivel de madurez y validaciones declarativas que nos entrega Spring Data JPA con Hibernate, y NestJS porque su ecosistema en TypeScript no cuenta con bibliotecas de verificación de arquitectura comparables a ArchUnit.

---

### 3.3 Persistencia Relacional & Base de Datos (ADR-003)

| Criterio (Peso) | PostgreSQL 16.3 | MySQL 8.0 | MongoDB 7.0 (NoSQL) | Amazon Aurora Cloud |
|---|---|---|---|---|
| C1: Rendimiento (25 %) | ★★★★★ (5) | ★★★★☆ (4) | ★★★★☆ (4) | ★★★★★ (5) |
| C2: Madurez & LTS (20 %) | ★★★★★ (5) | ★★★★★ (5) | ★★★★☆ (4) | ★★★★★ (5) |
| C3: Velocidad Dev (20 %) | ★★★★★ (5) | ★★★★☆ (4) | ★★★☆☆ (3) | ★★★☆☆ (3) |
| C4: Operabilidad (15 %) | ★★★★★ (5) | ★★★★☆ (4) | ★★★★☆ (4) | ★★★★☆ (4) |
| C5: Costo & TCO (20 %) | ★★★★★ (5) | ★★★★★ (5) | ★★★★☆ (4) | ★★☆☆☆ (2) |
| **Total Ponderado** | **5.00 / 5.00 (Seleccionado)** | 4.40 / 5.00 | 3.80 / 5.00 | 3.95 / 5.00 |

> **Justificación Técnica del Equipo:**  
> Adoptamos **PostgreSQL 16.3** como base de datos autónoma de nuestro microservicio (`probador_db` en puerto local 5434). Inicialmente analizamos si el servicio podía ser stateless, pero lo descartamos por completo: requerimos transaccionalidad ACID rigurosa para descontar las cuotas diarias de pruebas del plan Radar+ (SR-RF-102) y prevenir condiciones de carrera cuando un usuario dispara múltiples peticiones concurrentes desde la interfaz web. Además, el consentimiento biométrico (SR-RF-26 / Ley 1581) exige una tabla de auditoría inmutable con marcas de tiempo e identificadores UUID v4. PostgreSQL 16.3 nos permite almacenar los parámetros específicos de la prenda y el payload de respuesta de la IA en columnas nativas `JSONB` indexadas con GIN, brindándonos la flexibilidad de un motor documental sin renunciar a la consistencia transaccional y a las migraciones versionadas con Flyway. Descartamos NoSQL (MongoDB) porque no garantiza consistencia estricta en operaciones cruzadas de cuota y auditoría.

---

### 3.4 Caching en Memoria & Almacenamiento Seguro de Fotos (ADR-004)

| Criterio (Peso) | Redis 7.2 + Azure Blob (SAS) | Guardar fotos en BD (BYTEA) | Memcached + Disco local | AWS S3 + DynamoDB |
|---|---|---|---|---|
| C1: Rendimiento (25 %) | ★★★★★ (5) | ★☆☆☆☆ (1) | ★★★☆☆ (3) | ★★★★★ (5) |
| C2: Madurez & LTS (20 %) | ★★★★★ (5) | ★★★★★ (5) | ★★★☆☆ (3) | ★★★★★ (5) |
| C3: Velocidad Dev (20 %) | ★★★★★ (5) | ★★☆☆☆ (2) | ★★★☆☆ (3) | ★★★☆☆ (3) |
| C4: Operabilidad (15 %) | ★★★★★ (5) | ★☆☆☆☆ (1) | ★★☆☆☆ (2) | ★★★★☆ (4) |
| C5: Costo & TCO (20 %) | ★★★★★ (5) | ★☆☆☆☆ (1) | ★★★☆☆ (3) | ★★☆☆☆ (2) |
| **Total Ponderado** | **5.00 / 5.00 (Seleccionado)** | 2.10 / 5.00 | 2.85 / 5.00 | 3.95 / 5.00 |

> **Justificación Técnica del Equipo:**  
> Separamos categóricamente el almacenamiento de estado del almacenamiento binario. Guardar fotos corporales de 5 MB como BYTEA o BLOBs dentro de PostgreSQL es un antipatrón de diseño que colapsaría el buffer pool de la base de datos y saturaría el disco en pocos días. Por ello, adoptamos **Azure Blob Storage** con cifrado en reposo (AES-256) para almacenar las imágenes de forma privada y efímera. Para cumplir el requerimiento SR-RF-26, nunca entregamos URLs públicas; generamos enlaces temporales firmados con **SAS Tokens con expiración < 30 segundos**, garantizando que nadie pueda acceder a la fotografía corporal una vez descargada. En cuanto a **Redis 7.2**, lo utilizamos como caché en memoria para almacenar el estado transitorio del ticket (QUEUED, PROCESSING) con un TTL de 10 minutos, permitiendo que el frontend realice polling de alta frecuencia sin impactar la base de datos relacional. Además, cuando el usuario solicita eliminar sus datos (SR-RF-27 / Ley 1581), el backend ejecuta el borrado físico inmediato en Azure Blob con `deleteIfExists`.

---

### 3.5 Mensajería Asíncrona & Eventos de Dominio (ADR-005)

| Criterio (Peso) | RabbitMQ 3.13.2 (AMQP 0-9-1) | Apache Kafka | Redis Streams | Azure Service Bus |
|---|---|---|---|---|
| C1: Rendimiento (25 %) | ★★★★☆ (4) | ★★★★★ (5) | ★★★★☆ (4) | ★★★★☆ (4) |
| C2: Madurez & LTS (20 %) | ★★★★★ (5) | ★★★★★ (5) | ★★★☆☆ (3) | ★★★★☆ (4) |
| C3: Velocidad Dev (20 %) | ★★★★★ (5) | ★★☆☆☆ (2) | ★★★★☆ (4) | ★★★☆☆ (3) |
| C4: Operabilidad (15 %) | ★★★★★ (5) | ★★☆☆☆ (2) | ★★★★☆ (4) | ★★★★☆ (4) |
| C5: Costo & TCO (20 %) | ★★★★★ (5) | ★★★☆☆ (3) | ★★★★☆ (4) | ★★☆☆☆ (2) |
| **Total Ponderado** | **4.75 / 5.00 (Seleccionado)** | 3.55 / 5.00 | 3.80 / 5.00 | 3.45 / 5.00 |

> **Justificación Técnica del Equipo:**  
> Como equipo de estudiantes evaluamos el consumo de recursos en nuestras computadoras portátiles. Apache Kafka es excelente para streaming masivo de gigabytes de telemetría por segundo, pero levantar Kafka con Zookeeper o KRaft en Docker Compose consume entre 1.5 y 2 GB de memoria RAM, volviendo lenta la máquina al compilar y correr las pruebas. En contraste, **RabbitMQ 3.13.2** arranca en 5 segundos, consume escasos 60 a 80 MB de RAM y se ajusta con total precisión al volumen de eventos de negocio de StyleRadar (pruebas completadas, consentimientos revocados). Empleamos un *Topic Exchange* (`styleradar.exchange`) con colas durables; si el microservicio de analítica o catálogo están apagados, el mensaje no se pierde. Además, RabbitMQ cuenta con *Dead Letter Exchanges (DLX)* nativos para redirigir mensajes fallidos a la cola `tryon.dead-letter` sin perder auditoría, y cuenta con una interfaz web en el puerto 15672 que nos permite monitorear el flujo de mensajes en vivo durante las sustentaciones con el docente. Descartamos Azure Service Bus porque requiere conexión obligatoria a internet y credenciales propietarias que impiden el desarrollo local offline.

---

### 3.6 API Gateway Perimetral & Seguridad Centralizada (ADR-006)

| Criterio (Peso) | Spring Cloud Gateway 2023.0.3 | Kong API Gateway | NGINX Reverse Proxy | Traefik Proxy |
|---|---|---|---|---|
| C1: Rendimiento (25 %) | ★★★★★ (5) | ★★★★★ (5) | ★★★★★ (5) | ★★★★☆ (4) |
| C2: Madurez & LTS (20 %) | ★★★★★ (5) | ★★★★☆ (4) | ★★★★★ (5) | ★★★☆☆ (3) |
| C3: Velocidad Dev (20 %) | ★★★★★ (5) | ★★★☆☆ (3) | ★★★☆☆ (3) | ★★★★☆ (4) |
| C4: Operabilidad (15 %) | ★★★★☆ (4) | ★★★★☆ (4) | ★★★★☆ (4) | ★★★★☆ (4) |
| C5: Costo & TCO (20 %) | ★★★★★ (5) | ★★★☆☆ (3) | ★★★★☆ (4) | ★★★★☆ (4) |
| **Total Ponderado** | **4.85 / 5.00 (Seleccionado)** | 3.90 / 5.00 | 4.30 / 5.00 | 3.80 / 5.00 |

> **Justificación Técnica del Equipo:**  
> Adoptamos **Spring Cloud Gateway 2023.0.3 (Leyton / Gateway 4.1.x)** operando en el puerto perimetral 8080. Está construido sobre el servidor Netty y Spring WebFlux, proporcionando enrutamiento completamente reactivo y no bloqueante. El Gateway actúa como barrera de seguridad centralizada: valida la firma de los tokens JWT antes de permitir el ingreso hacia la ruta interna de nuestro servicio (`/api/v1/try-on/**` hacia el puerto 8092), aplica políticas globales de CORS para el cliente web y ejecuta un filtro de *Rate Limiting* respaldado en Redis 7.2 (algoritmo Token Bucket) que evita ataques de denegación de servicio o scripts que intenten saturar la API de IA. Descartamos Kong Gateway y NGINX porque obligan a mantener configuraciones declarativas externas en Lua y plugins separados que desacoplan la capa de seguridad del ecosistema Spring del proyecto, complicando la propagación limpia de claims de identidad hacia los encabezados internos de los microservicios.

---

### 3.7 Motor de IA para Probador Virtual VTON (ADR-007)

| Criterio (Peso) | FASHN AI API + Mock Dual | Self-Hosted GPU (Stable Diffusion) | Replicate IDM-VTON API |
|---|---|---|---|
| C1: Rendimiento (25 %) | ★★★★★ (5) | ★★★☆☆ (3) | ★★★★☆ (4) |
| C2: Madurez & LTS (20 %) | ★★★★☆ (4) | ★★★★☆ (4) | ★★★★☆ (4) |
| C3: Velocidad Dev (20 %) | ★★★★★ (5) | ★★☆☆☆ (2) | ★★★★☆ (4) |
| C4: Operabilidad (15 %) | ★★★★★ (5) | ★☆☆☆☆ (1) | ★★★★★ (5) |
| C5: Costo & TCO (20 %) | ★★★★★ (5) | ★☆☆☆☆ (1) | ★★★☆☆ (3) |
| **Total Ponderado** | **4.80 / 5.00 (Seleccionado)** | 2.30 / 5.00 | 3.95 / 5.00 |

> **Justificación Técnica del Equipo:**  
> Como estudiantes universitarios nos enfrentamos a una restricción económica real: los modelos de difusión generativa para Virtual Try-On (como Stable Diffusion XL con ControlNet o IDM-VTON) demandan servidores dedicados con GPUs NVIDIA A100 o T4 cuyo alquiler en la nube oscila entre $200 y $600 USD mensuales, lo cual es inviable para nuestro presupuesto académico. Por esta razón, seleccionamos la API especializada de **FASHN AI v1** con un costo de $0.075 USD por inferencia real de alta calidad fotográfica.  
> Sin embargo, para no agotar el crédito durante el desarrollo ni bloquear la ejecución de pruebas automatizadas en GitHub Actions, implementamos el patrón de diseño **Strategy**: creamos el componente `MockAiStrategy` que genera resultados de prueba con un retardo controlado de 2 segundos a **$0 USD de costo**. Mediante la variable de entorno `AI_PROVIDER=mock` o `AI_PROVIDER=fashn`, el backend conmuta de estrategia sin tocar una sola línea de código fuente.

---

## 04. Matriz de Riesgos y Mitigación Técnica

### 4.1 Evaluación de Riesgos Técnicos del Microservicio
| Riesgo Técnico Identificado | Probabilidad | Impacto | Estrategia de Mitigación Implementada en el Microservicio |
|---|---|---|---|
| **Agotamiento de créditos en la API de IA** | Media | Alto | Implementación del patrón Strategy con `MockAiStrategy`. Si la API de FASHN se queda sin saldo o no hay conexión a internet durante la sustentación en el aula, el microservicio conmuta a modo mock mediante `AI_PROVIDER=mock` y la demo continúa sin interrupciones. |
| **Latencia de inferencia superior a 30s (SR-RNF-05)** | Media | Alto | Arquitectura asíncrona mediante respuesta inmediata `HTTP 202 Accepted` con `jobId`. El cliente no mantiene conexiones HTTP abiertas; realiza polling ligero del estado del Job en Redis 7.2 sin bloquear hilos ni retener conexiones JDBC. |
| **Sanciones legales por retener fotos biométricas** | Baja | Crítico | Cumplimiento estricto de la Ley 1581 de 2012 (Habeas Data): validación obligatoria de consentimiento previo en `BiometricConsentValidator`, enlaces temporales SAS de corta duración (< 30s) y borrado físico inmediato en Azure Blob (`deleteIfExists`) al finalizar. |
| **Caídas de red al notificar resultados a otros servicios** | Media | Medio | Uso de colas durables en RabbitMQ 3.13.2 con *Dead Letter Exchange* (`tryon.dead-letter`). Si el servicio de analítica o catálogo está caído, el mensaje no se pierde y se reintenta automáticamente con backoff exponencial. |
| **Saturación de RAM por imágenes pesadas en Docker** | Media | Medio | Validación temprana en `BiometricImageValidator` rechazando archivos mayores a 5 MB o formatos no permitidos antes de procesarlos en memoria. Las imágenes se transfieren en streaming directo a Azure Blob Storage. |

### 4.2 Monitoreo Continuo y Resiliencia en el Pipeline
- **Quality Gate en GitHub Actions:** El pipeline `ci-cd.yml` ejecuta compilación limpia en Java 21 Temurin, 16 pruebas automatizadas y exige umbral JaCoCo $\ge 85\%$ (actualmente 90.1% en líneas y 100% en ramas).
- **Healthcheck y Docker Compose:** Controles de salud con `pg_isready` para PostgreSQL y comprobación de puertos para RabbitMQ antes de levantar el contenedor del servicio, garantizando arranque ordenado.
- **Manejo de Errores RFC 7807:** Respuestas de error estandarizadas con `ProblemDetail`, impidiendo fugas de stacktrace o exposición de rutas internas del servidor hacia el cliente.

---

## 05. Conclusiones y Equipo de Trabajo

### 5.1 Resumen Consolidado del Stack Tecnológico Definitivo
| Capa Arquitectónica | Tecnología Seleccionada & Versión Fija | Rol Principal en el Microservicio |
|---|---|---|
| **Lenguaje & Runtime** | **Java 21 LTS** (OpenJDK Eclipse Temurin) | Records inmutables, Virtual Threads (Project Loom) y tipado estricto para DDD. |
| **Framework Base** | **Spring Boot 3.3.4** | Arquitectura Hexagonal, inyección de dependencias desacoplada y Actuator. |
| **Cliente HTTP Reactivo** | **Spring WebFlux (WebClient)** | Llamadas HTTP asíncronas y no bloqueantes hacia la API externa de FASHN AI. |
| **Persistencia Relacional** | **PostgreSQL 16.3** (`probador_db` :5434) | Transaccionalidad ACID en cuotas Radar+ (SR-RF-102) y columnas JSONB. |
| **Caché & Polling** | **Redis 7.2** (Puerto 6379) | Almacenamiento en memoria de estados de Jobs para polling del cliente sin tocar disco. |
| **Mensajería de Eventos** | **RabbitMQ 3.13.2** (AMQP 0-9-1 :5672) | Topic Exchanges y Dead Letter Queues para publicación asíncrona de eventos. |
| **Gateway Perimetral** | **Spring Cloud Gateway 2023.0.3** (:8080) | Reverse proxy perimetral, validación de tokens JWT y rate limiting centralizado. |
| **Almacenamiento de Fotos** | **Azure Blob Storage SDK v12** | Bucket privado con cifrado AES-256, SAS Tokens < 30s y purga física (Ley 1581). |
| **Motor de Inferencia IA** | **FASHN AI API v1 + Mock Strategy** | Inferencia generativa de alta calidad con fallback local para pruebas a $0 USD. |
| **Testing & Calidad** | **JUnit 5 + JaCoCo 0.8.11 + ArchUnit 1.3.0** | 16 pruebas unitarias e integración con 90.1% de cobertura de líneas y 100% ramas. |

### 5.2 Integrantes del Squad Portal8080 (PT8080)
| Nombre Completo | Rol Oficial en el Squad | Área de Responsabilidad |
|---|---|---|
| **Daniel Valero Quiroga** | **Líder del Equipo** | Coordinación general, diseño del servicio y gestión de entregables. |
| **Jose Alejandro Martinez Arias** | **Desarrollador Backend** | Lógica de dominio hexagonal, casos de uso, validaciones y suite de pruebas. |
| **Juan David Munar** | **Desarrollador Backend** | Adaptadores de persistencia JPA, configuración Docker Compose y RabbitMQ. |
| **Kevin** | **Desarrollador Frontend** | Integración de interfaz de usuario, subida de fotografía frontal y visualización VTON. |

---
*StyleRadar · DOSW Semestre 6 · Squad Portal8080 (PT8080) · ms-probador-virtual :8092*
