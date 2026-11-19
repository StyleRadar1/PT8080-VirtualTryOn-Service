[⬅ Volver al README Principal](../../README.md)

---

## 5. TECNOLOGÍAS Y DEPENDENCIAS

El stack tecnológico de `PT8080-VirtualTryOn-Service` cumple con el estándar unificado de DOSW 2026:

| Capa / Componente | Tecnología | Versión | Justificación Técnica |
| :-------------------------------- | :------------------------- | :--------- | :-------------------------------------------------------------------------------------------------- |
| **Lenguaje Core** | Java LTS | `21` | Soporte nativo de Virtual Threads (Project Loom), Records inmutables y Pattern Matching. |
| **Framework Base** | Spring Boot | `3.2.5` | Ecosistema empresarial con soporte nativo para Spring Data JPA, Web y Spring AMQP. |
| **Base de Datos** | PostgreSQL | `16` | Integridad transaccional ACID, llaves UUIDv7 e indexación optimizada de jobs e históricos. |
| **Control de Esquema** | Flyway | `10.10.0` | Migraciones versionadas reproducibles de base de datos (`V1__init.sql`). |
| **Caché & Cuotas** | Redis | `7.2` | Gestión atómica en memoria de cuotas mensuales/diarias por usuario e idempotencia. |
| **Broker de Eventos** | RabbitMQ | `3.13` | Protocolo AMQP 0-9-1 para comunicación asíncrona desacoplada mediante Topic Exchanges. |
| **Cliente HTTP IA** | Spring Framework (Web) | `3.2.5` | `RestClient` síncrono y fluido (con Resilience4j Circuit Breaker y timeouts acotados) para consumo de la API externa de inferencia VTON (FASHN AI). |
| **Documentación API** | SpringDoc OpenAPI | `2.5.0` | Generación automática de especificación OpenAPI 3.0 y Swagger UI con esquema JWT Bearer. |
| **Pruebas Unitarias** | JUnit 5 + Mockito | `5.10.2` | Verificación del núcleo de dominio y lógica de aplicación sin inicializar contexto Spring. |
| **Pruebas de Integración** | Spring Boot Test + MockMvc | `3.2.5` | Pruebas de controladores REST, contratos HTTP y validación de esquemas RFC 7807. |
| **Testing de Contenedores** | Testcontainers | `1.19.7` | Aislamiento de PostgreSQL y RabbitMQ en contenedores efímeros durante pruebas de integración. |
| **Métricas de Cobertura** | JaCoCo Maven Plugin | `0.8.11` | Verificación automatizada de cobertura mínima obligatoria (≥ 85%) en fases de compilación y CI. |

---

[⬅ Volver al README Principal](../../README.md)
