# Runbook Operativo: Migración de `q.tryon.job.process` hacia `x-dead-letter-exchange=styleradar.dlx` sin pérdida ni duplicación de jobs

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
rabbitmqctl list_queues -p / name consumers messages messages_ready messages_unacknowledged arguments
```

O mediante HTTP Management API (donde esté habilitada):

```bash
curl -s -u "<ADMIN_USER>:<ADMIN_PASSWORD>" \
  "http://<RABBIT_HOST>:15672/api/queues/%2F/q.tryon.job.process" | jq '{name, consumers, messages, arguments}'
```

- Si responde **no encontrada** o si `"x-dead-letter-exchange": "styleradar.dlx"` y `"x-dead-letter-routing-key": "tryon.job.process.dead"`, **no se requiere migración** y el despliegue entra directo.
- Si muestra `"x-dead-letter-exchange": "styleradar.tryon.dlx"`, ejecutar el procedimiento de la sección 4 **pausando el Outbox y los consumidores** para preservar los jobs existentes y evitar duplicados si entran nuevas solicitudes durante la ventana.

---

## 4. Procedimiento de migración preservando jobs pendientes (sin réplicas antiguas ni doble binding)

> **Invariantes clave:**
> 1. **Desplegar antes de desenlazar:** Las versiones anteriores al PR #25 no reconocen `OUTBOX_DISPATCH_ENABLED`. Por ello, primero se despliega la imagen del PR #25 con la pausa configurada en **todas** las réplicas, slots y ambientes que comparten el mismo broker/vhost, **manteniendo `q.tryon.job.process` enlazada** hasta confirmar que no queda ninguna réplica antigua viva publicando ni envíos en curso.
> 2. **Sin doble binding:** Nunca coexisten `q.tryon.job.process` y la cola temporal enlazadas a la routing key `styleradar.tryon.job.created`. Con el Outbox pausado (`OUTBOX_DISPATCH_ENABLED=false`), las solicitudes HTTP nuevas (`POST /api/v1/try-on/jobs`) siguen persistiendo en PostgreSQL (`outbox_events`, `status = 'PENDING'`) hasta que `q.tryon.job.process` ya esté recreada, restaurada y enlazada en solitario.

### Paso 1. Registrar los valores actuales del Outbox y de los listeners por ambiente y slot
El único servicio que publica `styleradar.tryon.job.created` en el ecosistema es **`PT8080-VirtualTryOn-Service`** (`TransactionalOutboxPublisherAdapter`). Antes de tocar configuración alguna, consultar y anotar los valores vigentes (`OUTBOX_DISPATCH_ENABLED`, `TRYON_AMQP_LISTENERS_ENABLED`, número de réplicas/slots activos y commit en `/actuator/info`) en **cada ambiente y slot** conectado al mismo broker y vhost:

```bash
# Repetir por cada App Service y cada deployment slot (agregando --slot <SLOT> si aplica)
az webapp config appsettings list -g styleradar-pt8080-rg -n <APP_SERVICE_NAME> \
  --query "[?name=='OUTBOX_DISPATCH_ENABLED' || name=='TRYON_AMQP_LISTENERS_ENABLED' || name=='RABBITMQ_HOST' || name=='RABBITMQ_VHOST'].{name:name, value:value}" -o table

curl -s https://<APP_SERVICE_HOST>/actuator/info
```

Guardar la tabla de respaldo para el Paso 6 (ejemplo con los valores observados en la sección 2):

| Ambiente / Slot | `OUTBOX_DISPATCH_ENABLED` original | `TRYON_AMQP_LISTENERS_ENABLED` original |
|---|---|---|
| `styleradar-virtualtryon-qa` (`production` / principal) | *(no definido -> valor por defecto `true`)* | `true` |
| `styleradar-virtualtryon-prod` (`production` / principal) | *(no definido -> valor por defecto `true`)* | `false` |

### Paso 2. Configurar la pausa, desplegar la imagen con `OUTBOX_DISPATCH_ENABLED` en todas las réplicas y confirmar fin de envíos en curso
**Importante:** Mantener `q.tryon.job.process` enlazada a `styleradar.exchange` (`styleradar.tryon.job.created`) durante todo este paso. Poner `OUTBOX_DISPATCH_ENABLED=false` sobre la versión vieja del binario no impide que una réplica anterior despache nuevas solicitudes ni garantiza que deje de hacerlo aunque los contadores parezcan quietos por unos segundos.

1. Configurar la pausa en **todos** los ambientes y slots que publiquen al mismo broker y vhost:
   ```bash
   az webapp config appsettings set -g styleradar-pt8080-rg -n <APP_SERVICE_NAME> \
     --settings OUTBOX_DISPATCH_ENABLED=false TRYON_AMQP_LISTENERS_ENABLED=false
   ```
   *(Si existe un slot adicional en el mismo App Service, repetir con `--slot <SLOT>`).*
2. **Desplegar la imagen que ya incluye el PR #25** (con soporte de `OUTBOX_DISPATCH_ENABLED` e `ignoreDeclarationExceptions=true` para convivir sin crash-loop mientras exista la cola legada) en **todas** las réplicas, slots y ambientes conectados al mismo broker/vhost.
3. **Confirmar que terminó el despliegue al 100 % y no queda ninguna réplica antigua activa:**
   - Verificar que el workflow/rollout finalizó sin instancias antiguas drenando tráfico y que `GET /actuator/info` en cada ambiente/slot reporta ya el commit nuevo del PR #25.
   - *(Con `OUTBOX_DISPATCH_ENABLED=false` efectivo en la imagen nueva, tanto el `@Scheduled` cada `5000 ms` como el intento inmediato post-commit en `CreateTryOnJobServiceImpl` omiten publicar en RabbitMQ).*
4. **Confirmar que terminaron todos los envíos en curso del Outbox y consumos activos:**
   - **En PostgreSQL (`probador_db` de cada ambiente):** confirmar `0` filas con `status = 'PROCESSING'` (lease máximo `30 s`) y `MAX(published_at)` estable entre dos lecturas separadas por > `10 s`:
     ```sql
     SELECT status, COUNT(*) AS total, MAX(published_at) AS ultimo_envio
     FROM outbox_events
     WHERE event_type IN ('styleradar.tryon.job.created', 'tryon.job.created')
     GROUP BY status;
     ```
   - **En RabbitMQ:** confirmar `consumers = 0`, `messages_unacknowledged = 0` y `messages_ready` estable en `q.tryon.job.process`:
     ```bash
     rabbitmqctl list_queues -p / name consumers messages_ready messages_unacknowledged arguments
     ```

### Paso 3. Desenlazar `q.tryon.job.process` y drenar jobs pendientes hacia una cola temporal SIN binding
Con la pausa efectiva en todas las réplicas desplegadas y cero envíos en curso:

1. Declarar la cola durable temporal `q.tryon.job.process.migration.tmp` (mismo prefijo `q.tryon.*`) **sin crear ningún binding hacia `styleradar.exchange`**.
2. Desenlazar `q.tryon.job.process` de `styleradar.exchange` (`routing_key = styleradar.tryon.job.created`).
3. Drenar todos los mensajes pendientes desde `q.tryon.job.process` → `q.tryon.job.process.migration.tmp` usando confirmaciones de publicación (`publisher confirms` / `ack-mode: on-confirm`) **antes** del `basicAck` en origen:

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

Verificar antes del Paso 4 que `q.tryon.job.process` tenga **`messages = 0`** y **`messages_unacknowledged = 0`**, y que `q.tryon.job.process.migration.tmp` tenga el conteo exacto drenado.

### Paso 4. Recrear `q.tryon.job.process` con `x-dead-letter-exchange=styleradar.dlx`
1. Eliminar la cola antigua **exigiendo que esté vacía** (`--if-empty` / `queueDelete(..., ifUnused=false, ifEmpty=true)`):
   ```bash
   rabbitmqctl delete_queue -p / q.tryon.job.process --if-empty
   ```
2. Asegurar la existencia del exchange `styleradar.dlx` (`direct`, durable) y de la cola `q.tryon.job.process.dlq` enlazada a `styleradar.dlx` con routing key `tryon.job.process.dead`.
3. Declarar la nueva `q.tryon.job.process` durable con:
   - `x-dead-letter-exchange`: `styleradar.dlx`
   - `x-dead-letter-routing-key`: `tryon.job.process.dead`
   - Binding **único** desde `styleradar.exchange` con routing key `styleradar.tryon.job.created` (la cola temporal sigue sin estar enlazada al exchange).

### Paso 5. Reinyectar los jobs restaurados y eliminar la cola temporal (aún con Outbox pausado)
1. Mover todos los mensajes desde `q.tryon.job.process.migration.tmp` directamente hacia `q.tryon.job.process` (exchange por defecto `""`, routing key `q.tryon.job.process`, con `publisher confirms` / `on-confirm` antes del `basicAck`).
2. Confirmar que `q.tryon.job.process.migration.tmp` quedó en `0` mensajes y eliminarla con `--if-empty`:
   ```bash
   rabbitmqctl delete_queue -p / q.tryon.job.process.migration.tmp --if-empty
   ```
3. Confirmar que solo `q.tryon.job.process` está enlazada a `styleradar.tryon.job.created` y que sus argumentos muestran `x-dead-letter-exchange=styleradar.dlx` y `x-dead-letter-routing-key=tryon.job.process.dead`.

### Paso 6. Restablecer los valores originales de cada ambiente y slot, y validar
**Únicamente después** de completar el Paso 5, restablecer en cada ambiente y slot los valores exactos registrados en el Paso 1 (**no** habilitar listeners donde estaban apagados):

```bash
# Aplicar en cada ambiente/slot usando exactamente sus valores del Paso 1:
# - Ejemplo QA (listeners originalmente activos):
az webapp config appsettings set -g styleradar-pt8080-rg -n styleradar-virtualtryon-qa \
  --settings OUTBOX_DISPATCH_ENABLED=<VALOR_ORIGINAL_OUTBOX_QA> TRYON_AMQP_LISTENERS_ENABLED=true

# - Ejemplo PROD (si PROD tenía TRYON_AMQP_LISTENERS_ENABLED=false, DEBE seguir en false):
az webapp config appsettings set -g styleradar-pt8080-rg -n styleradar-virtualtryon-prod \
  --settings OUTBOX_DISPATCH_ENABLED=<VALOR_ORIGINAL_OUTBOX_PROD> TRYON_AMQP_LISTENERS_ENABLED=false
```

*(Si `<VALOR_ORIGINAL_OUTBOX_*>` no existía antes del Paso 1, restablecerlo a `true` o eliminar el override explícito).*

Validación final:
1. Los eventos que quedaron en `outbox_events` (`status = 'PENDING'`) durante la ventana pasan a `PUBLISHED` automáticamente y llegan **una sola vez** a `q.tryon.job.process`.
2. Todos los jobs restaurados más los creados durante la pausa se procesan sin pérdida ni duplicación en los ambientes con listeners habilitados.
3. Un job rechazado sin reencolar (`AmqpRejectAndDontRequeueException`, `defaultRequeueRejected=false`) aterriza en `q.tryon.job.process.dlq` con `exchange = styleradar.dlx` y `routingKey = tryon.job.process.dead`.

---

## 5. Prueba automatizada en broker real (Docker / Testcontainers)

Todo este flujo (arranque con `styleradar.tryon.dlx` + 2 jobs en cola → reproducción de `PRECONDITION_FAILED` → pausa del Outbox + creación de un job durante la pausa que permanece `PENDING` → drenaje sin doble binding hacia `q.tryon.job.process.migration.tmp` → recreación de `q.tryon.job.process` → reinyección → reanudación del Outbox verificando entrega **única** del job creado durante la pausa + enrutamiento de rechazo a `q.tryon.job.process.dlq` por `styleradar.dlx` / `tryon.job.process.dead`) está automatizado en:

- `src/test/java/edu/dosw/styleradar/virtualtryon/infrastructure/adapter/in/amqp/TryOnJobRabbitUpgradeMigrationIntegrationTest.java`
- `src/test/java/edu/dosw/styleradar/virtualtryon/infrastructure/adapter/out/messaging/TransactionalOutboxPublisherAdapterTest.java`
