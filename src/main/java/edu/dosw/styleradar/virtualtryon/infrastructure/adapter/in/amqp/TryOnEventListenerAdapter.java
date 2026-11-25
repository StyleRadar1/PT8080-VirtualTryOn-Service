package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.application.dto.event.UserAccountDeletedEvent;
import edu.dosw.styleradar.virtualtryon.application.dto.event.UserSubscriptionPlanEvent;
import edu.dosw.styleradar.virtualtryon.application.service.DeleteBiometricDataService;
import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Adaptador de entrada AMQP (RabbitMQ) que consume eventos del ecosistema StyleRadar:
 * 1. styleradar.catalogo.prenda.publicada -> sincroniza prendas del catálogo (SR-RF-24).
 * 2. styleradar.catalogo.prenda.retirada -> oculta prendas retiradas del catálogo (SR-RF-24).
 * 3. styleradar.catalogo.moderacion.cambiada -> actualiza visibilidad/moderación (SR-RF-24).
 * 4. styleradar.usuarios.cuenta.eliminada -> ejecuta purga total de datos biométricos por Ley 1581 (SR-RF-27).
 */
@Component
public class TryOnEventListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TryOnEventListenerAdapter.class);
    public static final String CATALOG_GARMENT_UPDATED_QUEUE = "styleradar.probador.prenda-publicada.q";
    public static final String CATALOG_GARMENT_WITHDRAWN_QUEUE = "styleradar.probador.prenda-retirada.q";
    public static final String CATALOG_GARMENT_MODERATION_QUEUE = "styleradar.probador.moderacion-cambiada.q";
    public static final String USER_ACCOUNT_DELETED_QUEUE = "styleradar.probador.cuenta-eliminada.q";
    public static final String PAYMENT_CONFIRMED_QUEUE = "styleradar.probador.pago-confirmado.q";
    public static final String SUBSCRIPTION_SUSPENDED_QUEUE = "styleradar.probador.suscripcion-suspendida.q";

    private final QueryTryOnJobsService queryTryOnJobsService;
    private final DeleteBiometricDataService deleteBiometricDataService;

    @Autowired
    public TryOnEventListenerAdapter(
            QueryTryOnJobsService queryTryOnJobsService,
            DeleteBiometricDataService deleteBiometricDataService
    ) {
        this.queryTryOnJobsService = queryTryOnJobsService;
        this.deleteBiometricDataService = deleteBiometricDataService;
    }

    public TryOnEventListenerAdapter(QueryTryOnJobsService queryTryOnJobsService) {
        this(queryTryOnJobsService, null);
    }

    @RabbitListener(
            queues = "${styleradar.tryon.amqp.catalog-garment-queue:" + CATALOG_GARMENT_UPDATED_QUEUE + "}",
            autoStartup = "${styleradar.tryon.amqp.listeners-enabled:true}"
    )
    public void onCatalogGarmentUpdated(CatalogGarmentUpdatedEvent event) {
        if (event == null || event.garmentId() == null) {
            log.error("Evento catalog.garment.updated descartado a DLQ por carecer de garmentId válido");
            throw new AmqpRejectAndDontRequeueException(
                    "Evento catalog.garment.updated inválido: carece de garmentId"
            );
        }
        try {
            syncFullCatalogGarment(event, event.visible());
        } catch (Exception ex) {
            log.error("Fallo al procesar evento catalog.garment.updated para prenda {}. Redirigiendo a DLQ: {}",
                    event.garmentId(), ex.getMessage());
            throw new AmqpRejectAndDontRequeueException(
                    "Error irrecuperable al sincronizar prenda. Redirigido a Dead Letter Queue", ex
            );
        }
    }

    @RabbitListener(
            queues = "${styleradar.tryon.amqp.catalog-withdrawn-queue:" + CATALOG_GARMENT_WITHDRAWN_QUEUE + "}",
            autoStartup = "${styleradar.tryon.amqp.listeners-enabled:true}"
    )
    public void onCatalogGarmentWithdrawn(CatalogGarmentUpdatedEvent event) {
        handleVisibilityEvent(event, false, "prenda.retirada");
    }

    @RabbitListener(
            queues = "${styleradar.tryon.amqp.catalog-moderation-queue:" + CATALOG_GARMENT_MODERATION_QUEUE + "}",
            autoStartup = "${styleradar.tryon.amqp.listeners-enabled:true}"
    )
    public void onCatalogGarmentModerationChanged(CatalogGarmentUpdatedEvent event) {
        boolean targetVisibility = event != null && event.visible();
        handleVisibilityEvent(event, targetVisibility, "moderacion.cambiada");
    }

    @RabbitListener(
            queues = "${styleradar.tryon.amqp.user-account-deleted-queue:" + USER_ACCOUNT_DELETED_QUEUE + "}",
            autoStartup = "${styleradar.tryon.amqp.listeners-enabled:true}"
    )
    public void onUserAccountDeleted(UserAccountDeletedEvent event) {
        if (event == null || event.userId() == null) {
            log.error("ALERTA LEY 1581: Evento user.account.deleted inválido o sin userId; enviado a DLQ");
            throw new AmqpRejectAndDontRequeueException(
                    "Evento user.account.deleted inválido: carece de userId"
            );
        }
        if (deleteBiometricDataService == null) {
            log.error("ALERTA LEY 1581: DeleteBiometricDataService no disponible al recibir baja del usuario {}; enviado a DLQ",
                    event.userId());
            throw new AmqpRejectAndDontRequeueException(
                    "DeleteBiometricDataService no disponible para purgar datos del usuario " + event.userId()
            );
        }
        deleteBiometricDataService.purgeAllBiometricDataForUser(UserId.of(event.userId()));
        log.info("Datos biométricos purgados automáticamente tras evento user.account.deleted para usuario {}",
                event.userId());
    }

    @RabbitListener(
            queues = "${styleradar.tryon.amqp.payment-confirmed-queue:" + PAYMENT_CONFIRMED_QUEUE + "}",
            autoStartup = "${styleradar.tryon.amqp.listeners-enabled:true}"
    )
    public void onPaymentConfirmed(UserSubscriptionPlanEvent event) {
        if (event == null) {
            log.error("Evento pagos.pago.confirmado nulo descartado a DLQ");
            throw new AmqpRejectAndDontRequeueException("Evento pagos.pago.confirmado inválido: payload nulo");
        }
        if (event.isStoreSubscription()) {
            log.debug("Evento pagos.pago.confirmado ignorado por corresponder a suscripción de tienda (planId={})",
                    event.planId());
            return;
        }
        if (event.userId() == null) {
            log.error("Evento pagos.pago.confirmado de usuario descartado a DLQ por carecer de userId válido");
            throw new AmqpRejectAndDontRequeueException("Evento pagos.pago.confirmado inválido: carece de userId");
        }
        if (!event.isApproved() || !event.isRadarPlusSubscription()) {
            log.debug("Evento pagos.pago.confirmado ignorado por no corresponder a un pago aprobado de Radar+ (planId={}, status={})",
                    event.planId(), event.status());
            return;
        }
        queryTryOnJobsService.updateUserSubscriptionPlan(UserId.of(event.userId()), true, event.occurredAt());
        log.info("Plan Radar+ procesado en cuotas del Probador Virtual para usuario {} (occurredAt={})",
                event.userId(), event.occurredAt());
    }

    @RabbitListener(
            queues = "${styleradar.tryon.amqp.subscription-suspended-queue:" + SUBSCRIPTION_SUSPENDED_QUEUE + "}",
            autoStartup = "${styleradar.tryon.amqp.listeners-enabled:true}"
    )
    public void onSubscriptionSuspended(UserSubscriptionPlanEvent event) {
        if (event == null) {
            log.error("Evento pagos.suscripcion.suspendida nulo descartado a DLQ");
            throw new AmqpRejectAndDontRequeueException("Evento pagos.suscripcion.suspendida inválido: payload nulo");
        }
        if (event.isStoreSubscription()) {
            log.debug("Evento pagos.suscripcion.suspendida ignorado por corresponder a suscripción de tienda (planId={})",
                    event.planId());
            return;
        }
        if (event.userId() == null) {
            log.error("Evento pagos.suscripcion.suspendida de usuario descartado a DLQ por carecer de userId válido");
            throw new AmqpRejectAndDontRequeueException("Evento pagos.suscripcion.suspendida inválido: carece de userId");
        }
        if (event.planId() != null && !event.isRadarPlusSubscription()) {
            log.debug("Evento pagos.suscripcion.suspendida ignorado por corresponder a otro plan (planId={})",
                    event.planId());
            return;
        }
        queryTryOnJobsService.updateUserSubscriptionPlan(UserId.of(event.userId()), false, event.occurredAt());
        log.info("Suscripción suspendida procesada en cuotas para usuario {} (occurredAt={})",
                event.userId(), event.occurredAt());
    }

    private void handleVisibilityEvent(CatalogGarmentUpdatedEvent event, boolean visible, String eventName) {
        if (event == null || event.garmentId() == null) {
            log.error("Evento {} sin garmentId válido; enviado a DLQ", eventName);
            throw new AmqpRejectAndDontRequeueException("Evento " + eventName + " inválido: carece de garmentId");
        }
        try {
            GarmentId garmentId = GarmentId.of(event.garmentId());
            Instant updatedAt = event.updatedAt() != null ? event.updatedAt() : Instant.now();
            Optional<CatalogGarment> updated = queryTryOnJobsService.updateCatalogGarmentVisibility(
                    garmentId, visible, updatedAt
            );
            if (updated.isPresent()) {
                log.info("Prenda {} actualizada por evento {} con visible={}",
                        event.garmentId(), eventName, visible);
                return;
            }
            if (event.name() != null && !event.name().isBlank()
                    && event.category() != null && !event.category().isBlank()) {
                syncFullCatalogGarment(event, visible);
                return;
            }
            // La prenda no está proyectada y el evento no trae datos para crearla: descartarlo en silencio
            // perdería la actualización (p. ej. retiro anterior a la publicación). Se envía a la DLQ para
            // preservarlo y poder reprocesarlo cuando la prenda exista.
            log.error("Prenda {} no existe en proyección local al recibir {} (visible={}) y el evento no permite crearla; enviado a DLQ para reproceso",
                    event.garmentId(), eventName, visible);
            throw new AmqpRejectAndDontRequeueException(
                    "Prenda " + event.garmentId() + " inexistente al procesar " + eventName
                            + "; actualización conservada en Dead Letter Queue"
            );
        } catch (AmqpRejectAndDontRequeueException rejection) {
            throw rejection;
        } catch (Exception ex) {
            log.error("Error procesando evento {} para prenda {}. Redirigiendo a DLQ: {}",
                    eventName, event.garmentId(), ex.getMessage());
            throw new AmqpRejectAndDontRequeueException(
                    "Error al procesar evento " + eventName + ". Redirigido a Dead Letter Queue", ex
            );
        }
    }

    private void syncFullCatalogGarment(CatalogGarmentUpdatedEvent event, boolean visible) {
        GarmentCategory category = GarmentCategory.fromRawValue(event.category());
        Instant updatedAt = event.updatedAt() != null ? event.updatedAt() : Instant.now();
        CatalogGarment garment = new CatalogGarment(
                GarmentId.of(event.garmentId()),
                event.name(),
                category,
                CatalogGarment.sanitizeImageUrl(event.imageUrl()),
                visible,
                updatedAt,
                event.availableSizes()
        );
        queryTryOnJobsService.syncCatalogGarment(garment);
        log.info("Prenda del catálogo {} sincronizada con categoría {} (compatible={}, visible={})",
                event.garmentId(), category, category.isCompatibleWithTryOn(), visible);
    }
}
