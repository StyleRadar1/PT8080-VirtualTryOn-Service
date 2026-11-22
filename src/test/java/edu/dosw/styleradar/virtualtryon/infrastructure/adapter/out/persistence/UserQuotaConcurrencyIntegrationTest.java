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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.time.YearMonth;
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
 * Pruebas de integración transaccionales reales sobre PostgreSQL 16 (Testcontainers + Flyway V1..V10).
 * Verifica la eliminación del deadlock Java/BD, el aislamiento fila a fila y la creación sin conflictos
 * de la cuota de un nuevo mes mediante {@code INSERT ... ON CONFLICT DO NOTHING} + {@code SELECT ... FOR UPDATE}
 * cuando el primer consumo y una suspensión ocurren simultáneamente (SR-RF-108).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class UserQuotaConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("probador_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
    }

    @Autowired
    private DataSource dataSource;

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

    private BillingPeriod previousBillingPeriod(BillingPeriod currentPeriod) {
        return BillingPeriod.of(YearMonth.parse(currentPeriod.getValue()).minusMonths(1).toString());
    }

    private String seedTryOnPrerequisites(UUID userUuid, UUID garmentUuid) {
        String blobPath = userUuid + "/front.jpg";
        String photoUrl = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/" + blobPath;

        consentJpaRepository.saveAndFlush(new BiometricConsentJpaEntity(
                UUID.randomUUID(),
                userUuid,
                "v1.0-LEY1581-2026",
                true,
                "VIRTUAL_TRY_ON_INFERENCE",
                Instant.parse("2026-10-01T10:00:00Z"),
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
                Instant.parse("2026-10-01T10:05:00Z"),
                true
        ));
        catalogGarmentJpaRepository.saveAndFlush(new CatalogGarmentJpaEntity(
                garmentUuid,
                "Camisa Concurrente",
                "TOP",
                "https://styleradar.blob.core.windows.net/catalog/shirt.png",
                true,
                "S,M,L",
                Instant.parse("2026-10-01T10:10:00Z")
        ));
        return photoUrl;
    }

    @Test
    @DisplayName("HU SR-RF-108 (PostgreSQL real): Dos transacciones reales (consumo + suspensión) sobre la misma cuota terminan sin deadlock")
    void shouldCompleteConcurrentQuotaConsumptionAndSuspensionTransactionsWithoutDeadlock() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getDatabaseProductName().contains("PostgreSQL"));
        }

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
                Instant.parse("2026-11-01T00:00:00Z"),
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

        List<UserQuotaJpaEntity> rows = userQuotaJpaRepository
                .findAllByUserIdAndBillingPeriod(userUuid, currentPeriod.getValue());
        assertEquals(1, rows.size());

        UserQuotaJpaEntity persisted = rows.get(0);
        assertEquals(3, persisted.getUsedCount());
        assertFalse(persisted.isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, persisted.getMonthlyLimit());
        assertEquals(suspensionTime, persisted.getPlanUpdatedAt());
    }

    @Test
    @DisplayName("HU SR-RF-108 (PostgreSQL real): CreateTryOnJobServiceImpl y suspensión concurrente completan sin deadlock preservando el trabajo, consumo único y plan gratuito")
    void shouldCompleteRealCreateTryOnJobAndSubscriptionSuspensionConcurrently() throws Exception {
        UUID userUuid = UUID.randomUUID();
        UUID garmentUuid = UUID.randomUUID();
        UserId userId = UserId.of(userUuid);
        GarmentId garmentId = GarmentId.of(garmentUuid);
        BillingPeriod currentPeriod = BillingPeriod.current();
        Instant initialPaymentTime = Instant.parse("2026-11-21T19:00:00Z");
        Instant suspensionTime = Instant.parse("2026-11-21T19:10:00Z");

        String photoUrl = seedTryOnPrerequisites(userUuid, garmentUuid);
        userQuotaJpaRepository.saveAndFlush(new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userUuid,
                UserQuota.RADAR_PLUS_MONTHLY_LIMIT,
                2,
                currentPeriod.getValue(),
                true,
                Instant.parse("2026-11-01T00:00:00Z"),
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

        List<UserQuotaJpaEntity> rows = userQuotaJpaRepository
                .findAllByUserIdAndBillingPeriod(userUuid, currentPeriod.getValue());
        List<TryOnJobJpaEntity> jobs = tryOnJobJpaRepository.findByUserIdOrderByCreatedAtDesc(userUuid);

        assertEquals(1, rows.size());
        UserQuotaJpaEntity persisted = rows.get(0);
        assertEquals(3, persisted.getUsedCount());
        assertFalse(persisted.isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, persisted.getMonthlyLimit());
        assertEquals(suspensionTime, persisted.getPlanUpdatedAt());
        assertEquals(1, jobs.size());
    }

    @Test
    @DisplayName("HU SR-RF-108 (PostgreSQL real): Pago obsoleto concurrente con suspensión más reciente conserva el plan gratuito y el consumo")
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
                Instant.parse("2026-11-01T00:00:00Z"),
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

        List<UserQuotaJpaEntity> rows = userQuotaJpaRepository
                .findAllByUserIdAndBillingPeriod(userUuid, currentPeriod.getValue());
        assertEquals(1, rows.size());

        UserQuotaJpaEntity persisted = rows.get(0);
        assertFalse(persisted.isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, persisted.getMonthlyLimit());
        assertEquals(newerSuspensionTime, persisted.getPlanUpdatedAt());
        assertEquals(4, persisted.getUsedCount());
    }

    @Test
    @DisplayName("HU SR-RF-108 (PostgreSQL real · Inicio de mes): Con solo cuota Radar+ del mes anterior, primer consumo y suspensión en paralelo crean una sola fila con usedCount=1 y plan gratuito")
    void shouldCreateSingleQuotaWhenFirstTryOnAndSuspensionCollideOnNewMonth() throws Exception {
        BillingPeriod currentPeriod = BillingPeriod.current();
        BillingPeriod previousPeriod = previousBillingPeriod(currentPeriod);
        Instant previousPaymentTime = Instant.parse("2026-10-15T12:00:00Z");
        Instant suspensionTime = Instant.parse("2026-11-21T19:10:00Z");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                UUID userUuid = UUID.randomUUID();
                UUID garmentUuid = UUID.randomUUID();
                UserId userId = UserId.of(userUuid);
                GarmentId garmentId = GarmentId.of(garmentUuid);

                String photoUrl = seedTryOnPrerequisites(userUuid, garmentUuid);
                userQuotaJpaRepository.saveAndFlush(new UserQuotaJpaEntity(
                        UUID.randomUUID(),
                        userUuid,
                        UserQuota.RADAR_PLUS_MONTHLY_LIMIT,
                        7,
                        previousPeriod.getValue(),
                        true,
                        Instant.parse("2026-10-01T00:00:00Z"),
                        previousPaymentTime
                ));

                CyclicBarrier barrier = new CyclicBarrier(2);
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

                List<UserQuotaJpaEntity> currentRows = userQuotaJpaRepository
                        .findAllByUserIdAndBillingPeriod(userUuid, currentPeriod.getValue());
                List<TryOnJobJpaEntity> jobs = tryOnJobJpaRepository.findByUserIdOrderByCreatedAtDesc(userUuid);

                assertEquals(1, currentRows.size());
                UserQuotaJpaEntity persisted = currentRows.get(0);
                assertEquals(1, persisted.getUsedCount());
                assertFalse(persisted.isRadarPlus());
                assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, persisted.getMonthlyLimit());
                assertEquals(suspensionTime, persisted.getPlanUpdatedAt());
                assertEquals(1, jobs.size());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("HU SR-RF-108 (PostgreSQL real · ON CONFLICT): Dos inserciones concurrentes sin fila previa convergen sobre la misma cuota preservando consumo y plan gratuito")
    void shouldShareSameNewMonthRowWhenConcurrentInsertConflictOccurs() throws Exception {
        UUID userUuid = UUID.randomUUID();
        UserId userId = UserId.of(userUuid);
        BillingPeriod currentPeriod = BillingPeriod.current();
        BillingPeriod previousPeriod = previousBillingPeriod(currentPeriod);
        Instant previousPaymentTime = Instant.parse("2026-10-15T12:00:00Z");
        Instant suspensionTime = Instant.parse("2026-11-21T19:10:00Z");

        userQuotaJpaRepository.saveAndFlush(new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userUuid,
                UserQuota.RADAR_PLUS_MONTHLY_LIMIT,
                5,
                previousPeriod.getValue(),
                true,
                Instant.parse("2026-10-01T00:00:00Z"),
                previousPaymentTime
        ));

        CountDownLatch tx1InsertedRow = new CountDownLatch(1);
        CountDownLatch tx2Started = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UserQuota> firstConsumerFuture = executor.submit(() -> requiresNewTransaction().execute(status -> {
                UserQuota quota = tryOnRepositoryAdapter.findOrCreateQuotaForUpdate(userId, currentPeriod);
                assertTrue(quota.isRadarPlus());
                assertEquals(0, quota.getUsedCount());
                tx1InsertedRow.countDown();
                try {
                    assertTrue(tx2Started.await(5, TimeUnit.SECONDS));
                    // TX2 encuentra ausente la fila, ejecuta INSERT ... ON CONFLICT DO NOTHING y espera a TX1 sin abortar su transacción.
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                quota.consumeQuota();
                return tryOnRepositoryAdapter.saveQuota(quota);
            }));

            Future<UserQuota> suspensionFuture = executor.submit(() -> {
                assertTrue(tx1InsertedRow.await(5, TimeUnit.SECONDS));
                tx2Started.countDown();
                return queryTryOnJobsService.updateUserSubscriptionPlan(userId, false, suspensionTime);
            });

            UserQuota consumed = firstConsumerFuture.get(10, TimeUnit.SECONDS);
            UserQuota suspended = suspensionFuture.get(10, TimeUnit.SECONDS);
            assertNotNull(consumed);
            assertNotNull(suspended);
        } finally {
            executor.shutdownNow();
        }

        List<UserQuotaJpaEntity> currentRows = userQuotaJpaRepository
                .findAllByUserIdAndBillingPeriod(userUuid, currentPeriod.getValue());
        assertEquals(1, currentRows.size());

        UserQuotaJpaEntity persisted = currentRows.get(0);
        assertEquals(1, persisted.getUsedCount());
        assertFalse(persisted.isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, persisted.getMonthlyLimit());
        assertEquals(suspensionTime, persisted.getPlanUpdatedAt());
    }
}
