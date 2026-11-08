package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp;

import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Adaptador de entrada AMQP (RabbitMQ) que consume eventos del ecosistema StyleRadar,
 * manteniendo sincronizada la proyección local de prendas del catálogo (catalog.garment.updated) (SR-RF-24).
 */
@Component
public class TryOnEventListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TryOnEventListenerAdapter.class);
    public static final String CATALOG_GARMENT_UPDATED_QUEUE = "q.tryon.catalog.garment.updated";

    private final QueryTryOnJobsService queryTryOnJobsService;

    public TryOnEventListenerAdapter(QueryTryOnJobsService queryTryOnJobsService) {
        this.queryTryOnJobsService = queryTryOnJobsService;
    }

    @RabbitListener(queues = CATALOG_GARMENT_UPDATED_QUEUE, autoStartup = "${styleradar.tryon.amqp.listeners-enabled:false}")
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
        }
    }
}

