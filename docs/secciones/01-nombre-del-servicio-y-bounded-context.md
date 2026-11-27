[⬅ Volver al README Principal](../../README.md)

---

## 1. NOMBRE DEL SERVICIO Y SU BOUNDED CONTEXT

* **Identificador de Servicio:** `PT8080-VirtualTryOn-Service` (`ms-probador-virtual`)
* **Paquete Arquitectónico:** Paquete 5: Experiencia y Probador Virtual AI
* **Puerto de Escucha Oficial:** `8500` (asignado en la matriz sin colisiones de StyleRadar)
* **Base de Datos Asignada:** `PostgreSQL 16` (`probador_db` en puerto host `5442`), aislada bajo el patrón *Database-per-Service*.
* **Caché en Memoria:** `Redis 7.2` (puerto host `6389`) para control de cuotas y limitación de tasa.
* **Bounded Context:** `Virtual Try-On (Probador Virtual con IA)`

### Responsabilidades del Dominio

Este microservicio encapsula exclusivamente la lógica de negocio correspondiente a la orquestación asíncrona de pruebas virtuales de ropa con inteligencia artificial generativa sobre la silueta del usuario.

| Qué HACE este Microservicio (Alcance) | Qué NO HACE este Microservicio (Límites) |
| :------------------------------------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------- |
| • Carga y valida fotografías frontales para el probador virtual (`SR-RF-23`). | • NO autentica usuarios ni emite tokens JWT (delegado a `ms-auth` en `:8081`). |
| • Gestiona y audita el consentimiento biométrico informado según Ley 1581 de 2012 / Habeas Data (`SR-RF-107`, `SR-RNF-02`). | • NO gestiona el catálogo maestro de prendas ni administra tiendas (delegado a `ms-catalogo` en `:8084` e `ms-tiendas` en `:8082`). |
| • Selecciona prendas del catálogo y valida compatibilidad por zona corporal y ajuste orientativo de talla (`SR-RF-24`). | • NO gestiona transacciones monetarias ni pasarelas de cobro (delegado a `ms-pagos` en `:8090`). |
| • Consulta y administra cuotas mensuales y disponibles de pruebas virtuales en PostgreSQL y Redis (`SR-RF-108`). | • NO envía correos, SMS ni notificaciones push directas al usuario final (delegado a `ms-notificaciones` en `:8091`). |
| • Genera visualizaciones aproximadas de prueba virtual orquestando inferencia asíncrona (`202 Accepted` + `jobId`) (`SR-RF-25`). | • NO calcula recomendaciones de estilo ni feeds sociales (delegado a `ms-recomendaciones` en `:8087`). |
| • Consulta y entrega resultados renderizados con URLs firmadas SAS seguras de caducidad controlada (`SR-RF-26`). | • NO almacena datos maestros de usuarios ni credenciales (delegado a `ms-usuarios` / `ms-auth`). |
| • Consulta la lista cronológica (historial) de pruebas virtuales del usuario (`SR-RF-109`). | • NO realiza inferencia de IA en el hilo HTTP sincrónico (delegado a worker asíncrono / proveedor FASHN AI). |
| • Elimina fotografías biométricas y trabajos renderizados garantizando Derecho al Olvido (`SR-RF-27`). | • NO accede directamente a bases de datos de otros microservicios bajo el principio *Database-per-Service*. |

### Catálogo de Requerimientos Funcionales Oficiales (Versión 5.0)

El microservicio `ms-probador-virtual` implementa estrictamente los 8 requerimientos funcionales oficiales formalizados en la especificación v5.0:

1. **`SR-RF-23`**: *Cargar fotografía frontal para el probador virtual* (`POST /api/v1/try-on/photos`).
2. **`SR-RF-107`**: *Gestionar consentimiento biométrico del usuario* (`POST`, `GET`, `DELETE /api/v1/try-on/consents/{userId}`).
3. **`SR-RF-24`**: *Seleccionar prenda y validar compatibilidad de talla para el probador virtual* (`GET /api/v1/try-on/garments/{garmentId}/validate`).
4. **`SR-RF-108`**: *Consultar cuotas disponibles del probador virtual* (`GET /api/v1/try-on/quotas`).
5. **`SR-RF-25`**: *Generar visualización aproximada de la prenda* (`POST /api/v1/try-on/jobs` $\rightarrow$ `202 Accepted`).
6. **`SR-RF-26`**: *Mostrar resultado del probador virtual* (`GET /api/v1/try-on/jobs/{jobId}`).
7. **`SR-RF-109`**: *Consultar historial de pruebas virtuales del usuario* (`GET /api/v1/try-on/jobs`).
8. **`SR-RF-27`**: *Eliminar imagen del probador virtual* (`DELETE /api/v1/try-on/personal-photos/{jobId}` $\rightarrow$ `204 No Content`).

### Arquetipos de Moda y Personajes de StyleRadar

Este microservicio da vida al arquetipo **Aura (Futuro)**, la embajadora de innovación tecnológica, vanguardia y probador virtual asistido por IA dentro del ecosistema StyleRadar.

---

[⬅ Volver al README Principal](../../README.md)
