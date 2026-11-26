<!-- ==============================================================================
  STYLE RADAR · PLANTILLA MAESTRA DE README PARA MICROSERVICIOS
  Asignatura: DOSW (Diseño y Operación de Soluciones Web) · 2026
  Estándar obligatorio unificado para los 13 microservicios del proyecto StyleRadar.
  Microservicio: PT8080-VirtualTryOn-Service (ms-probador-virtual)
  Squad Asignado: Portal8080 (PT8080)
============================================================================== -->

<!-- ==============================================================================
  STYLE RADAR · TIPOGRAFÍA Y SISTEMA DE DISEÑO OFICIAL
  Tipografía Oficial: Poppins (Manual de Identidad & Arquitectura Frontend)
  - Títulos Display (Black / Bold): Poppins 900 / 800 / 700
  - Subtítulos (SemiBold): Poppins 600
  - Cuerpo y Tablas (Regular): Poppins 400
  - Etiquetas y Badges (Medium): Poppins 500
  - Apoyo y Notas (Light): Poppins 300
  - Código y Terminal: JetBrains Mono
============================================================================== -->

<!-- ==============================================================================
  STYLE RADAR · RECURSOS 100% IMPORTADOS VÍA CDN / HTTPS (PORTABILIDAD TOTAL)
  Todos los logos, banners, fuentes y badges se importan desde la nube para que
  al copiar y pegar este README en cualquier repositorio nuevo se renderice
  a la perfección sin requerir archivos locales ni producir enlaces rotos.
============================================================================== -->

<div align="center">

  <a href="https://github.com/StyleRadar1">
    <img src="https://raw.githubusercontent.com/JUANNICOLASA/Frontend-Style-Radar/main/styleradar-frontend-v3/assets/logo/styleradar-logo.svg" width="340" alt="StyleRadar Logo Oficial" />
  </a>

  <h1>STYLE<font color="#5600EF">RADAR</font> &nbsp;·&nbsp; <code>ms-probador-virtual</code></h1>

  <p align="center">
    <img src="https://img.shields.io/badge/BOUNDED_CONTEXT-VIRTUAL__TRY__ON-12063A?style=for-the-badge" alt="Context" />
    <img src="https://img.shields.io/badge/PUERTO_OFICIAL-8500-5600EF?style=for-the-badge" alt="Port" />
    <img src="https://img.shields.io/badge/ARQUITECTURA-HEXAGONAL-16A34A?style=for-the-badge" alt="Hexagonal" />
    <img src="https://img.shields.io/badge/SQUAD-PORTAL8080-12063A?style=for-the-badge" alt="Squad" />
  </p>

  <p align="center">
    <strong><font color="#5600EF">Descubre tu estilo. En tu ciudad.</font></strong><br />
    <em>Orquestación asíncrona de pruebas virtuales con IA generativa, consentimiento biométrico bajo Ley 1581 y control de cuotas para StyleRadar Bogotá.</em>
  </p>

  <p align="center">
    <a href="docs/secciones/03-endpoints-y-enlace-a-swagger.md"><img src="https://img.shields.io/badge/DEV_LOCAL-Active-16A34A?style=for-the-badge&logo=docker&logoColor=white" alt="DEV Local Active" /></a>
    <a href="docs/secciones/03-endpoints-y-enlace-a-swagger.md"><img src="https://img.shields.io/badge/QA_CLOUD-Active-5600EF?style=for-the-badge&logo=azure-app-services&logoColor=white" alt="QA Cloud Active" /></a>
    <a href="docs/secciones/03-endpoints-y-enlace-a-swagger.md"><img src="https://img.shields.io/badge/PROD_CLOUD-Active-16A34A?style=for-the-badge&logo=azure-app-services&logoColor=white" alt="PROD Cloud Active" /></a>
    <a href="https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service/actions/workflows/ci.yml"><img src="https://img.shields.io/badge/CI_MAIN-passing-16A34A?style=for-the-badge&logo=githubactions&logoColor=white" alt="CI Main Status" /></a>
    <a href="https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service/actions/workflows/ci.yml"><img src="https://img.shields.io/badge/CI_DEVELOP-passing-16A34A?style=for-the-badge&logo=githubactions&logoColor=white" alt="CI Develop Status" /></a>
    <a href="docs/secciones/08-pruebas-y-cobertura.md"><img src="https://img.shields.io/badge/JaCoCo_Coverage-92%25-16A34A?style=for-the-badge&logo=codecov&logoColor=white" alt="Coverage" /></a>
    <a href="docs/secciones/09-cicd-pipeline.md"><img src="https://img.shields.io/badge/SonarQube-Quality_Gate_Passed-16A34A?style=for-the-badge&logo=sonarqube&logoColor=white" alt="SonarQube Quality Gate" /></a>
  </p>

  <p align="center">
    <a href="docs/secciones/05-tecnologias-y-dependencias.md">
      <img src="https://img.shields.io/badge/JAVA-21__LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
    </a>
    <a href="docs/secciones/05-tecnologias-y-dependencias.md">
      <img src="https://img.shields.io/badge/SPRING__BOOT-3.2.5-12063A?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot" />
    </a>
    <a href="docs/secciones/06-como-ejecutar-local-y-docker.md">
      <img src="https://img.shields.io/badge/DOCKER-OCI__CONTAINER-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker" />
    </a>
    <a href="docs/secciones/05-tecnologias-y-dependencias.md">
      <img src="https://img.shields.io/badge/POSTGRESQL-16.2-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL 16.2" />
    </a>
    <a href="docs/secciones/05-tecnologias-y-dependencias.md">
      <img src="https://img.shields.io/badge/REDIS-7.2-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis 7.2" />
    </a>
    <a href="docs/secciones/05-tecnologias-y-dependencias.md">
      <img src="https://img.shields.io/badge/RABBITMQ-3.13-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white" alt="RabbitMQ 3.13" />
    </a>
  </p>

</div>

---

## <img src="https://img.shields.io/badge/ÍNDICE-5600EF?style=for-the-badge" height="20" align="absmiddle" /> ÍNDICE DE CONTENIDOS

1. [Nombre del servicio y su bounded context](docs/secciones/01-nombre-del-servicio-y-bounded-context.md)
2. [Arquitectura hexagonal con sus diagramas](docs/secciones/02-arquitectura-hexagonal-y-diagramas.md)
3. [Endpoints y enlace a Swagger](docs/secciones/03-endpoints-y-enlace-a-swagger.md)
4. [Eventos que publica y consume](docs/secciones/04-eventos-que-publica-y-consume.md)
5. [Tecnologías y dependencias](docs/secciones/05-tecnologias-y-dependencias.md)
6. [Cómo ejecutar local y con Docker](docs/secciones/06-como-ejecutar-local-y-docker.md)
7. [Variables de entorno](docs/secciones/07-variables-de-entorno.md)
8. [Pruebas y cobertura (≥ 85%)](docs/secciones/08-pruebas-y-cobertura.md)
9. [CI/CD con el badge del pipeline](docs/secciones/09-cicd-pipeline.md)
10. [Ramas y cómo contribuir](docs/secciones/10-ramas-y-como-contribuir.md)
11. [Seguridad, Habeas Data y análisis OWASP Top 10](docs/secciones/11-seguridad-y-owasp.md)
12. [Equipo, responsables y enlace a la épica en Jira](docs/secciones/12-equipo-responsables-y-epica-jira.md)

---

## 13. REMEDIACIÓN Y AUDITORÍA TÉCNICA (PR #28 - FEEDBACK LÍDER TÉCNICO)

En atención a la revisión técnica del líder de equipo sobre el PR #28, se consolidaron los siguientes ajustes rigurosos de arquitectura y contratos:

* **BUG-032 (Retorno 404 en Fallos de Existencia de Prenda):** Unificación estricta del manejo de errores cuando una prenda no existe en el catálogo. Tanto el endpoint de validación (`GET /api/v1/try-on/garments/{garmentId}/validate`) como el de creación de trabajos (`POST /api/v1/try-on/jobs`) lanzan `GarmentNotFoundException` mapeada a HTTP 404 con código de error RFC 7807 `GARMENT_NOT_FOUND`.
* **BUG-009 (Diferenciación de Identificadores Malformados vs. Inexistentes):** Refactorización de `GarmentId.parseToUuid`. Cualquier cadena malformada o no conforme (ej. cadenas arbitrarias de texto) retorna `null` y genera HTTP 400 Bad Request (`INVALID_GARMENT`), diferenciándose limpiamente de un UUID sintácticamente válido pero no registrado en la base de datos (HTTP 404). Se mantienen de forma determinista los formatos históricos numéricos (`1001` y `styleradar-garment-1001`).
* **BUG-019 (Sanitización Específica de URL de Imágenes y Migración V11):** Corrección definitiva de la URL corrupta del inventario maestro `JUANNICOLASA%20Frontend-Style-Radar` $\rightarrow$ `JUANNICOLASA/Frontend-Style-Radar` tanto en el modelo de dominio `CatalogGarment.sanitizeImageUrl` como en el script de migración SQL Flyway `V11__sanitize_catalog_garment_image_urls.sql`, además de normalizar espacios a `%20`.
* **RabbitMQ e Idempotencia de Cuotas (Timestamp Versioning):** En `UserSubscriptionPlanEvent`, se preserva `timestamp == null` cuando el mensaje externo carece de fecha de ocurrencia. El servicio `QueryTryOnJobsServiceImpl` descarta eventos no versionados o desactualizados sin alterar el plan activo (`Radar+` o cuotas vigentes), protegiendo contra reentregas o mensajes desordenados.
* **BUG-031 (Trazabilidad y Observabilidad Saliente):** Inclusión de interceptores `ClientHttpRequestInterceptor` en `CatalogGarmentRestAdapter`, `CatalogGarmentMeasurementRestAdapter` y `UserMeasurementRestAdapter` para propagar automáticamente `X-Correlation-Id` y `X-Request-Id` desde el `MDC` hacia los microservicios externos (`ms-item-catalogo` y `ms-usuarios`).
* **RabbitMQ y Protección Defensiva de Cuotas:** En `UserSubscriptionPlanEvent`, se conserva `occurredAt == null` ante timestamps no válidos o vacíos (utilizando `parseFlexibleInstantOrNull`), garantizando que un evento desordenado, reentregado o sin fecha nunca sobreescriba ni degrade la suscripción vigente del usuario. Por su parte, `CatalogGarmentUpdatedEvent.parseFlexibleInstant` preserva el lanzamiento de `DateTimeParseException` ante fechas malformadas para evitar sobreescritura de proyecciones recientes de catálogo con marcas de tiempo artificiales.
* **Unificación de Contrato INVALID_GARMENT en POST:** El endpoint `POST /api/v1/try-on/jobs` rechaza identificadores de prenda malformados con `InvalidGarmentException` (HTTP 400 Bad Request, código RFC 7807 `INVALID_GARMENT`), unificándose con el contrato documentado y con el endpoint de validación.
* **Métricas y Evidencias de CI/CD:** Suite de pruebas actualizada con **386 pruebas totales** (384 aprobadas y 2 omitidas por requerir credenciales de integración real de GPU FASHN VTON), superando JaCoCo Quality Gate (> 93%) y SonarCloud en estado PASS ([Pipeline CI #129](https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service/actions/runs/129)).
* **Observabilidad Transversal Pendiente:** Se documenta a nivel de arquitectura la necesidad de articular de forma homogénea en los 14 servicios los exportadores OTLP (OpenTelemetry Collector), agregador de logs distribuido (Loki/Elasticsearch) y la recopilación de métricas de latencia de inferencia en el API Gateway.
