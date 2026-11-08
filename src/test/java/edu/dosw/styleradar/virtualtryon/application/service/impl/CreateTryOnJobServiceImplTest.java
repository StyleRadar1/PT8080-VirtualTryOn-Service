package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
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
 * Pruebas unitarias para CreateTryOnJobServiceImpl, TryOnJobBuilder, JobAuditLog,
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

    @Test
    @DisplayName("HU SR-RF-25: Dada solicitud válida con foto, prenda compatible y consentimiento, Cuando se ejecuta createTryOnJob, Entonces valida cadena V01..V03, consume cuota, genera render, guarda JobAuditLog y publica eventos AMQP")
    void shouldCreateAndCompleteTryOnJobPublishingEventsAndAuditLogs() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.empty());

        TryOnJob completed = createTryOnJobService.createTryOnJob(
                userId,
                garmentId,
                "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/photo.png?sv=2024-11-04&sr=b&sp=r&spr=https",
                true
        );

        assertThat(completed.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(completed.getResultImageUrl()).isNotNull();
        assertThat(completed.getResultImageUrl().value()).contains("sp=r");
        assertThat(completed.getExecutionTimeMs()).isGreaterThanOrEqualTo(1L);
        assertThat(persistedAuditLogs).hasSize(3);
        assertThat(persistedAuditLogs.get(0).getNewStatus()).isEqualTo("QUEUED");
        assertThat(persistedAuditLogs.get(1).getNewStatus()).isEqualTo("PROCESSING");
        assertThat(persistedAuditLogs.get(2).getNewStatus()).isEqualTo("COMPLETED");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.DEFAULT_TRYON_EXCHANGE),
                eq("tryon.job.created"),
                any(TryOnJobDomainEvent.class)
        );
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.DEFAULT_TRYON_EXCHANGE),
                eq("tryon.job.completed"),
                any(TryOnJobDomainEvent.class)
        );
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando la estrategia de inferencia lanza error, Entonces marca el job como FAILED, registra auditoría y publica tryon.job.failed en RabbitMQ")
    void shouldMarkJobFailedAndPublishFailedEventWhenInferenceThrowsException() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());

        AiInferenceStrategy failingStrategy = Mockito.mock(AiInferenceStrategy.class);
        when(failingStrategy.supportsProvider(AiProvider.FASHN_AI)).thenReturn(true);
        when(failingStrategy.submitTryOn(any())).thenThrow(new IllegalStateException("Error de almacenamiento en render"));

        CreateTryOnJobServiceImpl failingService = new CreateTryOnJobServiceImpl(
                repositoryAdapter,
                repositoryAdapter,
                quotaCacheAdapter,
                repositoryAdapter,
                eventPublisherAdapter,
                List.of(failingStrategy),
                "fashn"
        );

        assertThatThrownBy(() -> failingService.createTryOnJob(
                userId,
                garmentId,
                "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/photo.png",
                true
        )).isInstanceOf(IllegalStateException.class);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.DEFAULT_TRYON_EXCHANGE),
                eq("tryon.job.failed"),
                any(TryOnJobDomainEvent.class)
        );
        assertThat(persistedAuditLogs).hasSize(3);
        assertThat(persistedAuditLogs.get(2).getNewStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando falta consentimiento, la cuota está agotada o la prenda es incompatible, Entonces rechaza antes de crear el TryOnJob")
    void shouldRejectBeforeJobCreationWhenValidationFails() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        when(consentJpaRepository.findFirstByUserIdOrderByConsentedAtDesc(userId.value())).thenReturn(Optional.empty());
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createTryOnJobService.createTryOnJob(
                userId, garmentId, "https://styleradarblob.blob.core.windows.net/photo.png", false
        )).isInstanceOf(ConsentRequiredException.class);

        when(valueOperations.get(any())).thenReturn("10");
        assertThatThrownBy(() -> createTryOnJobService.createTryOnJob(
                userId, garmentId, "https://styleradarblob.blob.core.windows.net/photo.png", true
        )).isInstanceOf(QuotaExceededException.class);

        when(valueOperations.get(any())).thenReturn("1");
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(
                new CatalogGarmentJpaEntity(garmentId.value(), "Zapatos", "FOOTWEAR", "https://img", true, Instant.now())
        ));
        assertThatThrownBy(() -> createTryOnJobService.createTryOnJob(
                userId, garmentId, "https://styleradarblob.blob.core.windows.net/photo.png", true
        )).isInstanceOf(InvalidGarmentException.class);

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
                .withProvider(AiProvider.MOCK_LOCAL)
                .build();
        builtJob.purgeBiometricPhoto();
        assertThat(builtJob.isPhotoPurged()).isTrue();

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
        eventPublisherAdapter.publishJobEvent(TryOnJobDomainEvent.fromJob(builtJob));

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

