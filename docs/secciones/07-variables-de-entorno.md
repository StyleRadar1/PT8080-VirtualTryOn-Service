[⬅ Volver al README Principal](../../README.md)

---

## 7. VARIABLES DE ENTORNO

Todas las configuraciones dependientes de entorno se gestionan mediante variables externalizadas conformes al principio III de **Twelve-Factor App**.

| Variable | Descripción | Valor por Defecto (`dev`) | Requerido en QA / Prod |
| :--- | :--- | :--- | :---: |
| `SERVER_PORT` | Puerto HTTP del microservicio | `8500` | Sí |
| `SPRING_PROFILES_ACTIVE` | Perfil activo de Spring Boot (`dev`, `qa`, `prod`) | `dev` | Sí |
| `DB_URL` | URL JDBC de PostgreSQL aislado (`probador_db` / `probador_db_qa` / `probador_db_prod`) | `jdbc:postgresql://localhost:5432/probador_db` | Sí |
| `DB_USER` | Usuario de base de datos PostgreSQL | `postgres` | Sí |
| `DB_PASSWORD` | Contraseña de base de datos PostgreSQL | *(Inyectada por `.env` / Secret)* | Sí |
| `REDIS_HOST` | Hostname del **device único compartido** de Redis | `localhost` | Sí |
| `REDIS_PORT` | Puerto de conexión del device único de Redis | `6379` | Sí |
| `REDIS_PASSWORD` | Contraseña de conexión del device único de Redis | *(Vacío en local / Secret en nube)* | Sí |
| `RABBITMQ_HOST` | Hostname del **broker único compartido** RabbitMQ | `localhost` | Sí |
| `RABBITMQ_PORT` | Puerto AMQP de RabbitMQ | `5672` | Sí |
| `RABBITMQ_USER` | Usuario de conexión RabbitMQ | `guest` | Sí |
| `RABBITMQ_PASSWORD` | Contraseña de conexión RabbitMQ | `guest` | Sí |
| `AZURE_STORAGE_BASE_URL` | URL base del contenedor en Azure Blob Storage | *(Inyectada por `.env` / Secret)* | Sí |
| `AZURE_SAS_TOKEN` | Token SAS de administración de blobs (subida y purga Ley 1581) | *(Inyectada por `.env` / Secret)* | Sí |
| `AZURE_READ_SAS_TOKEN` | Token SAS de **solo lectura (`sp=r`, `sr=b`, `spr=https`)** | *(Inyectada por `.env` / Secret)* | Sí |
| `JWT_SECRET_BASE64` | Clave secreta HMAC-SHA256 en Base64 para validación JWT | *(Inyectada por `.env` / Secret)* | Sí |
| `FASHN_API_URL` | Endpoint base API de inferencia FASHN AI | `https://api.fashn.ai/v1` | Sí |
| `FASHN_API_KEY` | Llave de autenticación API FASHN AI | *(Inyectada por `.env` / Secret)* | Sí |
| `MAX_DAILY_QUOTA` | Cuota máxima de pruebas diarias por usuario (24h TTL) | `10` | Sí |

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

# Persistencia PostgreSQL (probador_db)
DB_HOST=localhost
DB_PORT=5442
DB_NAME=probador_db
DB_USER=styleradar_user
DB_PASSWORD=styleradar_dev_password

# Caché en Memoria y Cuotas (Redis)
REDIS_HOST=localhost
REDIS_PORT=6389

# Broker de Eventos (RabbitMQ)
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_EXCHANGE=styleradar.events

# Proveedor Externo de IA Generativa (FASHN AI)
FASHN_AI_BASE_URL=https://api.fashn.ai/v1
FASHN_AI_API_KEY=mock-dev-key

# Reglas de Negocio
MAX_DAILY_QUOTA=3

# Seguridad JWT Bearer
JWT_PUBLIC_KEY=classpath:keys/public-key.pem
```

</details>

---

[⬅ Volver al README Principal](../../README.md)
