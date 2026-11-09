package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobAuditLog;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.strategy.AiInferenceStrategy;
import edu.dosw.styleradar.virtualtryon.domain.strategy.MockAiStrategy;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.messaging.TryOnEventPublisherAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.RedisQuotaCacheAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.TryOnRepositoryAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.CatalogGarmentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.JobAuditLogJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnJobJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.UserQuotaJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricConsentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricPhotoJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.CatalogGarmentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.JobAuditLogJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.TryOnJobJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.UserQuotaJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.config.RabbitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias completas para CreateTryOnJobServiceImpl, TryOnRepositoryAdapter,
 * TryOnJobDomainEvent y TryOnEventPublisherAdapter (SR-RF-25).
 */
@ExtendWith(MockitoExtension.class)
class CreateTryOnJobServiceImplTest {

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
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    @Mock
    private BlobStoragePort blobStoragePort;

    private TryOnRepositoryAdapter repositoryAdapter;
    private RedisQuotaCacheAdapter quotaCacheAdapter;
    private TryOnEventPublisherAdapter eventPublisherAdapter;
    private CreateTryOnJobServiceImpl createTryOnJobService;
    private final List<JobAuditLogJpaEntity> persistedAuditLogs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TryOnPersistenceMapper mapper = new TryOnPersistenceMapper();
        repositoryAdapter = new TryOnRepositoryAdapter(
                consentJpaRepository,
                photoJpaRepository,
                quotaJpaRepository,
                catalogGarmentJpaRepository,
                tryOnJobJpaRepository,
                jobAuditLogJpaRepository,
                mapper
        );

        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        quotaCacheAdapter = new RedisQuotaCacheAdapter(redisTemplateProvider);

        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(rabbitTemplate);
        eventPublisherAdapter = new TryOnEventPublisherAdapter(
                rabbitTemplateProvider,
                RabbitConfig.DEFAULT_TRYON_EXCHANGE
        );

        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriod(any(), any())).thenReturn(Optional.empty());
        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(any(), any())).thenReturn(Optional.empty());
        lenient().when(quotaJpaRepository.save(any(UserQuotaJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(tryOnJobJpaRepository.save(any(TryOnJobJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jobAuditLogJpaRepository.save(any(JobAuditLogJpaEntity.class))).thenAnswer(inv -> {
            JobAuditLogJpaEntity entity = inv.getArgument(0);
            persistedAuditLogs.add(entity);
            return entity;
        });
        lenient().when(blobStoragePort.storeRenderedResult(any(), any(), any())).thenReturn(
                SasSignedUrl.of("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/render.png?sv=2024-11-04&sr=b&sp=r&spr=https")
        );

        MockAiStrategy mockAiStrategy = new MockAiStrategy(blobStoragePort);
        createTryOnJobService = new CreateTryOnJobServiceImpl(
                repositoryAdapter,
                repositoryAdapter,
                quotaCacheAdapter,
                repositoryAdapter,
                eventPublisherAdapter,
                List.of(mockAiStrategy),
                "mock"
        );
    }

    private void mockActiveConsentAndPhoto(UserId userId) {
        BiometricConsentJpaEntity consentEntity = new BiometricConsentJpaEntity(
                UUID.randomUUID(), userId.value(), "v1.0", true, "[\"VIRTUAL_TRY_ON_INFERENCE\"]", Instant.now(), null, "127.0.0.1"
        );
        lenient().when(consentJpaRepository.findFirstByUserIdOrderByConsentedAtDesc(userId.value()))
                .thenReturn(Optional.of(consentEntity));

        BiometricPhotoJpaEntity photoEntity = new BiometricPhotoJpaEntity(
                UUID.randomUUID(), userId.value(), "image/png", 400, 600, 5000L, userId.value() + "/silhouette-test.png", Instant.now()
        );
        lenient().when(photoJpaRepository.findByUserId(userId.value()))
                .thenReturn(Optional.of(photoEntity));
    }

    private void mockCatalogGarment(GarmentId garmentId, GarmentCategory category, boolean visible) {
        CatalogGarmentJpaEntity garmentEntity = new CatalogGarmentJpaEntity(
                garmentId.value(),
                "Prenda Test " + garmentId.value(),
                category.name(),
                "https://styleradar.dosw.edu/catalog/garments/" + garmentId.value() + ".png",
                visible,
                Instant.now()
        );
        lenient().when(catalogGarmentJpaRepository.findById(garmentId.value()))
                .thenReturn(Optional.of(garmentEntity));
    }

    @Test
    @DisplayName("HU SR-RF-25: Dada solicitud válida con foto, prenda compatible y consentimiento, Cuando se ejecuta createTryOnJob, Entonces valida cadena V01..V03, consume cuota, guarda TryOnJob en QUEUED, registra JobAuditLog y publica evento tryon.job.created")
    void shouldCreateQueuedTryOnJobDeductingQuotaAndPublishingCreatedEvent() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        mockActiveConsentAndPhoto(userId);
        mockCatalogGarment(garmentId, GarmentCategory.TOPS, true);

        String photoUrl = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/" + userId.value() + "/silhouette-test.png?sp=r";

        TryOnJob queued = createTryOnJobService.createTryOnJob(
                userId,
                garmentId,
                photoUrl,
                true
        );

        assertThat(queued.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(queued.getResultImageUrl()).isNull();
        assertThat(persistedAuditLogs).hasSize(1);
        assertThat(persistedAuditLogs.get(0).getNewStatus()).isEqualTo("QUEUED");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.DEFAULT_TRYON_EXCHANGE),
                eq("tryon.job.created"),
                any(TryOnJobDomainEvent.class)
        );
    }

    @Test
    @DisplayName("HU SR-RF-25: Creación con TransactionTemplate, sincronización de cuota desde Redis y validación de photoUrl con blobPath")
    void shouldCreateQueuedJobWithTransactionTemplateAndCachedRedisCount() {
        org.springframework.transaction.support.TransactionTemplate mockTemplate = org.mockito.Mockito.mock(
                org.springframework.transaction.support.TransactionTemplate.class
        );
        when(mockTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });

        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.transaction.support.TransactionTemplate> provider =
                org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mockTemplate);

        CreateTryOnJobServiceImpl serviceWithTx = new CreateTryOnJobServiceImpl(
                repositoryAdapter,
                repositoryAdapter,
                quotaCacheAdapter,
                repositoryAdapter,
                eventPublisherAdapter,
                List.of(new MockAiStrategy(blobStoragePort)),
                "mock",
                provider
        );

        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        mockActiveConsentAndPhoto(userId);
        mockCatalogGarment(garmentId, GarmentCategory.TOPS, true);

        when(valueOperations.get(any())).thenReturn("3");

        String photoUrl = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/" + userId.value() + "/silhouette-test.png";

        TryOnJob job = serviceWithTx.createTryOnJob(userId, garmentId, photoUrl, true);
        assertThat(job).isNotNull();
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando falta consentimiento, la cuota está agotada o la prenda no existe/es incompatible, Entonces rechaza antes de crear el TryOnJob")
    void shouldRejectBeforeJobCreationWhenValidationFails() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        // 1. Falta consentimiento explícito
        assertThatThrownBy(() -> createTryOnJobService.createTryOnJob(
                userId, garmentId, "https://styleradarblob.blob.core.windows.net/photo.png", false
        )).isInstanceOf(ConsentRequiredException.class);

        // 2. Consentimiento no registrado en base de datos
        when(consentJpaRepository.findFirstByUserIdOrderByConsentedAtDesc(userId.value())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> createTryOnJobService.createTryOnJob(
                userId, garmentId, "https://styleradarblob.blob.core.windows.net/photo.png", true
        )).isInstanceOf(ConsentRequiredException.class);

        // Mock consentimiento activo
        mockActiveConsentAndPhoto(userId);

        // 3. Usuario sin foto frontal registrada
        UserId userWithoutPhoto = UserId.of(UUID.randomUUID());
        BiometricConsentJpaEntity consentEntity = new BiometricConsentJpaEntity(
                UUID.randomUUID(), userWithoutPhoto.value(), "v1.0", true, "[\"VIRTUAL_TRY_ON_INFERENCE\"]", Instant.now(), null, "127.0.0.1"
        );
        when(consentJpaRepository.findFirstByUserIdOrderByConsentedAtDesc(userWithoutPhoto.value())).thenReturn(Optional.of(consentEntity));
        when(photoJpaRepository.findByUserId(userWithoutPhoto.value())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createTryOnJobService.createTryOnJob(
                userWithoutPhoto, garmentId, "https://styleradarblob.blob.core.windows.net/photo.png", true
        )).isInstanceOf(InvalidPhotoException.class);

        // 4. URL de foto que no pertenece al usuario autenticado (prevención IDOR)
        assertThatThrownBy(() -> createTryOnJobService.createTryOnJob(
                userId, garmentId, "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/otro-usuario/silhouette.png", true
        )).isInstanceOf(InvalidPhotoException.class);

        String validUserPhotoUrl = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/" + userId.value() + "/silhouette-test.png";

        // 5. Prenda no encontrada en el catálogo
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> createTryOnJobService.createTryOnJob(
                userId, garmentId, validUserPhotoUrl, true
        )).isInstanceOf(InvalidGarmentException.class);

        // 6. Prenda no compatible
        mockCatalogGarment(garmentId, GarmentCategory.FOOTWEAR, true);
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> createTryOnJobService.createTryOnJob(
                userId, garmentId, validUserPhotoUrl, true
        )).isInstanceOf(InvalidGarmentException.class);

        // 7. Cuota mensual agotada
        mockCatalogGarment(garmentId, GarmentCategory.TOPS, true);
        when(valueOperations.get(any())).thenReturn("10");
        assertThatThrownBy(() -> createTryOnJobService.createTryOnJob(
                userId, garmentId, validUserPhotoUrl, true
        )).isInstanceOf(QuotaExceededException.class);

        verify(tryOnJobJpaRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("HU SR-RF-25: Verificar consultas de TryOnRepositoryAdapter para jobs y auditLogs, RabbitConfig y manejo de errores en TryOnEventPublisherAdapter")
    void shouldPersistAndQueryJobsAuditLogsAndHandleRabbitPublisherBranches() {
        UUID jobId = UUID.randomUUID();
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        TryOnJob builtJob = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(userId)
                .withGarmentId(garmentId)
                .withPhotoUrl("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/photo.png")
                .withCategory(GarmentCategory.ONE_PIECES)
                .withGarmentImageUrl("https://styleradar.dosw.edu/catalog/garments/dress.png")
                .withProvider(AiProvider.MOCK_LOCAL)
                .build();
        builtJob.purgeBiometricPhoto();
        assertThat(builtJob.isPhotoPurged()).isTrue();
        assertThat(builtJob.getGarmentImageUrl()).isEqualTo("https://styleradar.dosw.edu/catalog/garments/dress.png");

        TryOnJobJpaEntity savedEntity = new TryOnPersistenceMapper().toJobJpaEntity(builtJob);
        when(tryOnJobJpaRepository.findById(jobId)).thenReturn(Optional.of(savedEntity));
        when(tryOnJobJpaRepository.findByUserIdOrderByCreatedAtDesc(userId.value())).thenReturn(List.of(savedEntity));
        when(jobAuditLogJpaRepository.findByJobIdOrderByOccurredAtAsc(jobId)).thenReturn(persistedAuditLogs);

        repositoryAdapter.saveAuditLog(JobAuditLog.ofTransition(jobId, null, JobStatus.QUEUED, "Creado"));
        assertThat(repositoryAdapter.findJobById(jobId)).isPresent();
        assertThat(repositoryAdapter.findJobsByUserId(userId)).hasSize(1);
        assertThat(repositoryAdapter.findAuditLogsByJobId(jobId)).hasSize(1);

        RabbitConfig rabbitConfig = new RabbitConfig();
        assertThat(rabbitConfig.tryOnEventsExchange(RabbitConfig.DEFAULT_TRYON_EXCHANGE).getName())
                .isEqualTo(RabbitConfig.DEFAULT_TRYON_EXCHANGE);
        assertThat(rabbitConfig.jackson2JsonMessageConverter(new com.fasterxml.jackson.databind.ObjectMapper())).isNotNull();

        eventPublisherAdapter.publishJobEvent(null);
        Mockito.doThrow(new RuntimeException("Rabbit offline"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));
        assertThatThrownBy(() -> eventPublisherAdapter.publishJobEvent(TryOnJobDomainEvent.fromJob(builtJob)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Rabbit offline");

        @SuppressWarnings("unchecked")
        ObjectProvider<RabbitTemplate> emptyRabbitProvider = Mockito.mock(ObjectProvider.class);
        when(emptyRabbitProvider.getIfAvailable()).thenReturn(null);
        TryOnEventPublisherAdapter offlinePublisher = new TryOnEventPublisherAdapter(
                emptyRabbitProvider,
                RabbitConfig.DEFAULT_TRYON_EXCHANGE
        );
        offlinePublisher.publishJobEvent(TryOnJobDomainEvent.fromJob(builtJob));
    }
}
