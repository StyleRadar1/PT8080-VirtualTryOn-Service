package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnOutfit;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp.TryOnEventListenerAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.RedisQuotaCacheAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.TryOnRepositoryAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.CatalogGarmentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnJobJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnOutfitJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.UserQuotaJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricConsentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricPhotoJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.CatalogGarmentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.JobAuditLogJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.TryOnJobJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.TryOnOutfitJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.UserQuotaJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para QueryTryOnJobsServiceImpl, TryOnRepositoryAdapter (cuotas y prendas),
 * RedisQuotaCacheAdapter y TryOnEventListenerAdapter (SR-RF-24).
 */
@ExtendWith(MockitoExtension.class)
class QueryTryOnJobsServiceImplTest {

    @Mock
    private BiometricConsentJpaRepository consentJpaRepository;

    @Mock
    private BiometricPhotoJpaRepository photoJpaRepository;

    @Mock
    private UserQuotaJpaRepository quotaJpaRepository;

    @Mock
    private CatalogGarmentJpaRepository catalogGarmentJpaRepository;

    @Mock
    private TryOnJobJpaRepository tryOnJobJpaRepository;

    @Mock
    private JobAuditLogJpaRepository jobAuditLogJpaRepository;

    @Mock
    private TryOnOutfitJpaRepository tryOnOutfitJpaRepository;

    @Mock
    private BlobStoragePort blobStoragePort;

    @Mock
    private BiometricDataRepositoryPort biometricDataRepositoryPort;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    private TryOnRepositoryAdapter repositoryAdapter;
    private RedisQuotaCacheAdapter redisQuotaCacheAdapter;
    private QueryTryOnJobsServiceImpl queryTryOnJobsService;
    private TryOnEventListenerAdapter eventListenerAdapter;

    @BeforeEach
    void setUp() {
        TryOnPersistenceMapper persistenceMapper = new TryOnPersistenceMapper();
        this.repositoryAdapter = new TryOnRepositoryAdapter(
                consentJpaRepository,
                photoJpaRepository,
                quotaJpaRepository,
                catalogGarmentJpaRepository,
                tryOnJobJpaRepository,
                jobAuditLogJpaRepository,
                tryOnOutfitJpaRepository,
                persistenceMapper
        );
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        this.redisQuotaCacheAdapter = new RedisQuotaCacheAdapter(redisTemplateProvider);

        this.queryTryOnJobsService = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                repositoryAdapter,
                blobStoragePort
        );
        this.eventListenerAdapter = new TryOnEventListenerAdapter(queryTryOnJobsService);
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado usuario sin registro previo de cuota en BD, Cuando consulta getUserQuota, Entonces crea cuota por defecto (10 créditos) en JPA y cachea en Redis")
    void shouldCreateDefaultQuotaAndCacheInRedisWhenNotFoundInDatabase() {
        UserId userId = UserId.of(UUID.randomUUID());
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any()))
                .thenReturn(Optional.empty());
        when(quotaJpaRepository.save(any(UserQuotaJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(valueOperations.get(any())).thenReturn(null);

        UserQuota quota = queryTryOnJobsService.getUserQuota(userId);

        assertEquals(userId, quota.getUserId());
        assertEquals(10, quota.getMonthlyLimit());
        assertEquals(0, quota.getUsedCount());
        assertEquals(10, quota.getRemainingCount());
        verify(quotaJpaRepository).save(any(UserQuotaJpaEntity.class));
        verify(valueOperations).set(any(), eq("0"), any(Duration.class));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado contador en Redis mayor al almacenado en BD, Cuando consulta getUserQuota, Entonces sincroniza y actualiza el conteo en JPA")
    void shouldSynchronizeDatabaseQuotaWhenRedisCounterIsHigher() {
        UserId userId = UserId.of(UUID.randomUUID());
        BillingPeriod currentPeriod = BillingPeriod.current();
        UserQuotaJpaEntity existingEntity = new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userId.value(),
                10,
                2,
                currentPeriod.getValue(),
                false,
                Instant.now()
        );
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(userId.value(), currentPeriod.getValue()))
                .thenReturn(Optional.of(existingEntity));
        when(quotaJpaRepository.save(any(UserQuotaJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(valueOperations.get(any())).thenReturn("4");

        UserQuota quota = queryTryOnJobsService.getUserQuota(userId);

        assertEquals(4, quota.getUsedCount());
        assertEquals(6, quota.getRemainingCount());
        verify(quotaJpaRepository).save(any(UserQuotaJpaEntity.class));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dada prenda existente y compatible, consentimiento vigente y cuota disponible, Cuando se invoca validateGarmentEligibility, Entonces retorna la prenda validada")
    void shouldValidateGarmentEligibilitySuccessfully() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        CatalogGarmentJpaEntity garmentEntity = new CatalogGarmentJpaEntity(
                garmentId.value(),
                "Camisa Oxford Azul",
                "TOPS",
                "https://styleradar.blob.core.windows.net/catalog/oxford.png",
                true,
                Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(garmentEntity));
        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(
                new BiometricConsent(
                        UUID.randomUUID(),
                        userId,
                        "v1.0-LEY1581",
                        true,
                        List.of("VIRTUAL_TRY_ON"),
                        Instant.now(),
                        null,
                        "127.0.0.1"
                )
        ));
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());
        when(valueOperations.get(any())).thenReturn("3");

        CatalogGarment validated = queryTryOnJobsService.validateGarmentEligibility(userId, garmentId);

        assertTrue(validated.isEligibleForTryOn());
        assertEquals(GarmentCategory.TOPS, validated.getCategory());
        assertEquals("Camisa Oxford Azul", validated.getName());
    }

    @Test
    @DisplayName("HU SR-RF-24: Dada prenda inexistente o cuota agotada o falta de consentimiento, Cuando se invoca validateGarmentEligibility, Entonces lanza la excepción de dominio correspondiente")
    void shouldRejectGarmentEligibilityWhenGarmentMissingOrConsentMissingOrQuotaExhausted() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.empty());
        assertThrows(InvalidGarmentException.class,
                () -> queryTryOnJobsService.validateGarmentEligibility(userId, garmentId));

        CatalogGarmentJpaEntity garmentEntity = new CatalogGarmentJpaEntity(
                garmentId.value(),
                "Pantalón Cargo",
                "BOTTOMS",
                "https://styleradar.blob.core.windows.net/catalog/cargo.png",
                true,
                Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(garmentEntity));
        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.empty());
        assertThrows(ConsentRequiredException.class,
                () -> queryTryOnJobsService.validateGarmentEligibility(userId, garmentId));

        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(
                new BiometricConsent(
                        UUID.randomUUID(),
                        userId,
                        "v1.0-LEY1581",
                        true,
                        List.of("VIRTUAL_TRY_ON"),
                        Instant.now(),
                        null,
                        "127.0.0.1"
                )
        ));
        when(valueOperations.get(any())).thenReturn("10");
        assertThrows(QuotaExceededException.class,
                () -> queryTryOnJobsService.validateGarmentEligibility(userId, garmentId));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado evento AMQP catalog.garment.updated válido o inválido, Cuando TryOnEventListenerAdapter lo procesa, Entonces persiste prendas válidas e ignora eventos corruptos")
    void shouldProcessCatalogGarmentUpdatedEventsInListener() {
        UUID garmentUuid = UUID.randomUUID();
        when(catalogGarmentJpaRepository.save(any(CatalogGarmentJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CatalogGarmentUpdatedEvent validEvent = new CatalogGarmentUpdatedEvent(
                garmentUuid,
                "Vestido Midi Floral",
                "VESTIDO",
                "https://styleradar.blob.core.windows.net/catalog/dress-midi.png",
                true,
                Instant.now()
        );
        eventListenerAdapter.onCatalogGarmentUpdated(validEvent);
        verify(catalogGarmentJpaRepository).save(any(CatalogGarmentJpaEntity.class));

        eventListenerAdapter.onCatalogGarmentUpdated(null);
        eventListenerAdapter.onCatalogGarmentUpdated(new CatalogGarmentUpdatedEvent(
                UUID.randomUUID(), "Perfume", "CATEGORIA_INVALIDA", "https://img", true, Instant.now()
        ));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado RedisQuotaCacheAdapter con y sin StringRedisTemplate disponible, Cuando incrementa y elimina claves, Entonces opera de forma resiliente")
    void shouldHandleIncrementEvictAndNullRedisTemplateGracefully() {
        UserId userId = UserId.of(UUID.randomUUID());
        BillingPeriod period = BillingPeriod.of("2026-11");
        when(valueOperations.increment(any())).thenReturn(5L);

        int incremented = redisQuotaCacheAdapter.incrementUsedCount(userId, period);
        assertEquals(5, incremented);

        redisQuotaCacheAdapter.evictQuota(userId, period);
        verify(redisTemplate).delete(any(String.class));

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> emptyProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        RedisQuotaCacheAdapter offlineAdapter = new RedisQuotaCacheAdapter(emptyProvider);

        assertTrue(offlineAdapter.getCachedUsedCount(userId, period).isEmpty());
        offlineAdapter.cacheUsedCount(userId, period, 3);
        assertEquals(0, offlineAdapter.incrementUsedCount(userId, period));
        offlineAdapter.evictQuota(userId, period);
        verify(redisTemplate, never()).delete("tryon:quota:non-existent");

        // Verificar ramas cuando Redis lanza RuntimeException o devuelve cadena en blanco
        when(valueOperations.get(any())).thenReturn("   ");
        assertTrue(redisQuotaCacheAdapter.getCachedUsedCount(userId, period).isEmpty());

        when(valueOperations.get(any())).thenThrow(new RuntimeException("Redis down"));
        assertTrue(redisQuotaCacheAdapter.getCachedUsedCount(userId, period).isEmpty());

        org.mockito.Mockito.doThrow(new RuntimeException("Redis write error"))
                .when(valueOperations).set(any(), any(), any(Duration.class));
        redisQuotaCacheAdapter.cacheUsedCount(userId, period, 2);

        when(valueOperations.increment(any())).thenThrow(new RuntimeException("Redis increment error"));
        assertEquals(0, redisQuotaCacheAdapter.incrementUsedCount(userId, period));

        when(redisTemplate.delete(any(String.class))).thenThrow(new RuntimeException("Redis delete error"));
        redisQuotaCacheAdapter.evictQuota(userId, period);
    }

    @Test
    @DisplayName("HU SR-RF-26: Dado un trabajo COMPLETED con TryOnOutfit expirado o sin outfit previo, Cuando se consulta getJobStatus o getJobHistory, Entonces renueva la URL SAS de solo lectura y verifica titularidad")
    void shouldGetJobStatusAndHistoryAndRefreshExpiredSasUrl() {
        UserId ownerId = UserId.of(UUID.randomUUID());
        UserId otherUserId = UserId.of(UUID.randomUUID());
        UUID jobId = UUID.randomUUID();
        TryOnPersistenceMapper mapper = new TryOnPersistenceMapper();

        TryOnJob completedJob = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(ownerId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://styleradar.blob.core.windows.net/biometric-silhouettes/photo.jpg")
                .build();
        completedJob.complete(
                SasSignedUrl.of("https://styleradar.blob.core.windows.net/biometric-silhouettes/render-old.png?sr=b&sp=r&spr=https"),
                1450L
        );
        TryOnJobJpaEntity jobEntity = mapper.toJobJpaEntity(completedJob);

        when(tryOnJobJpaRepository.findById(jobId)).thenReturn(Optional.of(jobEntity));

        // 1. Intento de acceso de otro usuario sin rol ADMIN -> AccessDeniedException
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> queryTryOnJobsService.getJobStatus(jobId, otherUserId, false));

        // 2. Job no existente -> JobNotFoundException
        UUID unknownJobId = UUID.randomUUID();
        when(tryOnJobJpaRepository.findById(unknownJobId)).thenReturn(Optional.empty());
        assertThrows(JobNotFoundException.class,
                () -> queryTryOnJobsService.getJobStatus(unknownJobId, ownerId, false));

        // 3. Outfit expirado -> renueva URL SAS de solo lectura y persiste en try_on_outfits
        TryOnOutfit expiredOutfit = new TryOnOutfit(
                UUID.randomUUID(),
                jobId,
                ownerId,
                SasSignedUrl.of("https://styleradar.blob.core.windows.net/biometric-silhouettes/render-old.png?sr=b&sp=r&spr=https"),
                Instant.now().minusSeconds(300),
                Instant.now().minusSeconds(3900)
        );
        when(tryOnOutfitJpaRepository.findByJobId(jobId)).thenReturn(Optional.of(mapper.toOutfitJpaEntity(expiredOutfit)));
        when(tryOnOutfitJpaRepository.save(any(TryOnOutfitJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        SasSignedUrl refreshedUrl = SasSignedUrl.of("https://styleradar.blob.core.windows.net/biometric-silhouettes/render-refreshed.png?sr=b&sp=r&spr=https");
        when(blobStoragePort.generateReadOnlySasUrl(any())).thenReturn(refreshedUrl);

        TryOnJob result = queryTryOnJobsService.getJobStatus(jobId, ownerId, false);
        assertEquals(refreshedUrl.value(), result.getResultImageUrl().value());
        verify(tryOnOutfitJpaRepository).save(any(TryOnOutfitJpaEntity.class));

        // 4. Outfit vigente (no expirado) y accedido por ADMIN -> retorna sin renovar
        TryOnOutfit validOutfit = TryOnOutfit.createForCompletedJob(jobId, ownerId, refreshedUrl);
        when(tryOnOutfitJpaRepository.findByJobId(jobId)).thenReturn(Optional.of(mapper.toOutfitJpaEntity(validOutfit)));
        TryOnJob adminResult = queryTryOnJobsService.getJobStatus(jobId, otherUserId, true);
        assertEquals(refreshedUrl.value(), adminResult.getResultImageUrl().value());

        // 5. Sin outfit previo + job QUEUED + job purgado en el historial -> cubre todas las ramas de enrichJobWithReadOnlySignedUrl
        TryOnJob queuedJob = new TryOnJobBuilder()
                .withId(UUID.randomUUID())
                .withUserId(ownerId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://styleradar.blob.core.windows.net/biometric-silhouettes/photo2.jpg")
                .build();
        TryOnJob purgedCompletedJob = new TryOnJobBuilder()
                .withId(UUID.randomUUID())
                .withUserId(ownerId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://styleradar.blob.core.windows.net/biometric-silhouettes/photo3.jpg")
                .build();
        purgedCompletedJob.complete(refreshedUrl, 900L);
        purgedCompletedJob.purgeBiometricPhoto();

        when(tryOnOutfitJpaRepository.findByJobId(jobId)).thenReturn(Optional.empty());
        when(tryOnJobJpaRepository.findByUserIdOrderByCreatedAtDesc(ownerId.value())).thenReturn(List.of(
                jobEntity,
                mapper.toJobJpaEntity(queuedJob),
                mapper.toJobJpaEntity(purgedCompletedJob)
        ));

        List<TryOnJob> history = queryTryOnJobsService.getJobHistory(ownerId);
        assertEquals(3, history.size());
        assertEquals(refreshedUrl.value(), history.get(0).getResultImageUrl().value());

        // 6. Eliminación de outfit por jobId y constructores alternativos de TryOnRepositoryAdapter
        repositoryAdapter.deleteOutfitByJobId(jobId);
        verify(tryOnOutfitJpaRepository).deleteByJobId(jobId);

        TryOnRepositoryAdapter adapterWithoutOutfitRepo = new TryOnRepositoryAdapter(
                consentJpaRepository,
                photoJpaRepository,
                quotaJpaRepository,
                catalogGarmentJpaRepository,
                tryOnJobJpaRepository,
                jobAuditLogJpaRepository,
                mapper
        );
        assertEquals(validOutfit, adapterWithoutOutfitRepo.saveOutfit(validOutfit));
        assertTrue(adapterWithoutOutfitRepo.findOutfitByJobId(jobId).isEmpty());
        adapterWithoutOutfitRepo.deleteOutfitByJobId(jobId);
    }
}

