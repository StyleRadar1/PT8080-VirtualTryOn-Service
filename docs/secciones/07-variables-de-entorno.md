[⬅ Volver al README Principal](../../README.md)

---

## 7. VARIABLES DE ENTORNO

Todas las configuraciones dependientes de entorno se gestionan mediante variables externalizadas conformes al principio III de **Twelve-Factor App**.

> [!IMPORTANT]
> **Configuración de Conexión según el Entorno donde corre Spring Boot:**
> * **Si Spring se ejecuta desde el equipo con `./mvnw` (Modo Host Local):**
>   - **PostgreSQL (`probador_db`):** Está mapeado en `localhost:5442`. La URL JDBC obligatoria es `jdbc:postgresql://localhost:5442/probador_db`.
>   - **Redis:** Está mapeado en `localhost:6389` (`REDIS_HOST=localhost`, `REDIS_PORT=6389`).
>   - **RabbitMQ:** Está mapeado en `localhost:5672` (`RABBITMQ_HOST=localhost`, `RABBITMQ_PORT=5672`).
> * **Si Spring se ejecuta dentro de Docker Compose (Modo Contenerizado en `styleradar-net`):**
>   - **PostgreSQL (`probador_db`):** Se resuelve internamente en `probador-postgres:5432`. La URL JDBC es `jdbc:postgresql://probador-postgres:5432/probador_db`.
>   - **Redis:** Se resuelve internamente en `probador-redis:6379` (`REDIS_HOST=probador-redis`, `REDIS_PORT=6379`).
>   - **RabbitMQ:** Se resuelve internamente en `probador-rabbitmq:5672` (`RABBITMQ_HOST=probador-rabbitmq`, `RABBITMQ_PORT=5672`).

| Variable | Descripción | Valor Local con `./mvnw` | Valor en Docker Compose | Requerido en QA / Prod |
| :--- | :--- | :--- | :--- | :---: |
| `SERVER_PORT` | Puerto HTTP del microservicio | `8500` | `8500` | Sí |
| `SPRING_PROFILES_ACTIVE` | Perfil activo de Spring Boot (`dev`, `qa`, `prod`) | `dev` | `dev` | Sí |
| `DB_URL` | URL JDBC de PostgreSQL aislado (`probador_db`) | `jdbc:postgresql://localhost:5442/probador_db` | `jdbc:postgresql://probador-postgres:5432/probador_db` | Sí |
| `DB_USER` | Usuario de base de datos PostgreSQL | `postgres` | `postgres` | Sí |
| `DB_PASSWORD` | Contraseña de base de datos PostgreSQL | *(Inyectada por `.env` / Secret)* | `postgres` | Sí |
| `REDIS_HOST` | Hostname del servicio Redis | `localhost` | `probador-redis` | Sí |
| `REDIS_PORT` | Puerto de conexión Redis | `6389` | `6379` | Sí |
| `REDIS_PASSWORD` | Contraseña de conexión Redis | *(Vacío en local)* | *(Vacío en local)* | Sí |
| `RABBITMQ_HOST` | Hostname del broker RabbitMQ | `localhost` | `probador-rabbitmq` | Sí |
| `RABBITMQ_PORT` | Puerto AMQP de RabbitMQ | `5672` | `5672` | Sí |
| `RABBITMQ_USER` | Usuario de conexión RabbitMQ | `guest` | `guest` | Sí |
| `RABBITMQ_PASSWORD` | Contraseña de conexión RabbitMQ | `guest` | `guest` | Sí |
| `AZURE_STORAGE_BASE_URL` | URL base del contenedor en Azure Blob Storage | *(Inyectada por `.env` / Secret)* | *(Inyectada por `.env` / Secret)* | Sí |
| `AZURE_SAS_TOKEN` | Token SAS de administración de blobs (subida y purga Ley 1581) | *(Inyectada por `.env` / Secret)* | *(Inyectada por `.env` / Secret)* | Sí |
| `AZURE_READ_SAS_TOKEN` | Token SAS de **solo lectura (`sp=r`, `sr=b`, `spr=https`)** | *(Inyectada por `.env` / Secret)* | *(Inyectada por `.env` / Secret)* | Sí |
| `JWT_SECRET_BASE64` | Clave secreta HMAC-SHA256 en Base64 para validación JWT | *(Inyectada por `.env` / Secret)* | *(Inyectada por `.env` / Secret)* | Sí |
| `FASHN_API_URL` | Endpoint base API de inferencia FASHN AI | `https://api.fashn.ai/v1` | `https://api.fashn.ai/v1` | Sí |
| `FASHN_API_KEY` | Llave de autenticación API FASHN AI | *(Inyectada por `.env` / Secret)* | *(Inyectada por `.env` / Secret)* | Sí |
| `MAX_DAILY_QUOTA` | Cuota máxima de pruebas diarias por usuario (24h TTL) | `10` | `10` | Sí |

<details>
<summary><b>Click para ver la Plantilla de Archivo .env.example Completa</b></summary>

<br>

```dotenv
# ==============================================================================
# STYLERADAR · VARIABLES DE ENTORNO LOCALES (.env)
# Microservicio: PT8080-VirtualTryOn-Service (ms-probador-virtual) · Puerto 8500
# ==============================================================================
SERVER_PORT=8500
SPRING_PROFILES_ACTIVE=dev

# ------------------------------------------------------------------------------
# Persistencia PostgreSQL (probador_db)
# Si ejecutas con ./mvnw -> DB_PORT=5442 y DB_URL=jdbc:postgresql://localhost:5442/probador_db
# Si ejecutas con Docker Compose -> DB_HOST=probador-postgres, DB_PORT=5432
# ------------------------------------------------------------------------------
DB_HOST=localhost
DB_PORT=5442
DB_NAME=probador_db
DB_URL=jdbc:postgresql://localhost:5442/probador_db
DB_USER=postgres
DB_PASSWORD=postgres

# ------------------------------------------------------------------------------
# Caché en Memoria y Cuotas (Redis)
# Si ejecutas con ./mvnw -> REDIS_PORT=6389
# Si ejecutas con Docker Compose -> REDIS_HOST=probador-redis, REDIS_PORT=6379
# ------------------------------------------------------------------------------
REDIS_HOST=localhost
REDIS_PORT=6389

# ------------------------------------------------------------------------------
# Broker de Eventos (RabbitMQ)
# Si ejecutas con ./mvnw -> RABBITMQ_HOST=localhost, RABBITMQ_PORT=5672
# Si ejecutas con Docker Compose -> RABBITMQ_HOST=probador-rabbitmq, RABBITMQ_PORT=5672
# ------------------------------------------------------------------------------
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_EXCHANGE=styleradar.exchange

# Proveedor Externo de IA Generativa (FASHN AI)
FASHN_API_URL=https://api.fashn.ai/v1
FASHN_API_KEY=mock-dev-key

# Reglas de Negocio
MAX_DAILY_QUOTA=10

# Seguridad JWT Bearer
JWT_SECRET_BASE64=dGVzdC1zZWNyZXQta2V5LXRlc3Qtc2VjcmV0LWtleS10ZXN0LXNlY3JldA==
```

</details>

---

[⬅ Volver al README Principal](../../README.md)
