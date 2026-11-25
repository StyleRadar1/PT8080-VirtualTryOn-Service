[⬅ Volver al README Principal](../../README.md)

---

## 4. EVENTOS QUE PUBLICA Y CONSUME

La comunicación asíncrona entre microservicios se realiza a través de **RabbitMQ 3.13** utilizando un Topic Exchange durable y un Dead Letter Exchange directo (`RabbitConfig.java`).

* **Exchange Principal de Dominio:** `styleradar.exchange` (`topic`, durable)
* **Dead Letter Exchange (DLX):** `styleradar.dlx` (`direct`, durable)
* **Colas de Procesamiento y Sincronización:** `q.tryon.job.process` y `styleradar.probador.*.q` con sus respectivas DLQs

### Matriz de Eventos AMQP

| Tipo | Routing Key | Cola (Queue) / DLQ | Microservicio Emisor / Receptor | Cuándo se Dispara / Acción |
| :---------------: | :---------------------------------------- | :------------------------------------------------------------------------------------------------- | :-------------------------------------------------- | :------------------------------------------------------------------------------------------- |
| **PUBLICA** | `styleradar.tryon.job.created` | `q.tryon.job.process` (DLQ: `q.tryon.job.process.dlq`, rk `tryon.job.process.dead`) | `ms-probador-virtual` &rarr; `ms-probador-virtual` / Bus | Trabajo de try-on encolado vía Transactional Outbox para ser procesado por el worker asíncrono. |
| **PUBLICA** | `styleradar.tryon.job.completed` | *(Exchange `styleradar.exchange`)* | `ms-probador-virtual` &rarr; Bus | Se emite cuando el motor de IA finaliza el renderizado (no propaga URLs SAS temporales). |
| **PUBLICA** | `styleradar.tryon.job.failed` | *(Exchange `styleradar.exchange`)* | `ms-probador-virtual` &rarr; Bus | Se emite si falla definitivamente la inferencia de IA o la validación asíncrona. |
| **PUBLICA** | `styleradar.tryon.photo.purged` | *(Exchange `styleradar.exchange`)* | `ms-probador-virtual` &rarr; Bus | Notifica a auditoría la eliminación irreversible de silueta y renderizados (Ley 1581). |
| **CONSUME** | `styleradar.tryon.job.created` | `q.tryon.job.process` (DLQ: `q.tryon.job.process.dlq`) | `ms-probador-virtual` &rarr; `ms-probador-virtual` | Worker asíncrono (`TryOnJobProcessConsumer`) que orquesta la inferencia VTON. |
| **CONSUME** | `styleradar.catalogo.prenda.publicada` | `styleradar.probador.prenda-publicada.q` (DLQ: `styleradar.probador.dlq`) | `ms-item-catalogo` &rarr; `ms-probador-virtual` | Sincroniza prendas publicadas o actualizadas en la réplica local `catalog_garments`. |
| **CONSUME** | `styleradar.catalogo.prenda.retirada` | `styleradar.probador.prenda-retirada.q` (DLQ: `styleradar.probador.prenda-retirada.dlq`) | `ms-item-catalogo` &rarr; `ms-probador-virtual` | Marca como no visible una prenda retirada o eliminada del catálogo. |
| **CONSUME** | `styleradar.catalogo.moderacion.cambiada` | `styleradar.probador.moderacion-cambiada.q` (DLQ: `styleradar.probador.moderacion-cambiada.dlq`) | `ms-item-catalogo` &rarr; `ms-probador-virtual` | Actualiza el estado de moderación y elegibilidad de la prenda. |
| **CONSUME** | `styleradar.usuarios.cuenta.eliminada` | `styleradar.probador.cuenta-eliminada.q` (DLQ: `styleradar.probador.cuenta-eliminada.dlq`) | `ms-usuarios` &rarr; `ms-probador-virtual` | Purga de forma inmediata consentimientos, siluetas y jobs del usuario (Ley 1581). |
| **CONSUME** | `styleradar.pagos.pago.confirmado` | `styleradar.probador.pago-confirmado.q` (DLQ: `styleradar.probador.pago-confirmado.dlq`) | `ms-pagos-suscripciones` &rarr; `ms-probador-virtual` | Actualiza la cuota/plan del usuario al confirmarse el pago de suscripción. |
| **CONSUME** | `styleradar.pagos.suscripcion.suspendida` | `styleradar.probador.suscripcion-suspendida.q` (DLQ: `styleradar.probador.suscripcion-suspendida.dlq`) | `ms-pagos-suscripciones` &rarr; `ms-probador-virtual` | Restablece la cuota al plan base cuando la suscripción es suspendida. |

<details>
<summary><b>Click para ver Esquema JSON del Evento Publicado (TryOnJobDomainEvent vía Outbox)</b></summary>

<br>

```json
{
  "eventId": "018e3a2b-4444-7000-8000-000000000001",
  "eventType": "styleradar.tryon.job.completed",
  "occurredAt": "2026-11-02T12:00:30Z",
  "jobId": "018e3a2b-3333-7000-8000-000000000001",
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "garmentId": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
  "status": "COMPLETED",
  "resultImageUrl": null,
  "errorMessage": null,
  "executionDurationMs": 2100
}
```

</details>

---

[⬅ Volver al README Principal](../../README.md)
