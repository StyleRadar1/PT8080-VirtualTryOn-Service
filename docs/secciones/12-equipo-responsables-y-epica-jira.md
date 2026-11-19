[⬅ Volver al README Principal](../../README.md)

---

## 12. EQUIPO, RESPONSABLES Y ENLACE A LA ÉPICA EN JIRA

### Equipo y Responsables del Microservicio

Desarrollado y mantenido por el **Squad Portal8080 (`PT8080`)** para la asignatura DOSW 2026.

<div align="center">
  <img src="../images/portal8080-bus-logo.png" alt="Logo Oficial Squad Portal8080" width="220" style="border-radius: 10px; margin-bottom: 12px;" />

| Rol | Nombre Completo | Usuario GitHub |
| :---: | :---: | :---: |
| **Líder del Equipo** | Daniel Valero Quiroga | [@DanielValeroQ](https://github.com/DanielValeroQ) |
| **Desarrollador Backend** | Jose Alejandro Martinez Arias | [@JoseMartinez883](https://github.com/JoseMartinez883) |
| **Desarrollador Backend** | Juan David Munar | [@JuanDavidMunar](https://github.com/JuanDavidMunar) |
| **Desarrollador Frontend** | Kevin | [@Kevin](https://github.com/StyleRadar1) |

</div>

---

### Enlaces de Gestión, Repositorios y Evidencias

El seguimiento de requerimientos, historias de usuario del Sprint y artefactos del proyecto se centraliza en los siguientes enlaces oficiales:

* **Repositorio Oficial en GitHub:** [https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service](https://github.com/StyleRadar1/PT8080-VirtualTryOn-Service)
* **Organización Oficial de StyleRadar:** [https://github.com/StyleRadar1](https://github.com/StyleRadar1)
* **Épica Oficial del Microservicio (Jira):** [https://mail-team-nyjtgqcj.atlassian.net/browse/PT8080-1?atlOrigin=eyJpIjoiMWE0NjAzMTdkODI5NDFmN2I2ODdhOGYzNjJjMTU4ZTkiLCJwIjoiaiJ9](https://mail-team-nyjtgqcj.atlassian.net/browse/PT8080-1?atlOrigin=eyJpIjoiMWE0NjAzMTdkODI5NDFmN2I2ODdhOGYzNjJjMTU4ZTkiLCJwIjoiaiJ9)
* **Tablero Scrum del Squad (Jira):** [https://mail-team-nyjtgqcj.atlassian.net/jira/software/projects/PT8080/boards/74/backlog?epics=visible&selectedIssue=PT8080-38&atlOrigin=eyJpIjoiMjRlZW](https://mail-team-nyjtgqcj.atlassian.net/jira/software/projects/PT8080/boards/74/backlog?epics=visible&selectedIssue=PT8080-38&atlOrigin=eyJpIjoiMjRlZW)
* **Swagger UI Local (Puerto 8500):** [http://localhost:8500/swagger-ui/index.html](http://localhost:8500/swagger-ui/index.html)
* **Especificación OpenAPI JSON:** [http://localhost:8500/v3/api-docs](http://localhost:8500/v3/api-docs)

<div align="center">
  <img src="../images/JiraEvidence.png" alt="Evidencia 05 - Épica PT8080-1 en Jira" width="100%" style="border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);" />
  <p><sub><b>Figura 12.1:</b> Épica oficial <code>PT8080-1</code> en Atlassian Jira para <code>ms-probador-virtual</code> (puerto 8500), detallando los requerimientos funcionales SR-RF-23 al 27, RNF asociados y el desglose de historias de usuario y tareas técnicas del Sprint para el probador virtual asistido por IA.</sub></p>
</div>

### Requerimientos Funcionales Asignados

| Código RF | Nombre del Requerimiento | Actor Principal | Criterio de Aceptación Clave |
| :----------: | :---------------------------------------------------------------------- | :----------------- | :---------------------------------------------------------------------------------------------------------------- |
| `SR-RF-23` | Carga de silueta de usuario con consentimiento biométrico | Usuario Registrado | La imagen debe cargarse en formato JPG/PNG con autorización explícita de Habeas Data (Ley 1581 de 2012). |
| `SR-RF-24` | Selección de prenda de vestir para prueba virtual | Usuario Registrado | Permite vincular una prenda activa del catálogo de StyleRadar indicando su identificador y categoría. |
| `SR-RF-25` | Generación y procesamiento asíncrono de prueba virtual | Usuario Registrado | Encola la solicitud de inferencia retornando HTTP 202 con cabecera `Location` y valida la cuota máxima diaria. |
| `SR-RF-26` | Visualización del resultado renderizado con control de caducidad | Usuario Registrado | Entrega la URL segura del renderizado generado por el motor de IA una vez el job alcanza el estado completado. |
| `SR-RF-27` | Eliminación irreversible de fotografía de silueta (Derecho al Olvido) | Usuario Registrado | Garantiza el borrado físico de la fotografía y del resultado renderizado respondiendo HTTP 204 No Content. |

---

[⬅ Volver al README Principal](../../README.md)
