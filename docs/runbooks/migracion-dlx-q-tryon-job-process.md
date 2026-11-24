# Runbook Operativo: Migración de `q.tryon.job.process` hacia `x-dead-letter-exchange=styleradar.dlx` sin pérdida de jobs

## 1. Contexto del cambio (PR #25)

En el commit `82cc582`, la cola durable `q.tryon.job.process` conserva su nombre, pero actualiza su argumento de dead-lettering:

- **Configuración anterior:** `x-dead-letter-exchange = styleradar.tryon.dlx` (`topic`, no autorizado para `ms_probador` en QA)
- **Configuración actual (alineada con los demás servicios):** `x-dead-letter-exchange = styleradar.dlx` (`direct`), con `x-dead-letter-routing-key = tryon.job.process.dead` hacia la cola durable `q.tryon.job.process.dlq`

En RabbitMQ, `x-dead-letter-exchange` es un argumento inmutable de la cola. Si la cola ya existía en el broker con `styleradar.tryon.dlx`, la redeclaración falla con `406 PRECONDITION_FAILED` y, gracias a `RabbitAdmin.setIgnoreDeclarationExceptions(true)`, la aplicación arranca sin caerse, pero RabbitMQ conserva la definición anterior de la cola mientras no se recree.

---

## 2. Estado verificado de los ambientes desplegados (QA y PROD)

| Ambiente | App Service / Commit | Host RabbitMQ configurado | Listeners AMQP | Estado verificado de `q.tryon.job.process` | ¿Requiere migración previa? |
|---|---|---|---|---|---|
| **QA** | `styleradar-virtualtryon-qa` (`c8ab9bd`) | `styleradar-rabbitmq-qa.northcentralus.cloudapp.azure.com:5671` | `TRYON_AMQP_LISTENERS_ENABLED=true` | **No existe en el broker.** El log real del contenedor (`2026-11-24T02:47:42Z`) registra `reply-code=404, reply-text=NOT_FOUND - no queue 'q.tryon.job.process' in vhost '/'`, porque RabbitAdmin abortaba antes con `403 ACCESS_REFUSED` sobre `styleradar.tryon.dlx`. | **No.** Al desplegar PR #25 se creará directamente con `styleradar.dlx`. |
| **PROD** | `styleradar-virtualtryon-prod` (`6efaf03`) | `styleradar-rabbitmq-qa.northcentralus.cloudapp.azure.com:5671` | `TRYON_AMQP_LISTENERS_ENABLED=false` | Comparte el mismo host RabbitMQ de QA y tiene los listeners AMQP deshabilitados en App Settings; su log de arranque (`2026-11-23T19:56–19:59Z`) no declara colas ni presenta errores AMQP. | **No.** Cuando se apunte a un broker propio de PROD o se habiliten listeners tras el merge de PR #25, nacerá con `styleradar.dlx`. |

> **Nota de acceso al broker compartido de QA:** Los puertos del Management UI/API (`15671` / `15672`) están bloqueados externamente por firewall de red, por lo que la verificación remota en QA se apoya en la respuesta AMQP del broker (`404 NOT_FOUND` en logs). Antes de habilitar producción en un broker dedicado o si un administrador de plataforma revisa internamente el nodo, debe confirmar la ausencia o argumentos reales con el comando de la sección 3.

---

## 3. Verificación previa de argumentos (si existe acceso interno o SSH al broker)

Con `rabbitmqctl` dentro del nodo RabbitMQ:

```bash
rabbitmqctl list_queues -p / name messages consumers arguments
```

O mediante HTTP Management API (donde esté habilitada):

```bash
curl -s -u "<ADMIN_USER>:<ADMIN_PASSWORD>" \
  "http://<RABBIT_HOST>:15672/api/queues/%2F/q.tryon.job.process" | jq '{name, messages, arguments}'
```

- Si responde **no encontrada** o si `"x-dead-letter-exchange": "styleradar.dlx"` y `"x-dead-letter-routing-key": "tryon.job.process.dead"`, **no se requiere migración** y el despliegue entra directo.
- Si muestra `"x-dead-letter-exchange": "styleradar.tryon.dlx"`, ejecutar el procedimiento de la sección 4 **preservando los jobs pendientes** antes o durante la ventana de despliegue.

---

## 4. Procedimiento de migración preservando jobs pendientes

> **Garantía adicional de la aplicación:** Todos los jobs creados en `PT8080-VirtualTryOn-Service` nacen primero persistidos en la tabla PostgreSQL `outbox_events` (*Transactional Outbox*), y además el drenaje AMQP de este procedimiento garantiza **cero pérdida** para jobs que ya estaban en tránsito dentro de `q.tryon.job.process`.

### Paso 1. Pausar consumidores en el servicio desplegado
Establecer temporalmente `TRYON_AMQP_LISTENERS_ENABLED=false` (o escalar a 0 consumidores) para evitar carreras mientras se vacía `q.tryon.job.process`:

```bash
az webapp config appsettings set -g styleradar-pt8080-rg -n <APP_SERVICE_NAME> \
  --settings TRYON_AMQP_LISTENERS_ENABLED=false
```

*(Los jobs nuevos quedan pendientes de despacho seguro en PostgreSQL `outbox_events`, o serán recibidos por la cola temporal en el Paso 2).*

### Paso 2. Crear cola temporal `q.tryon.job.process.migration.tmp` y desviar nuevos mensajes
1. Declarar la cola durable temporal `q.tryon.job.process.migration.tmp` (mismo prefijo `q.tryon.*`).
2. Enlazar `q.tryon.job.process.migration.tmp` al exchange `styleradar.exchange` con la routing key `styleradar.tryon.job.created`.
3. Desenlazar `q.tryon.job.process` de `styleradar.exchange` (`routing_key = styleradar.tryon.job.created`) para que deje de recibir nuevos eventos.

### Paso 3. Drenar todos los jobs pendientes de `q.tryon.job.process` hacia la cola temporal
Usar un traslado con confirmación de publicación (`publisher confirms`) **antes** del `basicAck` en origen (por ejemplo vía plugin **RabbitMQ Shovel** temporal con `ack-mode: on-confirm`, o el cliente AMQP ejecutando `basicGet(autoAck=false)` + `basicPublish("", "q.tryon.job.process.migration.tmp", persistent, body)` + `waitForConfirmsOrDie` + `basicAck`):

```bash
# Ejemplo con Shovel en Management API (opcional si shovel está habilitado):
curl -s -u "<USER>:<PASS>" -X PUT \
  "http://<RABBIT_HOST>:15672/api/parameters/shovel/%2F/migrate-tryon-to-tmp" \
  -H "content-type: application/json" \
  -d '{
    "value": {
      "src-protocol": "amqp091",
      "src-uri": "amqp:///",
      "src-queue": "q.tryon.job.process",
      "src-delete-after": "queue-length",
      "ack-mode": "on-confirm",
      "dest-protocol": "amqp091",
      "dest-uri": "amqp:///",
      "dest-queue": "q.tryon.job.process.migration.tmp"
    }
  }'
```

Verificar antes del siguiente paso que `q.tryon.job.process` tenga **`messages = 0`** y **`messages_unacknowledged = 0`**:

```bash
rabbitmqctl list_queues -p / name messages messages_ready messages_unacknowledged arguments
```

### Paso 4. Recrear `q.tryon.job.process` con `x-dead-letter-exchange=styleradar.dlx`
1. Eliminar la cola antigua **exigiendo que esté vacía** (`--if-empty` / `queueDelete(..., ifUnused=false, ifEmpty=true)` para impedir borrados accidentales si hubiera un mensaje residual):
   ```bash
   rabbitmqctl delete_queue -p / q.tryon.job.process --if-empty
   ```
2. Asegurar la existencia del exchange `styleradar.dlx` (`direct`, durable) y de la cola `q.tryon.job.process.dlq` enlazada a `styleradar.dlx` con routing key `tryon.job.process.dead`.
3. Reactivar los listeners / desplegar la nueva versión del PR #25 (`TRYON_AMQP_LISTENERS_ENABLED=true`), o declarar explícitamente la nueva `q.tryon.job.process` durable con:
   - `x-dead-letter-exchange`: `styleradar.dlx`
   - `x-dead-letter-routing-key`: `tryon.job.process.dead`
   - Binding desde `styleradar.exchange` con routing key `styleradar.tryon.job.created`.
4. Desenlazar `q.tryon.job.process.migration.tmp` de `styleradar.exchange`.

### Paso 5. Reinyectar los jobs preservados y limpiar la cola temporal
Mover los mensajes desde `q.tryon.job.process.migration.tmp` directamente hacia `q.tryon.job.process` (exchange `""`, routing key `q.tryon.job.process`, con `on-confirm` / `waitForConfirmsOrDie` antes de `basicAck`), verificar que `q.tryon.job.process.migration.tmp` quedó en `0` y eliminarla con `--if-empty`:

```bash
rabbitmqctl delete_queue -p / q.tryon.job.process.migration.tmp --if-empty
```

### Paso 6. Validación final
1. Confirmar que `q.tryon.job.process` reporta:
   - `x-dead-letter-exchange = styleradar.dlx`
   - `x-dead-letter-routing-key = tryon.job.process.dead`
2. Confirmar que los jobs preservados fueron procesados y que un rechazo permanente (`AmqpRejectAndDontRequeueException`, `defaultRequeueRejected=false`) aterriza en `q.tryon.job.process.dlq` con `exchange = styleradar.dlx` y `routingKey = tryon.job.process.dead`.

---

## 5. Prueba automatizada en broker real (Docker / Testcontainers)

Todo este flujo de actualización (arranque con `styleradar.tryon.dlx` + jobs pendientes en cola → reproducción de `PRECONDITION_FAILED` → migración sin pérdida → verificación en RabbitMQ Management API / `rabbitmqctl` → procesamiento FIFO de jobs preservados + enrutamiento a DLQ vía `styleradar.dlx` / `tryon.job.process.dead`) está cubierto de forma reproducible en:

- `src/test/java/edu/dosw/styleradar/virtualtryon/infrastructure/adapter/in/amqp/TryOnJobRabbitUpgradeMigrationIntegrationTest.java`
