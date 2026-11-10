package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.application.dto.event.UserAccountDeletedEvent;
import edu.dosw.styleradar.virtualtryon.application.service.DeleteBiometricDataService;
import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Adaptador de entrada AMQP (RabbitMQ) que consume eventos del ecosistema StyleRadar:
 * 1. catalog.garment.updated -> sincroniza prendas del catálogo (SR-RF-24).
 * 2. user.account.deleted -> ejecuta purga total de datos biométricos por Ley 1581 (SR-RF-27).
 */
@Component
public class TryOnEventListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TryOnEventListenerAdapter.class);
    public static final String CATALOG_GARMENT_UPDATED_QUEUE = "styleradar.probador.prenda-publicada.q";
    public static final String USER_ACCOUNT_DELETED_QUEUE = "styleradar.probador.cuenta-eliminada.q";

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
            log.warn("Evento catalog.garment.updated descartado por carecer de garmentId válido");
            return;
        }
        try {
            GarmentCategory category = GarmentCategory.fromRawValue(event.category());
            Instant updatedAt = event.updatedAt() != null ? event.updatedAt() : Instant.now();
            CatalogGarment garment = new CatalogGarment(
                    GarmentId.of(event.garmentId()),
                    event.name(),
                    category,
                    event.imageUrl(),
                    event.visible(),
                    updatedAt
            );
            queryTryOnJobsService.syncCatalogGarment(garment);
            log.info("Prenda del catálogo {} sincronizada con categoría {} (compatible={})",
                    event.garmentId(), category, category.isCompatibleWithTryOn());
        } catch (IllegalArgumentException ex) {
            log.warn("Evento catalog.garment.updated ignorado para prenda {}: {}", event.garmentId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Fallo inesperado al procesar evento catalog.garment.updated para prenda {}. Redirigiendo a DLQ: {}",
                    event.garmentId(), ex.getMessage());
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(
                    "Error irrecuperable al sincronizar prenda. Redirigido a Dead Letter Queue", ex
            );
        }
    }

    @RabbitListener(
            queues = "${styleradar.tryon.amqp.user-account-deleted-queue:" + USER_ACCOUNT_DELETED_QUEUE + "}",
            autoStartup = "${styleradar.tryon.amqp.listeners-enabled:true}"
    )
    public void onUserAccountDeleted(UserAccountDeletedEvent event) {
        if (event == null || event.userId() == null) {
            log.warn("Evento user.account.deleted descartado por carecer de userId válido");
            return;
        }
        if (deleteBiometricDataService != null) {
            deleteBiometricDataService.purgeAllBiometricDataForUser(UserId.of(event.userId()));
            log.info("Datos biométricos purgados automáticamente tras evento user.account.deleted para usuario {}",
                    event.userId());
        }
    }
}
