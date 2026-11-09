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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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

        // Error inesperado lanza AmqpRejectAndDontRequeueException para enrutar a DLQ
        doThrow(new RuntimeException("DB connection timeout")).when(catalogGarmentJpaRepository).save(any(CatalogGarmentJpaEntity.class));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
                eventListenerAdapter.onCatalogGarmentUpdated(validEvent)
        );
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

        // 1. Intento de acceso de otro usuario sin rol ADMIN -> JobAccessDeniedException
        assertThrows(edu.dosw.styleradar.virtualtryon.domain.exception.JobAccessDeniedException.class,
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

    @Test
    @DisplayName("HU SR-RF-25 / SR-RF-26: Consulta de TryOnJob por ID con verificación de titularidad y prevención de IDOR")
    void shouldGetTryOnJobWithTitularityCheck() {
        UUID jobId = UUID.randomUUID();
        UserId ownerId = UserId.of(UUID.randomUUID());
        UserId otherUser = UserId.of(UUID.randomUUID());

        edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob job = new edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder()
                .withUserId(ownerId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://storage/photo.png")
                .withCategory(GarmentCategory.TOPS)
                .build();

        edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort jobRepo = org.mockito.Mockito.mock(
                edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort.class
        );
        QueryTryOnJobsServiceImpl serviceWithJobs = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                jobRepo
        );

        when(jobRepo.findJobById(jobId)).thenReturn(Optional.of(job));

        // 1. Dueño legítimo consulta su job -> 200 OK
        edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob found = serviceWithJobs.getTryOnJob(jobId, ownerId, false);
        assertEquals(ownerId, found.getUserId());

        // 2. Administrador consulta cualquier job -> 200 OK
        edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob adminFound = serviceWithJobs.getTryOnJob(jobId, otherUser, true);
        assertEquals(ownerId, adminFound.getUserId());

        // 3. Otro usuario no administrador intenta consultar job ajeno o usuario nulo -> 403 Forbidden (prevención IDOR)
        assertThrows(edu.dosw.styleradar.virtualtryon.domain.exception.JobAccessDeniedException.class,
                () -> serviceWithJobs.getTryOnJob(jobId, otherUser, false));
        assertThrows(edu.dosw.styleradar.virtualtryon.domain.exception.JobAccessDeniedException.class,
                () -> serviceWithJobs.getTryOnJob(jobId, null, false));

        // 4. Job inexistente -> 404 Not Found
        UUID nonExistent = UUID.randomUUID();
        when(jobRepo.findJobById(nonExistent)).thenReturn(Optional.empty());
        assertThrows(edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException.class,
                () -> serviceWithJobs.getTryOnJob(nonExistent, ownerId, false));

        // 5. Servicio sin repositorio de jobs inyectado -> 404 Not Found
        QueryTryOnJobsServiceImpl serviceWithoutJobRepo = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort
        );
        assertThrows(edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException.class,
                () -> serviceWithoutJobRepo.getTryOnJob(jobId, ownerId, false));
    }

    @Test
    @DisplayName("HU SR-RF-27: purgeJobBiometricPhoto debe eliminar físicamente fotos de blob storage y marcar photoPurged en BD")
    void shouldPurgeBiometricPhotoAndLogAudit() {
        edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort jobRepo =
                org.mockito.Mockito.mock(edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort.class);
        edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort blobPort =
                org.mockito.Mockito.mock(edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort.class);

        QueryTryOnJobsServiceImpl service = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                jobRepo,
                blobPort
        );

        UUID jobId = UUID.randomUUID();
        UserId ownerId = UserId.of(UUID.randomUUID());
        String photoUrl = "https://blob/photo.jpg";
        edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl resultUrl =
                edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl.of("https://blob/render.png?sv=2024");

        edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob job = new edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob(
                jobId,
                ownerId,
                edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId.of(UUID.randomUUID()),
                photoUrl,
                edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory.TOPS,
                "https://garment.png",
                edu.dosw.styleradar.virtualtryon.domain.model.JobStatus.COMPLETED,
                resultUrl,
                edu.dosw.styleradar.virtualtryon.domain.model.AiProvider.FASHN_AI,
                1500L,
                null,
                false,
                Instant.now(),
                Instant.now()
        );

        when(jobRepo.findJobById(jobId)).thenReturn(Optional.of(job));

        service.purgeJobBiometricPhoto(jobId, ownerId, false);

        org.mockito.Mockito.verify(blobPort).deleteBlob(photoUrl);
        org.mockito.Mockito.verify(blobPort).deleteBlob(resultUrl.value());
        org.mockito.Mockito.verify(jobRepo, org.mockito.Mockito.atLeastOnce()).saveJob(job);
        org.mockito.Mockito.verify(jobRepo, org.mockito.Mockito.atLeastOnce()).saveAuditLog(any());
        assertTrue(job.isPhotoPurged());
        assertEquals("PURGED_BY_USER_REQUEST", job.getPhotoUrl());
        org.junit.jupiter.api.Assertions.assertNull(job.getResultImageUrl());
    }

    @Test
    @DisplayName("HU SR-RF-25: Purga biométrica con fallo en Azure Storage no marca foto como purgada y propaga la excepción")
    void shouldNotMarkPhotoPurgedAndPropagateExceptionWhenAzurePhotoDeleteFails() {
        edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort jobRepo =
                org.mockito.Mockito.mock(edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort.class);
        edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort blobPort =
                org.mockito.Mockito.mock(edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort.class);

        QueryTryOnJobsServiceImpl service = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                jobRepo,
                blobPort
        );

        UUID jobId = UUID.randomUUID();
        UserId ownerId = UserId.of(UUID.randomUUID());
        String photoUrl = "https://blob/photo.jpg";
        edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl resultUrl =
                edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl.of("https://blob/render.png?sv=2024");

        edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob job = new edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob(
                jobId,
                ownerId,
                edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId.of(UUID.randomUUID()),
                photoUrl,
                edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory.TOPS,
                "https://garment.png",
                edu.dosw.styleradar.virtualtryon.domain.model.JobStatus.COMPLETED,
                resultUrl,
                edu.dosw.styleradar.virtualtryon.domain.model.AiProvider.FASHN_AI,
                1500L,
                null,
                false,
                Instant.now(),
                Instant.now()
        );

        when(jobRepo.findJobById(jobId)).thenReturn(Optional.of(job));
        doThrow(new RuntimeException("Azure Blob Storage 503 Service Unavailable"))
                .when(blobPort).deleteBlob(photoUrl);

        assertThrows(RuntimeException.class, () -> service.purgeJobBiometricPhoto(jobId, ownerId, false));

        org.junit.jupiter.api.Assertions.assertFalse(job.isPhotoPurged());
        assertEquals(photoUrl, job.getPhotoUrl());
        assertEquals(resultUrl, job.getResultImageUrl());
        verify(jobRepo, never()).saveJob(any());
    }

    @Test
    @DisplayName("HU SR-RF-25: Purga con éxito en silueta pero fallo en render refleja purga parcial y propaga excepción")
    void shouldReflectPartialPurgeWhenRenderDeleteFails() {
        edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort jobRepo =
                org.mockito.Mockito.mock(edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort.class);
        edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort blobPort =
                org.mockito.Mockito.mock(edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort.class);

        QueryTryOnJobsServiceImpl service = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                jobRepo,
                blobPort
        );

        UUID jobId = UUID.randomUUID();
        UserId ownerId = UserId.of(UUID.randomUUID());
        String photoUrl = "https://blob/photo.jpg";
        edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl resultUrl =
                edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl.of("https://blob/render.png?sv=2024");

        edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob job = new edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob(
                jobId,
                ownerId,
                edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId.of(UUID.randomUUID()),
                photoUrl,
                edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory.TOPS,
                "https://garment.png",
                edu.dosw.styleradar.virtualtryon.domain.model.JobStatus.COMPLETED,
                resultUrl,
                edu.dosw.styleradar.virtualtryon.domain.model.AiProvider.FASHN_AI,
                1500L,
                null,
                false,
                Instant.now(),
                Instant.now()
        );

        when(jobRepo.findJobById(jobId)).thenReturn(Optional.of(job));
        doNothing().when(blobPort).deleteBlob(photoUrl);
        doThrow(new RuntimeException("Azure Storage Timeout al purgar render"))
                .when(blobPort).deleteBlob(resultUrl.value());

        assertThrows(RuntimeException.class, () -> service.purgeJobBiometricPhoto(jobId, ownerId, false));

        assertTrue(job.isPhotoPurged());
        assertEquals("PURGED_BY_USER_REQUEST", job.getPhotoUrl());
        assertEquals(resultUrl, job.getResultImageUrl());
        verify(jobRepo, org.mockito.Mockito.times(1)).saveJob(job);
    }

    @Test
    @DisplayName("HU SR-RF-25: Reintento exitoso de purga tras fallo parcial completa render sin reintentar silueta ya purgada")
    void shouldCompleteRemainingPurgeOnRetryWithoutRetryingAlreadyPurgedPhoto() {
        edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort jobRepo =
                org.mockito.Mockito.mock(edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort.class);
        edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort blobPort =
                org.mockito.Mockito.mock(edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort.class);

        QueryTryOnJobsServiceImpl service = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                jobRepo,
                blobPort
        );

        UUID jobId = UUID.randomUUID();
        UserId ownerId = UserId.of(UUID.randomUUID());
        edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl resultUrl =
                edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl.of("https://blob/render.png?sv=2024");

        // Job con estado de purga parcial previa
        edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob job = new edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob(
                jobId,
                ownerId,
                edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId.of(UUID.randomUUID()),
                "PURGED_BY_USER_REQUEST",
                edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory.TOPS,
                "https://garment.png",
                edu.dosw.styleradar.virtualtryon.domain.model.JobStatus.COMPLETED,
                resultUrl,
                edu.dosw.styleradar.virtualtryon.domain.model.AiProvider.FASHN_AI,
                1500L,
                null,
                true, // Ya purgado previamente
                Instant.now(),
                Instant.now()
        );

        when(jobRepo.findJobById(jobId)).thenReturn(Optional.of(job));

        service.purgeJobBiometricPhoto(jobId, ownerId, false);

        verify(blobPort, never()).deleteBlob(eq("PURGED_BY_USER_REQUEST"));
        verify(blobPort).deleteBlob(resultUrl.value());
        assertTrue(job.isPhotoPurged());
        org.junit.jupiter.api.Assertions.assertNull(job.getResultImageUrl());
    }

    @Test
    @DisplayName("HU SR-RF-25: Purga biométrica lanza IllegalStateException si BlobStoragePort es null y no inventa éxito ni auditoría física")
    void shouldThrowIllegalStateExceptionWhenBlobStoragePortIsNullDuringPurge() {
        edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort jobRepo =
                org.mockito.Mockito.mock(edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort.class);

        // Instanciado sin BlobStoragePort (null)
        QueryTryOnJobsServiceImpl serviceWithoutStorage = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                jobRepo,
                null
        );

        UUID jobId = UUID.randomUUID();
        UserId ownerId = UserId.of(UUID.randomUUID());
        edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob job = new edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob(
                jobId,
                ownerId,
                edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId.of(UUID.randomUUID()),
                "https://blob/photo.jpg",
                edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory.TOPS,
                "https://garment.png",
                edu.dosw.styleradar.virtualtryon.domain.model.JobStatus.COMPLETED,
                edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl.of("https://blob/render.png"),
                edu.dosw.styleradar.virtualtryon.domain.model.AiProvider.FASHN_AI,
                1500L,
                null,
                false,
                Instant.now(),
                Instant.now()
        );

        when(jobRepo.findJobById(jobId)).thenReturn(Optional.of(job));

        assertThrows(IllegalStateException.class,
                () -> serviceWithoutStorage.purgeJobBiometricPhoto(jobId, ownerId, false));

        org.junit.jupiter.api.Assertions.assertFalse(job.isPhotoPurged());
        verify(jobRepo, never()).saveJob(any());
        verify(jobRepo, never()).saveAuditLog(any());
    }

    @Test
    @DisplayName("HU SR-RF-26: Cobertura exhaustiva de ramas en enrichJobWithReadOnlySignedUrl y métodos default de BlobStoragePort")
    void shouldCoverEnrichBranchesAndBlobPortDefaultMethods() {
        UserId userId = UserId.of(UUID.randomUUID());
        UUID jobId = UUID.randomUUID();

        // 1. Servicio sin repositorio de jobs retorna lista vacía en historial
        QueryTryOnJobsServiceImpl serviceWithoutJobRepo = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                null,
                null
        );
        assertThat(serviceWithoutJobRepo.getJobHistory(userId)).isEmpty();

        // 2. Ramas de enrichJobWithReadOnlySignedUrl: status != COMPLETED, photoPurged = true, resultImageUrl = null
        TryOnJob queuedJob = new TryOnJobBuilder().withId(jobId).withUserId(userId).withGarmentId(GarmentId.of(UUID.randomUUID())).withPhotoUrl("https://storage/photo.jpg").build();
        TryOnJob purgedJob = new TryOnJobBuilder().withId(jobId).withUserId(userId).withGarmentId(GarmentId.of(UUID.randomUUID())).withPhotoUrl("https://storage/photo.jpg").build();
        purgedJob.purgeBiometricPhoto();
        TryOnJob noRenderJob = new TryOnJobBuilder().withId(jobId).withUserId(userId).withGarmentId(GarmentId.of(UUID.randomUUID())).withPhotoUrl("https://storage/photo.jpg").build();

        edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort jobRepo = org.mockito.Mockito.mock(edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnJobRepositoryPort.class);
        QueryTryOnJobsServiceImpl serviceWithRepo = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                jobRepo,
                blobStoragePort
        );

        when(jobRepo.findJobsByUserId(userId)).thenReturn(List.of(queuedJob, purgedJob, noRenderJob));
        List<TryOnJob> history = serviceWithRepo.getJobHistory(userId);
        assertThat(history).hasSize(3);

        // 3. BlobStoragePort default methods
        BlobStoragePort anonymousPort = new BlobStoragePort() {
            @Override
            public String storeBiometricPhoto(UserId uid, String ct, byte[] bytes) {
                return "test/" + uid.value() + ".png";
            }
            @Override
            public SasSignedUrl generateReadOnlySignedUrl(String blobPath) {
                return SasSignedUrl.of("https://storage/" + blobPath + "?sas=1");
            }
            @Override
            public Optional<byte[]> retrieveBlobBytes(String blobPathOrUrl) {
                return blobPathOrUrl == null ? Optional.empty() : Optional.of(new byte[]{1, 2});
            }
            @Override
            public void deleteBlob(String blobPathOrUrl) {
            }
        };

        assertThat(anonymousPort.generateReadOnlySasUrl((SasSignedUrl) null)).isNull();
        SasSignedUrl inputSas = SasSignedUrl.of("https://storage/render.png?old=1");
        assertThat(anonymousPort.generateReadOnlySasUrl(inputSas)).isNotNull();
        assertThat(anonymousPort.storeRenderedResult(userId, jobId, new byte[]{1})).isNotNull();
        assertThat(anonymousPort.retrieveBlobBytes((SasSignedUrl) null)).isEmpty();
        anonymousPort.deleteBlob((SasSignedUrl) null);
    }
}

