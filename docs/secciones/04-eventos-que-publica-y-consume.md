[⬅ Volver al README Principal](../../README.md)

---

## 4. EVENTOS QUE PUBLICA Y CONSUME

La comunicación asíncrona entre microservicios se realiza a través de **RabbitMQ 3.13** utilizando Topic Exchanges durables.

* **Exchange Principal de Dominio:** `styleradar.events` (`topic`, durable)
* **Exchange de Try-On:** `styleradar.tryon.events` (`topic`, durable)
* **Colas Asíncronas y DLQ:** `q.tryon.*` + `tryon.dlq` (`styleradar.dlx`)

### Matriz de Eventos AMQP

| Tipo | Routing Key | Cola (Queue) | Microservicio Emisor / Receptor | Cuándo se Dispara / Acción |
| :---------------: | :-------------------------- | :--------------------------------- | :--------------------------------------------- | :------------------------------------------------------------------------------------ |
| **PUBLICA** | `tryon.job.created` | `q.tryon.jobs` | `ms-probador-virtual` &rarr; Bus | Se publica cuando un job de prueba virtual es encolado satisfactoriamente. |
| **PUBLICA** | `tryon.job.completed` | `q.tryon.completed` | `ms-probador-virtual` &rarr; Bus | Se emite cuando el motor de IA finaliza la renderización de la imagen. |
| **PUBLICA** | `tryon.job.failed` | `q.tryon.failed` | `ms-probador-virtual` &rarr; Bus | Se emite si la silueta no cumple parámetros antropométricos o falla el motor de IA. |
| **PUBLICA** | `user.photo.deleted` | `q.tryon.privacy` | `ms-probador-virtual` &rarr; Bus | Notifica a storage y auditoría la eliminación irreversible de la foto biométrica. |
| **CONSUME** | `catalog.garment.updated` | `q.tryon.garment-updated` | `ms-item-catalogo` &rarr; `ms-probador-virtual` | Invalida cachés de imágenes de prendas modificadas o descontinuadas. |
| **CONSUME** | `user.account.deleted` | `q.tryon.user-deleted` | `ms-auth` &rarr; `ms-probador-virtual` | Purga de forma inmediata todas las siluetas e históricos de jobs del usuario. |

<details>
<summary><b>Click para ver Esquema JSON del Evento Publicado (con Idempotencia y UUIDv7)</b></summary>

<br>

```json
{
  "eventId": "018e3a2b-4444-7000-8000-000000000001",
  "eventType": "tryon.job.completed",
  "timestamp": "2026-11-02T12:00:30Z",
  "producer": "ms-probador-virtual",
  "correlationId": "corr-uuid-7777",
  "data": {
    "jobId": "018e3a2b-3333-7000-8000-000000000001",
    "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "garmentId": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
    "resultImageUrl": "https://styleradarblob.blob.core.windows.net/renders/render-018e3a2b.jpg",
    "status": "COMPLETED",
    "executionTimeMs": 2100
  }
}
```

</details>

---

[⬅ Volver al README Principal](../../README.md)
