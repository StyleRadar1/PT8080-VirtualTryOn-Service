package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence;

import edu.dosw.styleradar.virtualtryon.application.service.impl.CreateTryOnJobServiceImpl;
import edu.dosw.styleradar.virtualtryon.application.service.impl.QueryTryOnJobsServiceImpl;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.CatalogGarmentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnJobJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.UserQuotaJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricConsentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricPhotoJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.CatalogGarmentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.TryOnJobJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.UserQuotaJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de integración transaccional real con Spring Boot + JPA + SQL concurrente.
 * Verifica que el consumo de cuota y la actualización/suspensión del plan de suscripción
 * coordinan exclusivamente sobre la fila en base de datos sin bloqueos Java en orden inverso (deadlock AB-BA),
 * preservando el incremento único de usedCount y el estado final del plan (SR-RF-108).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:quota_concurrency_test_db;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.hikari.maximum-pool-size=10"
})
class UserQuotaConcurrencyIntegrationTest {

    @Autowired
    private CreateTryOnJobServiceImpl createTryOnJobService;

    @Autowired
    private QueryTryOnJobsServiceImpl queryTryOnJobsService;

    @Autowired
    private TryOnRepositoryAdapter tryOnRepositoryAdapter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserQuotaJpaRepository userQuotaJpaRepository;

    @Autowired
    private BiometricConsentJpaRepository consentJpaRepository;

    @Autowired
    private BiometricPhotoJpaRepository photoJpaRepository;

    @Autowired
    private CatalogGarmentJpaRepository catalogGarmentJpaRepository;

    @Autowired
    private TryOnJobJpaRepository tryOnJobJpaRepository;

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @Test
    @DisplayName("HU SR-RF-108: Dos transacciones reales (consumo + suspensión) sobre la misma cuota terminan sin deadlock y preservan consumo y plan gratuito")
    void shouldCompleteConcurrentQuotaConsumptionAndSuspensionTransactionsWithoutDeadlock() throws Exception {
        UUID userUuid = UUID.randomUUID();
        UserId userId = UserId.of(userUuid);
        BillingPeriod currentPeriod = BillingPeriod.current();
        Instant initialPaymentTime = Instant.parse("2026-11-21T19:00:00Z");
        Instant suspensionTime = Instant.parse("2026-11-21T19:10:00Z");

        userQuotaJpaRepository.saveAndFlush(new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userUuid,
                UserQuota.RADAR_PLUS_MONTHLY_LIMIT,
                2,
                currentPeriod.getValue(),
                true,
                Instant.now(),
                initialPaymentTime
        ));

        CountDownLatch tx1LockedRow = new CountDownLatch(1);
        CountDownLatch tx2Started = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UserQuota> consumptionFuture = executor.submit(() -> requiresNewTransaction().execute(status -> {
                UserQuota quota = tryOnRepositoryAdapter.findQuotaByUserIdAndPeriodForUpdate(userId, currentPeriod)
                        .orElseThrow();
                tx1LockedRow.countDown();
                try {
                    assertTrue(tx2Started.await(5, TimeUnit.SECONDS));
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                quota.consumeQuota();
                return tryOnRepositoryAdapter.saveQuota(quota);
            }));

            Future<UserQuota> suspensionFuture = executor.submit(() -> {
                assertTrue(tx1LockedRow.await(5, TimeUnit.SECONDS));
                tx2Started.countDown();
                return queryTryOnJobsService.updateUserSubscriptionPlan(userId, false, suspensionTime);
            });

            UserQuota consumed = consumptionFuture.get(10, TimeUnit.SECONDS);
            UserQuota suspended = suspensionFuture.get(10, TimeUnit.SECONDS);
            assertNotNull(consumed);
            assertNotNull(suspended);
        } finally {
            executor.shutdownNow();
        }

        UserQuotaJpaEntity persisted = userQuotaJpaRepository
                .findByUserIdAndBillingPeriod(userUuid, currentPeriod.getValue())
                .orElseThrow();

        assertEquals(3, persisted.getUsedCount());
        assertFalse(persisted.isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, persisted.getMonthlyLimit());
        assertEquals(suspensionTime, persisted.getPlanUpdatedAt());
    }

    @Test
    @DisplayName("HU SR-RF-108: CreateTryOnJobServiceImpl y suspensión concurrente completan sin deadlock preservando el trabajo, un único consumo y el plan gratuito")
    void shouldCompleteRealCreateTryOnJobAndSubscriptionSuspensionConcurrently() throws Exception {
        UUID userUuid = UUID.randomUUID();
        UUID garmentUuid = UUID.randomUUID();
        UserId userId = UserId.of(userUuid);
        GarmentId garmentId = GarmentId.of(garmentUuid);
        BillingPeriod currentPeriod = BillingPeriod.current();
        Instant initialPaymentTime = Instant.parse("2026-11-21T19:00:00Z");
        Instant suspensionTime = Instant.parse("2026-11-21T19:10:00Z");

        String blobPath = userUuid + "/front.jpg";
        String photoUrl = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/" + blobPath;

        consentJpaRepository.saveAndFlush(new BiometricConsentJpaEntity(
                UUID.randomUUID(),
                userUuid,
                "v1.0-LEY1581-2026",
                true,
                "VIRTUAL_TRY_ON_INFERENCE",
                Instant.now(),
                null,
                "127.0.0.1"
        ));
        photoJpaRepository.saveAndFlush(new BiometricPhotoJpaEntity(
                UUID.randomUUID(),
                userUuid,
                "image/jpeg",
                800,
                1200,
                204800L,
                blobPath,
                Instant.now(),
                true
        ));
        catalogGarmentJpaRepository.saveAndFlush(new CatalogGarmentJpaEntity(
                garmentUuid,
                "Camisa Concurrente",
                "TOP",
                "https://styleradar.blob.core.windows.net/catalog/shirt.png",
                true,
                "S,M,L",
                Instant.now()
        ));
        userQuotaJpaRepository.saveAndFlush(new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userUuid,
                UserQuota.RADAR_PLUS_MONTHLY_LIMIT,
                2,
                currentPeriod.getValue(),
                true,
                Instant.now(),
                initialPaymentTime
        ));

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TryOnJob> createJobFuture = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return createTryOnJobService.createTryOnJob(userId, garmentId, photoUrl, true);
            });

            Future<UserQuota> suspensionFuture = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return queryTryOnJobsService.updateUserSubscriptionPlan(userId, false, suspensionTime);
            });

            TryOnJob createdJob = createJobFuture.get(10, TimeUnit.SECONDS);
            UserQuota suspended = suspensionFuture.get(10, TimeUnit.SECONDS);
            assertNotNull(createdJob);
            assertNotNull(suspended);
        } finally {
            executor.shutdownNow();
        }

        UserQuotaJpaEntity persisted = userQuotaJpaRepository
                .findByUserIdAndBillingPeriod(userUuid, currentPeriod.getValue())
                .orElseThrow();
        List<TryOnJobJpaEntity> jobs = tryOnJobJpaRepository.findByUserIdOrderByCreatedAtDesc(userUuid);

        assertEquals(3, persisted.getUsedCount());
        assertFalse(persisted.isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, persisted.getMonthlyLimit());
        assertEquals(suspensionTime, persisted.getPlanUpdatedAt());
        assertEquals(1, jobs.size());
    }

    @Test
    @DisplayName("HU SR-RF-108: Pago obsoleto concurrente con suspensión más reciente en transacciones reales conserva el plan gratuito y el consumo")
    void shouldKeepNewerSuspensionWhenPaymentAndSuspensionRunInParallelRealTransactions() throws Exception {
        UUID userUuid = UUID.randomUUID();
        UserId userId = UserId.of(userUuid);
        BillingPeriod currentPeriod = BillingPeriod.current();
        Instant olderPaymentTime = Instant.parse("2026-11-21T19:00:00Z");
        Instant newerSuspensionTime = Instant.parse("2026-11-21T19:10:00Z");

        userQuotaJpaRepository.saveAndFlush(new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userUuid,
                UserQuota.DEFAULT_FREE_MONTHLY_LIMIT,
                4,
                currentPeriod.getValue(),
                false,
                Instant.now(),
                null
        ));

        CountDownLatch suspensionLockedRow = new CountDownLatch(1);
        CountDownLatch paymentStarted = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UserQuota> suspensionFuture = executor.submit(() -> requiresNewTransaction().execute(status -> {
                tryOnRepositoryAdapter.findQuotaByUserIdAndPeriodForUpdate(userId, currentPeriod)
                        .orElseThrow();
                suspensionLockedRow.countDown();
                try {
                    assertTrue(paymentStarted.await(5, TimeUnit.SECONDS));
                    Thread.sleep(150);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return tryOnRepositoryAdapter.updateQuotaPlanAtomically(
                        userId, currentPeriod, false, newerSuspensionTime
                );
            }));

            Future<UserQuota> paymentFuture = executor.submit(() -> {
                assertTrue(suspensionLockedRow.await(5, TimeUnit.SECONDS));
                paymentStarted.countDown();
                return queryTryOnJobsService.updateUserSubscriptionPlan(userId, true, olderPaymentTime);
            });

            UserQuota suspended = suspensionFuture.get(10, TimeUnit.SECONDS);
            UserQuota paymentResult = paymentFuture.get(10, TimeUnit.SECONDS);
            assertNotNull(suspended);
            assertNotNull(paymentResult);
        } finally {
            executor.shutdownNow();
        }

        UserQuotaJpaEntity persisted = userQuotaJpaRepository
                .findByUserIdAndBillingPeriod(userUuid, currentPeriod.getValue())
                .orElseThrow();

        assertFalse(persisted.isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, persisted.getMonthlyLimit());
        assertEquals(newerSuspensionTime, persisted.getPlanUpdatedAt());
        assertEquals(4, persisted.getUsedCount());
    }
}
