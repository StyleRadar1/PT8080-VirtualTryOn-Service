[⬅ Volver al README Principal](../../README.md)

---

## 6. CÓMO EJECUTAR LOCAL Y CON DOCKER

### Prerrequisitos de Entorno

* **Java Development Kit (JDK):** Versión 21 LTS instalada (`java -version`).
* **Apache Maven:** Versión 3.9+ instalada (`mvn -version`) o uso del wrapper `./mvnw`.
* **Docker Engine & Docker Compose:** Versión 24+ en ejecución (`docker --version`).

### Ejecución Local con Maven (Perfil Dev)

Cuando Spring Boot se ejecuta desde la máquina host con `./mvnw`, consume la infraestructura auxiliar expuesta en los puertos mapeados del host (`localhost:5442` para PostgreSQL y `localhost:6389` para Redis).

Para que Spring apunte a los puertos mapeados en una terminal limpia (donde por defecto `application-dev.yml` usaría 5432 y 6379 si no estuvieran definidas), se configuran `DB_URL` y `REDIS_PORT` antes del arranque:

```bash
# 1. Clonar el repositorio
git clone https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service.git
cd PT8080-VirtualTryOn-Service

# 2. Levantar la infraestructura auxiliar con Docker Compose (PostgreSQL, Redis, RabbitMQ)
docker compose up -d probador-postgres probador-redis probador-rabbitmq

# 3. Exportar variables de conexión a los puertos publicados en el host
export DB_URL=jdbc:postgresql://localhost:5442/probador_db
export REDIS_PORT=6389

# 4. Ejecutar el microservicio localmente en el puerto 8500
./mvnw clean spring-boot:run -Dspring-boot.run.profiles=dev
```

> [!NOTE]
> **Parámetros de conexión al ejecutar con `./mvnw` desde el equipo host:**
> * **PostgreSQL (`probador_db`):** `localhost:5442` &nbsp;·&nbsp; URL JDBC: `jdbc:postgresql://localhost:5442/probador_db`
> * **Redis Server:** `localhost:6389` &nbsp;·&nbsp; Puerto: `6389`
> * **RabbitMQ Broker:** `localhost:5672` (Management Dashboard en `http://localhost:15672`)
> * *Para usuarios en Windows (PowerShell):*  
>   `$env:DB_URL="jdbc:postgresql://localhost:5442/probador_db"`  
>   `$env:REDIS_PORT="6389"`

### Ejecución Contenerizada Completa con Docker Compose

El repositorio incluye un archivo `docker-compose.yml` que orquesta la infraestructura completa junto al microservicio. En este modo, todos los contenedores conviven en la red virtual bridge `styleradar-net` y se resuelven mediante los nombres de servicio DNS internos:

<details>
<summary><b>Click para ver Instrucciones de Docker y Contenedores Orquestados</b></summary>

<br>

```bash
# Levantar todos los servicios en segundo plano (build multi-stage del microservicio + DB + Redis + RabbitMQ)
docker compose up -d --build

# Verificar el estado de los contenedores
docker compose ps

# Visualizar logs en tiempo real del microservicio
docker compose logs -f virtual-tryon-service

# Detener los servicios
docker compose down
```

#### Resolución de Conexiones según el Entorno de Ejecución:

| Componente | Conexión con `./mvnw` (Host Local) | Conexión dentro de Docker Compose (Red Interna) |
| :--- | :--- | :--- |
| **PostgreSQL (`probador_db`)** | `localhost:5442` &nbsp;(`jdbc:postgresql://localhost:5442/probador_db`) | `probador-postgres:5432` &nbsp;(`jdbc:postgresql://probador-postgres:5432/probador_db`) |
| **Redis Server** | `localhost:6389` | `probador-redis:6379` |
| **RabbitMQ Broker** | `localhost:5672` | `probador-rabbitmq:5672` |
| **Microservicio Try-On** | `http://localhost:8500` | `http://virtual-tryon-service:8500` *(mapeado a `localhost:8500`)* |
| **RabbitMQ Dashboard** | `http://localhost:15672` *(guest / guest)* | `http://localhost:15672` *(mapeado al host)* |

</details>

### Comprobación de Salud (Health Check)

```bash
curl -s http://localhost:8500/actuator/health
# Esperado: {"status":"UP"}
```

<div align="center">
  <img src="../images/DockerEvidence.png" alt="Evidencia 02 - Contenedores OCI en Ejecución (docker compose ps)" width="100%" style="border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);" />
  <p><sub><b>Figura 6.1:</b> Ejecución de <code>docker compose ps</code> verificando los 4 contenedores OCI levantados y en estado saludable (healthy): <code>sr-probador-virtual</code> (:8500), <code>sr-probador-postgres</code> (:5442), <code>sr-probador-redis</code> (:6389) y <code>sr-probador-rabbitmq</code> (:5672/15672).</sub></p>
</div>

---

[⬅ Volver al README Principal](../../README.md)
