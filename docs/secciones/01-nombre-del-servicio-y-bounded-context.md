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
| • Orquesta la solicitud y ejecución asíncrona de pruebas virtuales de prendas sobre fotografías de usuarios (`SR-RF-23`, `SR-RF-24`, `SR-RF-25`). | • NO autentica usuarios ni emite tokens JWT (delegado a `ms-auth` en `:8081`). |
| • Valida el consentimiento explícito y la autorización de tratamiento de datos biométricos según Ley 1581 de 2012 / Habeas Data antes de procesar cualquier imagen (`SR-RNF-02`). | • NO gestiona el catálogo maestro de prendas ni administra tiendas (delegado a `ms-catalogo` en `:8084` e `ms-tiendas` en `:8082`). |
| • Gestiona y hace cumplir la cuota mensual y diaria de pruebas virtuales por usuario (`monthly_limit`, plan estándar vs. Radar+) con control en Redis y PostgreSQL. | • NO gestiona transacciones monetarias ni pasarelas de cobro (delegado a `ms-pagos` en `:8090`). |
| • Consulta y entrega los resultados de inferencia generados (URLs firmadas seguras de imagen renderizada con caducidad) (`SR-RF-26`). | • NO envía correos, SMS ni notificaciones push directas al usuario final (delegado a `ms-notificaciones` en `:8091`). |
| • Garantiza el derecho al olvido y la eliminación definitiva de fotografías de silueta y resultados renderizados a solicitud del usuario (`SR-RF-27`). | • NO accede directamente a bases de datos de otros microservicios bajo el principio *Database-per-Service*. |

### Arquetipos de Moda y Personajes de StyleRadar

Este microservicio da vida al arquetipo **Aura (Futuro)**, la embajadora de innovación tecnológica, vanguardia y probador virtual asistido por IA dentro del ecosistema StyleRadar.

---

[⬅ Volver al README Principal](../../README.md)
