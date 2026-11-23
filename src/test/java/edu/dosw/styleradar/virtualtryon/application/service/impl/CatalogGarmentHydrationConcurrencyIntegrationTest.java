package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.service.CreateTryOnJobService;
import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotEligibleException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.CatalogGarmentLookupPort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.CatalogGarmentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.UserQuotaJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.CatalogGarmentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.TryOnJobJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.UserQuotaJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Prueba de integración en BD real (H2 modo PostgreSQL) que verifica que una respuesta REST
 * atrasada desde Catálogo nunca sobrescribe una retirada concurrente recibida mientras la
 * llamada HTTP está pendiente, y que la creación/validación evalúa la versión que quedó en BD.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class CatalogGarmentHydrationConcurrencyIntegrationTest {

    @Autowired
    private CreateTryOnJobService createTryOnJobService;

    @Autowired
    private QueryTryOnJobsService queryTryOnJobsService;

    @Autowired
    private BiometricDataRepositoryPort biometricDataRepositoryPort;

    @Autowired
    private CatalogGarmentJpaRepository catalogGarmentJpaRepository;

    @Autowired
    private TryOnJobJpaRepository tryOnJobJpaRepository;

    @Autowired
    private UserQuotaJpaRepository userQuotaJpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private CatalogGarmentLookupPort catalogGarmentLookupPort;

    @MockBean
    private BlobStoragePort blobStoragePort;

    @Test
    @DisplayName("Si llega retirada más reciente mientras la consulta REST está pendiente, createTryOnJob conserva la retirada y no encola el trabajo")
    void shouldPreserveConcurrentWithdrawalDuringRestLookupAndRejectCreateTryOnJob() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        String photoUrl = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/" + userId.value() + "/silhouette-race.png";

        seedConsentAndPhoto(userId, photoUrl);

        Instant restTimestamp = Instant.parse("2026-11-23T18:00:00Z");
        Instant withdrawnTimestamp = Instant.parse("2026-11-23T18:01:00Z");

        when(catalogGarmentLookupPort.findGarmentById(garmentId)).thenAnswer(inv -> {
            persistWithdrawalInSeparateTransaction(garmentId, withdrawnTimestamp);
            return Optional.of(new CatalogGarment(
                    garmentId,
                    "Vestido largo negro",
                    GarmentCategory.ONE_PIECE,
                    "https://cdn.styleradar.co/vestido.jpg",
                    true,
                    restTimestamp,
                    Set.of("M")
            ));
        });

        assertThrows(GarmentNotEligibleException.class,
                () -> createTryOnJobService.createTryOnJob(userId, garmentId, photoUrl, true));

        CatalogGarmentJpaEntity persisted = catalogGarmentJpaRepository.findById(garmentId.value()).orElseThrow();
        assertFalse(persisted.isVisible(), "La prenda debe permanecer retirada (visible=false)");
        assertEquals(withdrawnTimestamp, persisted.getUpdatedAt());
        assertTrue(tryOnJobJpaRepository.findByUserIdOrderByCreatedAtDesc(userId.value()).isEmpty(),
                "No debe haberse encolado ningún trabajo tras la retirada concurrente");

        int usedCount = userQuotaJpaRepository
                .findByUserIdAndBillingPeriod(userId.value(), BillingPeriod.current().getValue())
                .map(q -> q.getUsedCount())
                .orElse(0);
        assertEquals(0, usedCount, "La cuota no debe consumirse cuando la prenda fue retirada concurrentemente");
    }

    @Test
    @DisplayName("Si llega retirada más reciente mientras la consulta REST está pendiente, validateGarmentEligibility conserva visible=false y rechaza la prenda")
    void shouldPreserveConcurrentWithdrawalDuringRestLookupOnValidation() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        Instant restTimestamp = Instant.parse("2026-11-23T19:00:00Z");
        Instant withdrawnTimestamp = Instant.parse("2026-11-23T19:02:00Z");

        when(catalogGarmentLookupPort.findGarmentById(garmentId)).thenAnswer(inv -> {
            persistWithdrawalInSeparateTransaction(garmentId, withdrawnTimestamp);
            return Optional.of(new CatalogGarment(
                    garmentId,
                    "Camisa Blanca",
                    GarmentCategory.TOP,
                    "https://cdn.styleradar.co/camisa.jpg",
                    true,
                    restTimestamp,
                    Set.of("M")
            ));
        });

        assertThrows(GarmentNotEligibleException.class,
                () -> queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "M", List.of()));

        CatalogGarmentJpaEntity persisted = catalogGarmentJpaRepository.findById(garmentId.value()).orElseThrow();
        assertFalse(persisted.isVisible());
        assertEquals(withdrawnTimestamp, persisted.getUpdatedAt());
    }

    @Test
    @DisplayName("Cuando no hay retirada concurrente, la hidratación REST inserta la prenda en BD mediante SQL-92 y encola el trabajo")
    void shouldInsertHydratedGarmentViaRequiresNewAndEnqueueJobWhenNoWithdrawalOccurs() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        String photoUrl = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/" + userId.value() + "/silhouette-race.png";
        seedConsentAndPhoto(userId, photoUrl);

        Instant restTimestamp = Instant.parse("2026-11-23T18:10:00Z");
        when(catalogGarmentLookupPort.findGarmentById(garmentId)).thenReturn(Optional.of(new CatalogGarment(
                garmentId,
                "Blusa Seda Blanca",
                GarmentCategory.TOP,
                "https://cdn.styleradar.co/blusa.jpg",
                true,
                restTimestamp,
                Set.of("S", "M")
        )));

        TryOnJob job = createTryOnJobService.createTryOnJob(userId, garmentId, photoUrl, true);

        assertNotNull(job);
        assertEquals(JobStatus.QUEUED, job.getStatus());

        CatalogGarmentJpaEntity persisted = catalogGarmentJpaRepository.findById(garmentId.value()).orElseThrow();
        assertTrue(persisted.isVisible());
        assertEquals(restTimestamp, persisted.getUpdatedAt());
        assertEquals(1, tryOnJobJpaRepository.findByUserIdOrderByCreatedAtDesc(userId.value()).size());
    }

    private void seedConsentAndPhoto(UserId userId, String photoUrl) {
        biometricDataRepositoryPort.saveConsent(new BiometricConsent(
                UUID.randomUUID(),
                userId,
                "v1.0-LEY1581-2026",
                true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"),
                Instant.now(),
                null,
                "127.0.0.1"
        ));
        biometricDataRepositoryPort.savePhoto(new BiometricPhoto(
                UUID.randomUUID(),
                userId,
                "image/png",
                600,
                900,
                12000L,
                userId.value() + "/silhouette-race.png",
                SasSignedUrl.of(photoUrl),
                Instant.now()
        ));
        userQuotaJpaRepository.saveAndFlush(new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userId.value(),
                UserQuota.DEFAULT_FREE_MONTHLY_LIMIT,
                0,
                BillingPeriod.current().getValue(),
                false,
                Instant.now(),
                Instant.now()
        ));
    }

    private void persistWithdrawalInSeparateTransaction(GarmentId garmentId, Instant withdrawnTimestamp) throws InterruptedException {
        TransactionTemplate requiresNewTx = new TransactionTemplate(transactionManager);
        requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Thread rabbitThread = new Thread(() -> requiresNewTx.executeWithoutResult(status ->
                queryTryOnJobsService.syncCatalogGarment(new CatalogGarment(
                        garmentId,
                        "Prenda Retirada",
                        GarmentCategory.ONE_PIECE,
                        "https://cdn.styleradar.co/vestido.jpg",
                        false,
                        withdrawnTimestamp,
                        Set.of("M")
                ))
        ));
        rabbitThread.start();
        rabbitThread.join();
    }
}
