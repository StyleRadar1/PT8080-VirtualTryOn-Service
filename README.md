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
