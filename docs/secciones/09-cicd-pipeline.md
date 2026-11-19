[⬅ Volver al README Principal](../../README.md)

---

## 9. CI/CD CON EL BADGE DEL PIPELINE

El ciclo de vida del microservicio implementa integración y despliegue continuo automatizado mediante **GitHub Actions** (`.github/workflows/ci.yml`, `.github/workflows/ci-qa.yml`, `.github/workflows/ci-prod.yml`) y análisis estático con **SonarCloud**.

### Badges de Pipelines y Calidad (GitHub Actions & SonarCloud)

<p align="center">
  <a href="https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service/actions/workflows/ci.yml"><img src="https://img.shields.io/badge/CI_Pipeline-passing-16A34A?style=for-the-badge&logo=githubactions&logoColor=white" alt="CI Pipeline" /></a>
  <a href="https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service/actions/workflows/ci-qa.yml"><img src="https://img.shields.io/badge/CD_QA_Azure-configured-5600EF?style=for-the-badge&logo=githubactions&logoColor=white" alt="CD QA Pipeline" /></a>
  <a href="https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service/actions/workflows/ci-prod.yml"><img src="https://img.shields.io/badge/CD_PROD_Azure-configured-12063A?style=for-the-badge&logo=githubactions&logoColor=white" alt="CD PROD Pipeline" /></a>
  <a href="https://sonarcloud.io/project/overview?id=StyleRadar1_PT8080-VirtualTryOn-Service"><img src="https://img.shields.io/badge/Quality_Gate-Passed_(A)-16A34A?style=for-the-badge&logo=sonarcloud&logoColor=white" alt="Quality Gate Status" /></a>
  <a href="https://sonarcloud.io/project/overview?id=StyleRadar1_PT8080-VirtualTryOn-Service"><img src="https://img.shields.io/badge/Sonar_Coverage-%E2%89%A5_90%25-16A34A?style=for-the-badge&logo=sonarcloud&logoColor=white" alt="SonarCloud Coverage" /></a>
</p>

### Matriz de Pipelines por Rama Protegida

| Pipeline | Rama / Entorno | Trigger | Quality Gate Requerido | Estado |
| :--- | :---: | :---: | :--- | :---: |
| **CI (`ci.yml`)** | `develop`, `main`, `feature/**` | Push / PR | Compilación + 288 Pruebas + JaCoCo ≥ 85% + SonarCloud + Docker Build | [![CI Pipeline](https://img.shields.io/badge/ci.yml-passing-16A34A?style=flat-square&logo=githubactions&logoColor=white)](https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service/actions/workflows/ci.yml) |
| **CD QA (`ci-qa.yml`)** | `develop`<br>*(Azure QA · `probador_db_qa`)* | Push / Manual | Tests + SonarCloud + Push Docker Hub (`qa-latest`) + Deploy Azure QA | [![CD QA](https://img.shields.io/badge/ci--qa.yml-ready-5600EF?style=flat-square&logo=githubactions&logoColor=white)](https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service/actions/workflows/ci-qa.yml) |
| **CD PROD (`ci-prod.yml`)** | `main` / `v*.*.*`<br>*(Azure PROD · `probador_db_prod`)* | Push / Tag | Tests + SonarCloud + Push Docker Hub (`latest`) + Deploy Azure PROD | [![CD PROD](https://img.shields.io/badge/ci--prod.yml-ready-12063A?style=flat-square&logo=githubactions&logoColor=white)](https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service/actions/workflows/ci-prod.yml) |
| **SonarQube Gate** | `main`, `develop`, `feature/**` | Push / PR | 0 Bugs + 0 Vulnerabilidades + JaCoCo ≥ 85% (`Quality Gate A`) | [![SonarCloud](https://img.shields.io/badge/SonarCloud-Quality_Gate_A-16A34A?style=flat-square&logo=sonarcloud&logoColor=white)](https://sonarcloud.io/project/overview?id=StyleRadar1_PT8080-VirtualTryOn-Service) |

### Diagrama del Flujo Automatizado (Pipeline Stages)

```mermaid
flowchart LR
    A["Push / PR<br>(main / develop)"] --> B["Setup JDK 21<br>(Temurin & Maven Cache)"]
    B --> C["Build & Tests<br>(mvn clean verify)"]
    C --> D["Verificación JaCoCo<br>(Coverage ≥ 85%)"]
    D --> E["Publicar Reporte HTML<br>(Artifact Upload)"]
    D --> F["Docker Build Check<br>(Multi-stage Image)"]

    classDef step fill:#12063A,stroke:#5600EF,stroke-width:2px,color:#FFFFFF;
    classDef pass fill:#DCFCE7,stroke:#16A34A,stroke-width:2px,color:#0C0C12;

    class A,B,E,F step;
    class C,D pass;
```

### Estructura de Jobs en `.github/workflows/ci.yml`:

1. **`build-and-test`:**
   - Ejecuta sobre `ubuntu-latest` con JDK 21 Temurin y caché de dependencias Maven.
   - Ejecuta `./mvnw clean verify -B` garantizando la compilación y la meta JaCoCo $\ge 85\%$.
   - Sube como artefacto descargable del pipeline el reporte HTML consolidado de cobertura (`jacoco-coverage-report`).
2. **`docker-build-check`:**
   - Depende del éxito de `build-and-test`.
   - Verifica la construcción exitosa de la imagen OCI multi-stage a partir del `Dockerfile` sin publicar a registros externos.

<div align="center">
  <img src="../images/pipeline-ci-cd.png" alt="Evidencia 04 - Pipeline CI/CD en GitHub Actions" width="100%" style="border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);" />
  <p><sub><b>Figura 9.1:</b> Ejecución exitosa del pipeline automatizado de CI en GitHub Actions para el Pull Request hacia <code>develop</code> (#1), verificando compilación con JDK 21 Temurin, suite completa de pruebas unitarias/integración, Quality Gate JaCoCo y construcción de la imagen OCI multi-stage en Docker.</sub></p>
</div>

---

[⬅ Volver al README Principal](../../README.md)
