package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.application.dto.event.UserSubscriptionPlanEvent;
import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotEligibleException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentSizeUnavailableException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import edu.dosw.styleradar.virtualtryon.domain.exception.TryOnSelectionIncompleteException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BodyZone;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentSizeChart;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentValidationResult;
import edu.dosw.styleradar.virtualtryon.domain.model.SizeMeasurementRange;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnOutfit;
import edu.dosw.styleradar.virtualtryon.domain.model.UserQuota;
import edu.dosw.styleradar.virtualtryon.domain.model.UserBodyMeasurements;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BiometricDataRepositoryPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.GarmentSizeMeasurementPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.UserMeasurementPort;
import edu.dosw.styleradar.virtualtryon.domain.validation.GarmentSelectionPolicy;
import edu.dosw.styleradar.virtualtryon.domain.validation.GarmentSizeFitPolicy;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
    private UserMeasurementPort userMeasurementPort;

    @Mock
    private GarmentSizeMeasurementPort garmentSizeMeasurementPort;

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
                blobStoragePort,
                userMeasurementPort,
                garmentSizeMeasurementPort
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
    @DisplayName("HU SR-RF-24: Dado contador desactualizado en Redis mayor al almacenado en BD tras un reembolso, Cuando consulta getUserQuota, Entonces PostgreSQL prevalece y resincroniza Redis")
    void shouldPreserveDatabaseQuotaAndResyncRedisWhenRedisCounterIsStale() {
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
        when(valueOperations.get(any())).thenReturn("4");

        UserQuota quota = queryTryOnJobsService.getUserQuota(userId);

        assertEquals(2, quota.getUsedCount());
        assertEquals(8, quota.getRemainingCount());
        verify(quotaJpaRepository, never()).save(any(UserQuotaJpaEntity.class));
        verify(valueOperations).set(any(), eq("2"), any(Duration.class));
    }

    @Test
    @DisplayName("HU SR-RF-24: Si falla la actualización de Redis al consultar cuota desactualizada tras reembolso, getUserQuota no revierte el reembolso ni falla")
    void shouldTolerateRedisFailureWhileResyncingStaleCounterInGetUserQuota() {
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
        when(valueOperations.get(any())).thenReturn("3");
        doThrow(new RuntimeException("Redis write timeout"))
                .when(valueOperations).set(any(), any(), any(Duration.class));

        UserQuota quota = queryTryOnJobsService.getUserQuota(userId);

        assertEquals(2, quota.getUsedCount());
        assertEquals(8, quota.getRemainingCount());
        verify(quotaJpaRepository, never()).save(any(UserQuotaJpaEntity.class));
        verify(redisTemplate).delete(any(String.class));
    }

    @Test
    @DisplayName("HU SR-RF-24: Dada prenda existente y compatible, consentimiento vigente y cuota disponible, Cuando se invoca validateGarmentEligibility, Entonces retorna la prenda validada")
    void shouldValidateGarmentEligibilitySuccessfully() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        CatalogGarmentJpaEntity garmentEntity = new CatalogGarmentJpaEntity(
                garmentId.value(),
                "Camisa Oxford Azul",
                "TOP",
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

        GarmentValidationResult result = queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "M", List.of());

        assertTrue(result.eligibleForTryOn());
        assertTrue(result.compatible());
        assertTrue(result.visible());
        assertEquals(BodyZone.UPPER_BODY, result.zone());
        assertEquals(GarmentCategory.TOP, result.garment().getCategory());
        assertEquals("Camisa Oxford Azul", result.garment().getName());
        assertTrue(result.replacedGarmentIds().isEmpty());
    }

    @Test
    @DisplayName("HU SR-RF-24: Con medidas antropométricas y talla 'M', evalúa ajuste orientativo exitoso (sizeEligible=true, fitMessage)")
    void shouldValidateGarmentEligibilityWithSizeFitAndAvailableMeasurements() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        CatalogGarmentJpaEntity garmentEntity = new CatalogGarmentJpaEntity(
                garmentId.value(),
                "Camisa Oxford Azul",
                "TOP",
                "https://styleradar.blob.core.windows.net/catalog/oxford.png",
                true,
                Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(garmentEntity));
        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(
                new BiometricConsent(
                        UUID.randomUUID(), userId, "v1.0-LEY1581", true,
                        List.of("VIRTUAL_TRY_ON"), Instant.now(), null, "127.0.0.1"
                )
        ));
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());

        UserMeasurementPort userMeasurementPort = mock(UserMeasurementPort.class);
        when(userMeasurementPort.findMeasurementsByUserId(userId))
                .thenReturn(Optional.of(new UserBodyMeasurements(175.0, 98.0, 82.0, 96.0)));

        GarmentSizeMeasurementPort garmentSizeMeasurementPort = mock(GarmentSizeMeasurementPort.class);
        GarmentSizeChart chart = new GarmentSizeChart(
                garmentId,
                java.util.Set.of("XS", "S", "M", "L"),
                java.util.Map.of(
                        "XS", SizeMeasurementRange.ofChest(70.0, 88.0),
                        "M", SizeMeasurementRange.ofChest(92.0, 102.0)
                )
        );
        when(garmentSizeMeasurementPort.findSizeChartByGarmentId(garmentId)).thenReturn(Optional.of(chart));

        QueryTryOnJobsServiceImpl serviceWithMeasurements = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                repositoryAdapter,
                blobStoragePort,
                userMeasurementPort,
                garmentSizeMeasurementPort
        );

        // Caso talla M -> compatible
        GarmentValidationResult resultM = serviceWithMeasurements.validateGarmentEligibility(
                userId, garmentId, "M", List.of());

        assertTrue(resultM.eligibleForTryOn());
        assertEquals("M", resultM.selectedSize());
        assertEquals(Boolean.TRUE, resultM.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_FIT_SUCCESS, resultM.fitMessage());
        assertTrue(resultM.quotaAvailable());

        // Caso talla XS -> no recomendada para 98 cm de pecho
        GarmentValidationResult resultXS = serviceWithMeasurements.validateGarmentEligibility(
                userId, garmentId, "XS", List.of());

        assertTrue(resultXS.eligibleForTryOn());
        assertEquals("XS", resultXS.selectedSize());
        assertEquals(Boolean.FALSE, resultXS.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_SIZE_FIT_WARNING, resultXS.fitMessage());
    }

    @Test
    @DisplayName("HU SR-RF-24: Sin medidas registradas del usuario, retorna caso nulo controlado (sizeEligible=null, fitMessage no concluyente)")
    void shouldValidateGarmentEligibilityWithSizeFitWhenNoMeasurementsAreAvailable() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        CatalogGarmentJpaEntity garmentEntity = new CatalogGarmentJpaEntity(
                garmentId.value(),
                "Camisa Oxford Azul",
                "TOP",
                "https://styleradar.blob.core.windows.net/catalog/oxford.png",
                true,
                Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(garmentEntity));
        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(
                new BiometricConsent(
                        UUID.randomUUID(), userId, "v1.0-LEY1581", true,
                        List.of("VIRTUAL_TRY_ON"), Instant.now(), null, "127.0.0.1"
                )
        ));
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());

        GarmentValidationResult result = queryTryOnJobsService.validateGarmentEligibility(
                userId, garmentId, "M", List.of());

        assertTrue(result.eligibleForTryOn());
        assertEquals("M", result.selectedSize());
        assertNull(result.sizeEligible(), "sizeEligible debe ser null cuando no hay registro antropométrico");
        assertEquals(GarmentSizeFitPolicy.MSG_FIT_DATA_UNAVAILABLE, result.fitMessage());
        assertTrue(result.quotaAvailable());
    }

    @Test
    @DisplayName("HU SR-RF-24: Cobertura de sobrecarga de 3 argumentos y constructor de 5 argumentos en QueryTryOnJobsServiceImpl")
    void shouldCoverThreeArgOverloadAndFiveArgConstructor() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        GarmentId existingTopId = GarmentId.of(UUID.randomUUID());

        CatalogGarmentJpaEntity candidateEntity = new CatalogGarmentJpaEntity(
                garmentId.value(),
                "Polo Blanco",
                "TOP",
                "https://storage/polo.png",
                true,
                Instant.now()
        );
        CatalogGarmentJpaEntity existingEntity = new CatalogGarmentJpaEntity(
                existingTopId.value(),
                "Camisa Roja",
                "TOP",
                "https://storage/roja.png",
                true,
                Instant.now()
        );

        lenient().when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(candidateEntity));
        lenient().when(catalogGarmentJpaRepository.findAllById(List.of(existingTopId.value()))).thenReturn(List.of(existingEntity));
        lenient().when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(
                new BiometricConsent(
                        UUID.randomUUID(), userId, "v1.0-LEY1581", true,
                        List.of("VIRTUAL_TRY_ON"), Instant.now(), null, "127.0.0.1"
                )
        ));
        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());

        // Instanciar usando el constructor de 5 argumentos (sin userMeasurementPort)
        QueryTryOnJobsServiceImpl service5Args = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                repositoryAdapter,
                blobStoragePort
        );

        // Invocar la sobrecarga de 3 argumentos sin selectedSize lanza TryOnSelectionIncompleteException
        assertThrows(TryOnSelectionIncompleteException.class, () ->
                service5Args.validateGarmentEligibility(userId, garmentId, List.of(existingTopId))
        );

        // Invocar pasando null como selectedGarmentIds con talla "L"
        GarmentValidationResult resultNullSelected = service5Args.validateGarmentEligibility(
                userId, garmentId, "L", null
        );
        assertNotNull(resultNullSelected);
        assertEquals("L", resultNullSelected.selectedSize());
        assertTrue(resultNullSelected.replacedGarmentIds().isEmpty());
    }

    @Test
    @DisplayName("HU SR-RF-24 (FA-04 / RN-01 / RN-02): Prenda incompatible (OUTERWEAR) u oculta lanza GarmentNotEligibleException y selectedGarmentIds calcula reemplazos")
    void shouldReturnNonThrowingValidationResultForIncompatibleOrHiddenGarmentAndComputeZoneReplacements() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId jacketId = GarmentId.of(UUID.randomUUID());
        GarmentId hiddenTopId = GarmentId.of(UUID.randomUUID());
        GarmentId dressId = GarmentId.of(UUID.randomUUID());
        GarmentId existingTopId = GarmentId.of(UUID.randomUUID());

        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(
                new BiometricConsent(
                        UUID.randomUUID(), userId, "v1.0-LEY1581", true,
                        List.of("VIRTUAL_TRY_ON"), Instant.now(), null, "127.0.0.1"
                )
        ));
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());
        when(valueOperations.get(any())).thenReturn("1");

        // 1. OUTERWEAR -> lanza GarmentNotEligibleException (FA-04)
        CatalogGarmentJpaEntity outerwearEntity = new CatalogGarmentJpaEntity(
                jacketId.value(), "Chaqueta Cuero", "OUTERWEAR", "https://cat/jacket.png", true, Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(jacketId.value())).thenReturn(Optional.of(outerwearEntity));
        assertThrows(GarmentNotEligibleException.class, () ->
                queryTryOnJobsService.validateGarmentEligibility(userId, jacketId, "M", List.of()));

        // 2. Prenda no visible -> lanza GarmentNotEligibleException (FA-04)
        CatalogGarmentJpaEntity hiddenEntity = new CatalogGarmentJpaEntity(
                hiddenTopId.value(), "Camiseta Oculta", "TOP", "https://cat/hidden.png", false, Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(hiddenTopId.value())).thenReturn(Optional.of(hiddenEntity));
        assertThrows(GarmentNotEligibleException.class, () ->
                queryTryOnJobsService.validateGarmentEligibility(userId, hiddenTopId, "M", List.of()));

        // 3. ONE_PIECE contra selectedGarmentIds -> reemplaza TOP previamente seleccionado
        CatalogGarmentJpaEntity dressEntity = new CatalogGarmentJpaEntity(
                dressId.value(), "Vestido Gala", "ONE_PIECE", "https://cat/dress.png", true, Instant.now()
        );
        CatalogGarmentJpaEntity existingTopEntity = new CatalogGarmentJpaEntity(
                existingTopId.value(), "Blusa Seda", "TOP", "https://cat/top.png", true, Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(dressId.value())).thenReturn(Optional.of(dressEntity));
        when(catalogGarmentJpaRepository.findAllById(List.of(existingTopId.value())))
                .thenReturn(List.of(existingTopEntity));

        GarmentValidationResult dressResult = queryTryOnJobsService.validateGarmentEligibility(
                userId, dressId, "M", List.of(existingTopId)
        );
        assertThat(dressResult.eligibleForTryOn()).isTrue();
        assertThat(dressResult.zone()).isEqualTo(BodyZone.FULL_BODY);
        assertThat(dressResult.replacedGarmentIds()).containsExactly(existingTopId);
    }

    @Test
    @DisplayName("HU SR-RF-24: Dada prenda inexistente o cuota agotada o falta de consentimiento, Cuando se invoca validateGarmentEligibility, Entonces lanza la excepción de dominio correspondiente")
    void shouldRejectGarmentEligibilityWhenGarmentMissingOrConsentMissingOrQuotaExhausted() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.empty());
        assertThrows(GarmentNotFoundException.class,
                () -> queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "M", List.of()));

        CatalogGarmentJpaEntity garmentEntity = new CatalogGarmentJpaEntity(
                garmentId.value(),
                "Pantalón Cargo",
                "BOTTOM",
                "https://styleradar.blob.core.windows.net/catalog/cargo.png",
                true,
                Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(garmentEntity));
        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.empty());
        assertThrows(ConsentRequiredException.class,
                () -> queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "M", List.of()));

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
        BillingPeriod currentPeriod = BillingPeriod.current();
        // Si Redis conservaba un "10" obsoleto tras un reembolso en BD (used=9), PostgreSQL prevalece y permite validar
        UserQuotaJpaEntity refundedEntity = new UserQuotaJpaEntity(
                UUID.randomUUID(), userId.value(), 10, 9, currentPeriod.getValue(), false, Instant.now()
        );
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(userId.value(), currentPeriod.getValue()))
                .thenReturn(Optional.of(refundedEntity));
        when(valueOperations.get(any())).thenReturn("10");

        GarmentValidationResult refundedResult = queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "M", List.of());
        assertTrue(refundedResult.eligibleForTryOn());
        verify(valueOperations).set(any(), eq("9"), any(Duration.class));

        // Cuando la cuota realmente está agotada en PostgreSQL (used=10), lanza QuotaExceededException
        UserQuotaJpaEntity exhaustedEntity = new UserQuotaJpaEntity(
                UUID.randomUUID(), userId.value(), 10, 10, currentPeriod.getValue(), false, Instant.now()
        );
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(userId.value(), currentPeriod.getValue()))
                .thenReturn(Optional.of(exhaustedEntity));
        assertThrows(QuotaExceededException.class,
                () -> queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "M", List.of()));
    }

    @Test
    @DisplayName("HU SR-RF-24: Cuando selectedSize es omitido o no ofrecido por la prenda, lanza excepción correspondiente")
    void shouldRejectWhenSizeIsMissingOrUnavailable() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        // 1. Talla nula o vacía -> TryOnSelectionIncompleteException (FA-01)
        assertThrows(TryOnSelectionIncompleteException.class, () ->
                queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, null, List.of()));
        assertThrows(TryOnSelectionIncompleteException.class, () ->
                queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "   ", List.of()));

        // 2. Prenda con tallas limitadas S, M y se solicita XL -> GarmentSizeUnavailableException (RN-07)
        CatalogGarmentJpaEntity limitedGarment = new CatalogGarmentJpaEntity(
                garmentId.value(), "Camisa Slim", "TOP", "https://cat/slim.png", true, "S,M", Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(limitedGarment));
        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(
                new BiometricConsent(UUID.randomUUID(), userId, "v1.0-LEY1581", true, List.of("VIRTUAL_TRY_ON"), Instant.now(), null, "127.0.0.1")
        ));
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());

        assertThrows(GarmentSizeUnavailableException.class, () ->
                queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "XL", List.of()));
    }

    @Test
    @DisplayName("HU SR-RF-24: Catálogo ofrece S y M, tiene medidas históricas para XL y prenda local sin tallas -> Seleccionar XL lanza GarmentSizeUnavailableException (400 TRYON_SIZE_UNAVAILABLE)")
    void shouldRejectWithTryOnSizeUnavailableWhenCatalogOffersSMAndHasHistoricalXLAndLocalGarmentHasNoSizes() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        // Prenda local antigua sin tallas guardadas (available_sizes es null)
        CatalogGarmentJpaEntity localGarmentWithoutSizes = new CatalogGarmentJpaEntity(
                garmentId.value(), "Camisa Oxford Vintage", "TOP", "https://cat/oxford.png", true, null, Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(localGarmentWithoutSizes));
        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(
                new BiometricConsent(UUID.randomUUID(), userId, "v1.0-LEY1581", true, List.of("VIRTUAL_TRY_ON"), Instant.now(), null, "127.0.0.1")
        ));
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());

        // Catálogo ofrece S y M explícitamente, pero tiene medidas históricas para XL
        SizeMeasurementRange rangeXl = new SizeMeasurementRange(108.0, 116.0, 96.0, 104.0, null, null);
        GarmentSizeChart catalogSizeChart = new GarmentSizeChart(
                garmentId,
                Set.of("S", "M"),
                Map.of("XL", rangeXl)
        );
        when(garmentSizeMeasurementPort.findSizeChartByGarmentId(garmentId)).thenReturn(Optional.of(catalogSizeChart));

        GarmentSizeUnavailableException ex = assertThrows(GarmentSizeUnavailableException.class, () ->
                queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "XL", List.of()));
        assertEquals("TRYON_SIZE_UNAVAILABLE", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("XL"));
    }

    @Test
    @DisplayName("HU SR-RF-24: Catálogo ofrece M, pero falta su tabla de medidas -> Seleccionar M retorna 200 con sizeEligible = null y mensaje de datos insuficientes")
    void shouldReturnSizeEligibleNullWhenCatalogOffersMButMeasurementChartIsMissing() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        // Prenda local antigua sin tallas guardadas
        CatalogGarmentJpaEntity localGarmentWithoutSizes = new CatalogGarmentJpaEntity(
                garmentId.value(), "Camiseta Básica", "TOP", "https://cat/basica.png", true, null, Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(localGarmentWithoutSizes));
        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(
                new BiometricConsent(UUID.randomUUID(), userId, "v1.0-LEY1581", true, List.of("VIRTUAL_TRY_ON"), Instant.now(), null, "127.0.0.1")
        ));
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());

        // Catálogo ofrece M, pero la tabla de medidas está vacía (falta la tabla de la talla M)
        GarmentSizeChart catalogSizeChart = new GarmentSizeChart(
                garmentId,
                Set.of("M"),
                Map.of()
        );
        when(garmentSizeMeasurementPort.findSizeChartByGarmentId(garmentId)).thenReturn(Optional.of(catalogSizeChart));
        when(userMeasurementPort.findMeasurementsByUserId(userId)).thenReturn(Optional.of(
                new UserBodyMeasurements(175.0, 95.0, 80.0, 95.0)
        ));

        GarmentValidationResult result = queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "M", List.of());
        assertNotNull(result);
        assertTrue(result.eligibleForTryOn());
        assertEquals("M", result.selectedSize());
        assertNull(result.sizeEligible());
        assertEquals(GarmentSizeFitPolicy.MSG_FIT_DATA_UNAVAILABLE, result.fitMessage());
    }

    @Test
    @DisplayName("HU SR-RF-24 Regresión: Catálogo responde availableSizes: [], copia local todavía ofrece M -> Seleccionar M lanza 400 TRYON_SIZE_UNAVAILABLE")
    void shouldRejectWithTryOnSizeUnavailableWhenCatalogExplicitlyReturnsEmptyAvailableSizesEvenIfLocalOffersSize() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        // Copia local todavía ofrece M (guardada previamente en available_sizes)
        CatalogGarmentJpaEntity localGarmentWithM = new CatalogGarmentJpaEntity(
                garmentId.value(), "Camiseta Deportiva", "TOP", "https://cat/deportiva.png", true, "M,L", Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(localGarmentWithM));
        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(
                new BiometricConsent(UUID.randomUUID(), userId, "v1.0-LEY1581", true, List.of("VIRTUAL_TRY_ON"), Instant.now(), null, "127.0.0.1")
        ));
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());

        // Catálogo responde explícitamente availableSizes: [] (0 tallas ofertadas, fuente de verdad)
        GarmentSizeChart catalogSizeChart = new GarmentSizeChart(
                garmentId,
                Set.of(),
                Map.of()
        );
        when(garmentSizeMeasurementPort.findSizeChartByGarmentId(garmentId)).thenReturn(Optional.of(catalogSizeChart));

        GarmentSizeUnavailableException ex = assertThrows(GarmentSizeUnavailableException.class, () ->
                queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "M", List.of()));
        assertEquals("TRYON_SIZE_UNAVAILABLE", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("M"));
    }

    @Test
    @DisplayName("HU SR-RF-24: Catálogo no informa availableSizes (null) y copia local ofrece M -> Seleccionar M usa copia local como contingencia")
    void shouldFallbackToLocalCopyWhenCatalogDoesNotInformAvailableSizes() {
        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());

        // Copia local ofrece M
        CatalogGarmentJpaEntity localGarmentWithM = new CatalogGarmentJpaEntity(
                garmentId.value(), "Camiseta Deportiva", "TOP", "https://cat/deportiva.png", true, "M", Instant.now()
        );
        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.of(localGarmentWithM));
        when(biometricDataRepositoryPort.findConsentByUserId(userId)).thenReturn(Optional.of(
                new BiometricConsent(UUID.randomUUID(), userId, "v1.0-LEY1581", true, List.of("VIRTUAL_TRY_ON"), Instant.now(), null, "127.0.0.1")
        ));
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userId.value()), any())).thenReturn(Optional.empty());

        // Catálogo responde tabla de medidas pero availableSizes es null (no informado)
        GarmentSizeChart catalogSizeChartWithoutSizes = new GarmentSizeChart(
                garmentId,
                null,
                Map.of()
        );
        when(garmentSizeMeasurementPort.findSizeChartByGarmentId(garmentId)).thenReturn(Optional.of(catalogSizeChartWithoutSizes));

        GarmentValidationResult result = queryTryOnJobsService.validateGarmentEligibility(userId, garmentId, "M", List.of());
        assertNotNull(result);
        assertTrue(result.eligibleForTryOn());
        assertEquals("M", result.selectedSize());
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

        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
                eventListenerAdapter.onCatalogGarmentUpdated(null)
        );
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
                eventListenerAdapter.onCatalogGarmentUpdated(new CatalogGarmentUpdatedEvent(
                        UUID.randomUUID(), "Perfume", "CATEGORIA_INVALIDA", "https://img", true, Instant.now()
                ))
        );

        // Error inesperado lanza AmqpRejectAndDontRequeueException para enrutar a DLQ
        doThrow(new RuntimeException("DB connection timeout")).when(catalogGarmentJpaRepository).save(any(CatalogGarmentJpaEntity.class));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
                eventListenerAdapter.onCatalogGarmentUpdated(validEvent)
        );
    }

    @Test
    @DisplayName("HU SR-RF-24: Eventos de prenda retirada y moderación cambiada actualizan visibilidad sin fallar por columnas NOT NULL")
    void shouldProcessCatalogWithdrawnAndModerationChangedEvents() throws Exception {
        UUID existingId = UUID.randomUUID();
        java.util.concurrent.atomic.AtomicReference<CatalogGarmentJpaEntity> storedGarment =
                new java.util.concurrent.atomic.AtomicReference<>(new CatalogGarmentJpaEntity(
                        existingId, "Blazer Azul", "TOP", "https://img/blazer.png", true, Instant.now().minusSeconds(60)
                ));
        when(catalogGarmentJpaRepository.findById(existingId)).thenAnswer(inv -> Optional.ofNullable(storedGarment.get()));
        when(catalogGarmentJpaRepository.save(any(CatalogGarmentJpaEntity.class))).thenAnswer(inv -> {
            CatalogGarmentJpaEntity entity = inv.getArgument(0);
            storedGarment.set(entity);
            return entity;
        });

        // 1. Retiro de prenda existente -> marca visible=false preservando name y category NOT NULL
        CatalogGarmentUpdatedEvent withdrawnEvent = new CatalogGarmentUpdatedEvent(
                existingId, null, null, null, true, Instant.now()
        );
        eventListenerAdapter.onCatalogGarmentWithdrawn(withdrawnEvent);
        assertFalse(storedGarment.get().isVisible());
        assertEquals("Blazer Azul", storedGarment.get().getName());
        assertEquals("TOP", storedGarment.get().getCategory());

        // 2. Cambio de moderación aprobada con aliases JSON del catálogo real -> vuelve a visible=true
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String approvedJson = """
                {
                  "itemId": "%s",
                  "nombrePrenda": "Blazer Azul",
                  "categoria": "TOP",
                  "imagenUrl": "https://img/blazer.png",
                  "aprobada": true,
                  "estadoModeracion": "APPROVED"
                }
                """.formatted(existingId);
        CatalogGarmentUpdatedEvent approvedEvent = mapper.readValue(approvedJson, CatalogGarmentUpdatedEvent.class);
        assertTrue(approvedEvent.visible());
        eventListenerAdapter.onCatalogGarmentModerationChanged(approvedEvent);
        assertTrue(storedGarment.get().isVisible());

        // 3. Cambio de moderación RECHAZADA -> resolveVisibility devuelve false
        String rejectedJson = """
                {
                  "garmentId": "%s",
                  "estadoModeracion": "RECHAZADA"
                }
                """.formatted(existingId);
        CatalogGarmentUpdatedEvent rejectedEvent = mapper.readValue(rejectedJson, CatalogGarmentUpdatedEvent.class);
        assertFalse(rejectedEvent.visible());
        eventListenerAdapter.onCatalogGarmentModerationChanged(rejectedEvent);
        assertFalse(storedGarment.get().isVisible());

        // 4. Retiro de prenda inexistente sin name/category -> DLQ (no se pierde la actualización)
        UUID unknownId = UUID.randomUUID();
        when(catalogGarmentJpaRepository.findById(unknownId)).thenReturn(Optional.empty());
        org.springframework.amqp.AmqpRejectAndDontRequeueException withdrawnRejection = assertThrows(
                org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> eventListenerAdapter.onCatalogGarmentWithdrawn(new CatalogGarmentUpdatedEvent(
                        unknownId, null, null, null, false, Instant.now()
                ))
        );
        assertThat(withdrawnRejection.getMessage()).contains(unknownId.toString()).contains("Dead Letter Queue");
        verify(catalogGarmentJpaRepository, org.mockito.Mockito.never())
                .save(argThat(entity -> unknownId.equals(entity.getGarmentId())));

        // 4b. Moderación de prenda inexistente sin datos suficientes -> DLQ
        org.springframework.amqp.AmqpRejectAndDontRequeueException moderationRejection = assertThrows(
                org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> eventListenerAdapter.onCatalogGarmentModerationChanged(new CatalogGarmentUpdatedEvent(
                        unknownId, null, null, null, true, Instant.now()
                ))
        );
        assertThat(moderationRejection.getMessage()).contains(unknownId.toString());

        // 5. Moderación de prenda nueva aún no proyectada pero con name y category -> sincroniza completa
        UUID newModerationId = UUID.randomUUID();
        when(catalogGarmentJpaRepository.findById(newModerationId)).thenReturn(Optional.empty());
        eventListenerAdapter.onCatalogGarmentModerationChanged(new CatalogGarmentUpdatedEvent(
                newModerationId, "Chaqueta Nueva", "TOP", "https://img/chaqueta.png", true, Instant.now()
        ));
        verify(catalogGarmentJpaRepository, org.mockito.Mockito.atLeast(4)).save(any(CatalogGarmentJpaEntity.class));

        // 6. Casos adicionales de CatalogGarmentUpdatedEvent y HandleVisibilityEvent (aliases, slugs, null updatedAt)
        CatalogGarmentUpdatedEvent legacySlugEvent = mapper.readValue("""
                {"itemId":"legacy-slug-id","approved":false,"fechaPublicacion":"2026-11-15T10:00:00Z"}
                """, CatalogGarmentUpdatedEvent.class);
        assertFalse(legacySlugEvent.visible());
        assertThat(legacySlugEvent.garmentId()).isNull();

        CatalogGarmentUpdatedEvent legacyNumericEvent = mapper.readValue("""
                {"itemId":"1001","approved":false,"fechaPublicacion":"2026-11-15T10:00:00Z"}
                """, CatalogGarmentUpdatedEvent.class);
        assertFalse(legacyNumericEvent.visible());
        assertThat(legacyNumericEvent.garmentId()).isNotNull();

        CatalogGarmentUpdatedEvent via6ArgFactory = CatalogGarmentUpdatedEvent.fromJson(
                null, "Camisa", "TOP", "https://img/camisa.png", false, Instant.parse("2026-11-15T10:00:00Z")
        );
        assertFalse(via6ArgFactory.visible());
        assertThat(via6ArgFactory.garmentId()).isNull();

        UUID missingIdWithNameOnly = UUID.randomUUID();
        when(catalogGarmentJpaRepository.findById(missingIdWithNameOnly)).thenReturn(Optional.empty());
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
                eventListenerAdapter.onCatalogGarmentWithdrawn(new CatalogGarmentUpdatedEvent(
                        missingIdWithNameOnly, "Sin Categoria", null, null, false, null
                ))
        );
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
                eventListenerAdapter.onCatalogGarmentModerationChanged(new CatalogGarmentUpdatedEvent(
                        missingIdWithNameOnly, "   ", "TOP", null, true, null
                ))
        );

        // 7. Error de BD en moderación / payload inválido sin garmentId -> DLQ
        CatalogGarmentUpdatedEvent freshApprovedEvent = new CatalogGarmentUpdatedEvent(
                existingId, "Blazer Azul", "TOP", "https://img/blazer.png", true, Instant.now().plusSeconds(10)
        );
        doThrow(new RuntimeException("DB error")).when(catalogGarmentJpaRepository).save(any(CatalogGarmentJpaEntity.class));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
                eventListenerAdapter.onCatalogGarmentModerationChanged(freshApprovedEvent)
        );
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
                eventListenerAdapter.onCatalogGarmentUpdated(new CatalogGarmentUpdatedEvent(
                        null, "Sin ID", "TOP", "https://img", true, Instant.now()
                ))
        );
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
                eventListenerAdapter.onCatalogGarmentWithdrawn(null)
        );
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, () ->
                eventListenerAdapter.onCatalogGarmentModerationChanged(new CatalogGarmentUpdatedEvent(
                        null, "Sin ID", "TOP", "https://img", true, Instant.now()
                ))
        );
    }

    @Test
    @DisplayName("HU SR-RF-24: Dado un evento de catálogo fuera de orden con updatedAt anterior al existente, Cuando se procesa sincronización o visibilidad, Entonces se descarta sin revertir el estado actual")
    void shouldIgnoreOutOfOrderCatalogGarmentEventsAndPreserveCurrentState() {
        UUID garmentId = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-11-15T10:00:00Z");
        Instant t1 = Instant.parse("2026-11-15T12:00:00Z"); // Estado más reciente: RETIRADA / OCULTA
        Instant t2 = Instant.parse("2026-11-15T14:00:00Z"); // Estado futuro: REACTIVADA

        CatalogGarmentJpaEntity currentEntity = new CatalogGarmentJpaEntity(
                garmentId, "Vestido Elegante", "ONE_PIECE", "https://img/vestido.png", false, t1
        );
        when(catalogGarmentJpaRepository.findById(garmentId)).thenReturn(Optional.of(currentEntity));

        // 1. Evento viejo de publicación (T0 < T1) llega fuera de orden intentando poner visible=true -> Se descarta
        CatalogGarment stalePublishGarment = new CatalogGarment(
                GarmentId.of(garmentId), "Vestido Elegante", GarmentCategory.ONE_PIECE,
                "https://img/vestido.png", true, t0
        );
        CatalogGarment syncedResult = queryTryOnJobsService.syncCatalogGarment(stalePublishGarment);
        assertThat(syncedResult.isVisible()).isFalse();
        assertThat(syncedResult.getUpdatedAt()).isEqualTo(t1);

        // 2. Evento viejo de cambio de visibilidad (T0 < T1) llega fuera de orden -> Se descarta
        Optional<CatalogGarment> staleVisibilityResult = queryTryOnJobsService.updateCatalogGarmentVisibility(
                GarmentId.of(garmentId), true, t0
        );
        assertThat(staleVisibilityResult).isPresent();
        assertThat(staleVisibilityResult.get().isVisible()).isFalse();
        assertThat(staleVisibilityResult.get().getUpdatedAt()).isEqualTo(t1);

        // Se verifica que para los eventos obsoletos nunca se invocó save
        verify(catalogGarmentJpaRepository, never()).save(any(CatalogGarmentJpaEntity.class));

        // 3. Evento más reciente (T2 > T1) -> Sí se guarda y actualiza el estado a visible=true
        CatalogGarment newerGarment = new CatalogGarment(
                GarmentId.of(garmentId), "Vestido Elegante", GarmentCategory.ONE_PIECE,
                "https://img/vestido.png", true, t2
        );
        when(catalogGarmentJpaRepository.save(any(CatalogGarmentJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CatalogGarment newerResult = queryTryOnJobsService.syncCatalogGarment(newerGarment);
        assertThat(newerResult.isVisible()).isTrue();
        assertThat(newerResult.getUpdatedAt()).isEqualTo(t2);
        verify(catalogGarmentJpaRepository).save(any(CatalogGarmentJpaEntity.class));
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
        verify(redisTemplate, org.mockito.Mockito.times(2)).delete(any(String.class));

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

        // 3. Outfit permanente -> genera URL SAS de solo lectura en consulta sin persistir credenciales temporales en try_on_outfits
        TryOnOutfit permanentOutfit = new TryOnOutfit(
                UUID.randomUUID(),
                jobId,
                ownerId,
                SasSignedUrl.of("https://styleradar.blob.core.windows.net/biometric-silhouettes/render-permanent.png"),
                Instant.now().plusSeconds(900),
                Instant.now()
        );
        when(tryOnOutfitJpaRepository.findByJobId(jobId)).thenReturn(Optional.of(mapper.toOutfitJpaEntity(permanentOutfit)));
        SasSignedUrl refreshedUrl = SasSignedUrl.of("https://styleradar.blob.core.windows.net/biometric-silhouettes/render-permanent.png?sr=b&sp=r&spr=https");
        when(blobStoragePort.generateReadOnlySasUrl(any())).thenReturn(refreshedUrl);

        TryOnJob result = queryTryOnJobsService.getJobStatus(jobId, ownerId, false);
        assertEquals(refreshedUrl.value(), result.getResultImageUrl().value());
        // Se valida que las credenciales SAS temporales NO se persistan en PostgreSQL durante la consulta
        verify(tryOnOutfitJpaRepository, never()).save(argThat(entity -> entity != null && entity.getFinalRenderUrl().contains("?")));

        // 4. Consulta por ADMIN -> genera URL firmada al momento de la consulta sin credenciales temporales en BD
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
        when(tryOnOutfitJpaRepository.save(any(TryOnOutfitJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
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

        TryOnOutfit validOutfit = permanentOutfit;
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
                .withCategory(GarmentCategory.TOP)
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
                edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory.TOP,
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
        org.mockito.Mockito.verify(jobRepo).deleteOutfitByJobId(jobId);
        org.mockito.Mockito.verify(jobRepo, org.mockito.Mockito.atLeastOnce()).saveJob(job);
        org.mockito.Mockito.verify(jobRepo, org.mockito.Mockito.atLeastOnce()).saveAuditLog(any());
        assertTrue(job.isPhotoPurged());
        assertEquals("PURGED_BY_USER_REQUEST", job.getPhotoUrl());
        org.junit.jupiter.api.Assertions.assertNull(job.getResultImageUrl());
    }

    @Test
    @DisplayName("HU SR-RF-27 (Escenario 3): purgeJobBiometricPhoto lanza JobNotFoundException si el trabajo ya fue purgado")
    void shouldThrowJobNotFoundExceptionWhenJobWasAlreadyPurgedInQueryService() {
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
        TryOnJob purgedJob = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(ownerId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("PURGED_BY_USER_REQUEST")
                .build();
        purgedJob.purgeBiometricPhoto();

        when(jobRepo.findJobById(jobId)).thenReturn(Optional.of(purgedJob));

        assertThrows(JobNotFoundException.class,
                () -> service.purgeJobBiometricPhoto(jobId, ownerId, false));
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
                edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory.TOP,
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
                edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory.TOP,
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
                edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory.TOP,
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
        verify(jobRepo).deleteOutfitByJobId(jobId);
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
                edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory.TOP,
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

    @Test
    @DisplayName("HU SR-RF-24 / SR-RF-108: Eventos AMQP de ms-pagos actualizan a Radar+ al confirmar pago y revierten al suspender")
    void shouldUpdateQuotaPlanFromPaymentAndSubscriptionEvents() throws Exception {
        UUID userUuid = UserId.parseToUuid(12L);
        java.util.concurrent.atomic.AtomicReference<UserQuotaJpaEntity> storedQuota =
                new java.util.concurrent.atomic.AtomicReference<>();

        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userUuid), any()))
                .thenAnswer(inv -> Optional.ofNullable(storedQuota.get()));
        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(eq(userUuid), any()))
                .thenAnswer(inv -> Optional.ofNullable(storedQuota.get()));
        lenient().when(quotaJpaRepository.save(any(UserQuotaJpaEntity.class)))
                .thenAnswer(inv -> {
                    UserQuotaJpaEntity entity = inv.getArgument(0);
                    storedQuota.set(entity);
                    return entity;
                });

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        // 1. Pago confirmado de usuario comprador (Radar+, planId=2) -> activa Radar+ (50)
        String paymentConfirmedJson = """
                {
                  "eventId": "%s",
                  "eventType": "styleradar.pagos.pago.confirmado",
                  "timestamp": "2026-11-21T19:00:00Z",
                  "data": {
                    "usuarioId": 12,
                    "suscriptorId": "12",
                    "tipoSuscriptor": "USER",
                    "planId": 2,
                    "estado": "APPROVED"
                  }
                }
                """.formatted(UUID.randomUUID());

        UserSubscriptionPlanEvent confirmedEvent = mapper.readValue(paymentConfirmedJson, UserSubscriptionPlanEvent.class);
        eventListenerAdapter.onPaymentConfirmed(confirmedEvent);

        assertNotNull(storedQuota.get());
        assertTrue(storedQuota.get().isRadarPlus());
        assertEquals(UserQuota.RADAR_PLUS_MONTHLY_LIMIT, storedQuota.get().getMonthlyLimit());

        // 2. Pago de tienda (tipoSuscriptor=STORE, planId=3) -> ignorado limpiamente sin tocar cuotas
        String storePaymentJson = """
                {
                  "eventType": "styleradar.pagos.pago.confirmado",
                  "data": {
                    "suscriptorId": "store-44",
                    "tipoSuscriptor": "STORE",
                    "planId": 3,
                    "estado": "APPROVED"
                  }
                }
                """;
        UserSubscriptionPlanEvent storeEvent = mapper.readValue(storePaymentJson, UserSubscriptionPlanEvent.class);
        eventListenerAdapter.onPaymentConfirmed(storeEvent);
        eventListenerAdapter.onSubscriptionSuspended(storeEvent);
        verify(quotaJpaRepository, org.mockito.Mockito.times(1)).save(any(UserQuotaJpaEntity.class));

        // 3. Suscripción suspendida de usuario comprador -> revierte a plan gratuito (10)
        String suspendedJson = """
                {
                  "eventId": "%s",
                  "eventType": "styleradar.pagos.suscripcion.suspendida",
                  "timestamp": "2026-11-21T19:10:00Z",
                  "data": {
                    "suscriptorId": "12",
                    "tipoSuscriptor": "USER",
                    "planId": 2
                  }
                }
                """.formatted(UUID.randomUUID());

        UserSubscriptionPlanEvent suspendedEvent = mapper.readValue(suspendedJson, UserSubscriptionPlanEvent.class);
        eventListenerAdapter.onSubscriptionSuspended(suspendedEvent);

        assertFalse(storedQuota.get().isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, storedQuota.get().getMonthlyLimit());
        verify(quotaJpaRepository, org.mockito.Mockito.times(2)).save(any(UserQuotaJpaEntity.class));

        // 4. Pago no aprobado (REJECTED) -> no actualiza cuota
        eventListenerAdapter.onPaymentConfirmed(new UserSubscriptionPlanEvent(
                userUuid, "USER", 2L, "REJECTED", Instant.now()
        ));
        verify(quotaJpaRepository, org.mockito.Mockito.times(2)).save(any(UserQuotaJpaEntity.class));

        // 5. Payloads inválidos (null o USER sin userId) -> DLQ
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> eventListenerAdapter.onPaymentConfirmed(null));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> eventListenerAdapter.onPaymentConfirmed(new UserSubscriptionPlanEvent(
                        null, "USER", 2L, "APPROVED", Instant.now()
                )));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> eventListenerAdapter.onSubscriptionSuspended(null));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> eventListenerAdapter.onSubscriptionSuspended(new UserSubscriptionPlanEvent(
                        null, "USER", 2L, null, Instant.now()
                )));

        // 6. Método default de GetUserQuotaUseCase.updateUserSubscriptionPlan delega en getUserQuota
        edu.dosw.styleradar.virtualtryon.domain.port.in.GetUserQuotaUseCase defaultPort =
                new edu.dosw.styleradar.virtualtryon.domain.port.in.GetUserQuotaUseCase() {
                    @Override
                    public UserQuota getUserQuota(UserId uid) {
                        return UserQuota.createDefault(uid, BillingPeriod.current());
                    }
                    @Override
                    public GarmentValidationResult validateGarmentEligibility(UserId uid, GarmentId gid, List<GarmentId> list) {
                        return null;
                    }
                    @Override
                    public CatalogGarment syncCatalogGarment(CatalogGarment g) {
                        return g;
                    }
                };
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT,
                defaultPort.updateUserSubscriptionPlan(UserId.of(userUuid), true).getMonthlyLimit());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT,
                defaultPort.updateUserSubscriptionPlan(UserId.of(userUuid), true, Instant.now()).getMonthlyLimit());
    }

    @Test
    @DisplayName("HU SR-RF-108 (Caso 1): Solo un pago aprobado de Radar+ aumenta la cuota; otro plan o evento incompleto no la suben")
    void shouldOnlyUpgradeQuotaOnApprovedRadarPlusPayment() {
        UUID userOtherPlanUuid = UserId.parseToUuid(21L);
        UUID userIncompleteUuid = UserId.parseToUuid(22L);
        UUID userRadarPlusUuid = UserId.parseToUuid(23L);
        java.util.Map<UUID, UserQuotaJpaEntity> quotasByUser = new java.util.concurrent.ConcurrentHashMap<>();

        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriod(any(), any()))
                .thenAnswer(inv -> Optional.ofNullable(quotasByUser.get(inv.getArgument(0))));
        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(any(), any()))
                .thenAnswer(inv -> Optional.ofNullable(quotasByUser.get(inv.getArgument(0))));
        lenient().when(quotaJpaRepository.save(any(UserQuotaJpaEntity.class)))
                .thenAnswer(inv -> {
                    UserQuotaJpaEntity entity = inv.getArgument(0);
                    quotasByUser.put(entity.getUserId(), entity);
                    return entity;
                });

        // 1. Plan distinto (planId=1) con estado APPROVED -> no debe activar Radar+
        eventListenerAdapter.onPaymentConfirmed(new UserSubscriptionPlanEvent(
                userOtherPlanUuid, "USER", 1L, "APPROVED", Instant.parse("2026-11-21T19:00:00Z")
        ));
        assertFalse(quotasByUser.containsKey(userOtherPlanUuid));

        // 2. Evento incompleto (sin planId ni estado, solo userId válido) -> no debe activar Radar+
        eventListenerAdapter.onPaymentConfirmed(new UserSubscriptionPlanEvent(
                userIncompleteUuid, null, null, null, Instant.parse("2026-11-21T19:01:00Z")
        ));
        eventListenerAdapter.onPaymentConfirmed(new UserSubscriptionPlanEvent(
                userIncompleteUuid, "USER", 2L, null, Instant.parse("2026-11-21T19:02:00Z")
        ));
        assertFalse(quotasByUser.containsKey(userIncompleteUuid));

        // 3. Pago válido de Radar+ (planId=2, status=APPROVED) -> único que sube la cuota a 50
        eventListenerAdapter.onPaymentConfirmed(new UserSubscriptionPlanEvent(
                userRadarPlusUuid, "USER", 2L, "APPROVED", Instant.parse("2026-11-21T19:05:00Z")
        ));
        UserQuotaJpaEntity upgraded = quotasByUser.get(userRadarPlusUuid);
        assertNotNull(upgraded);
        assertTrue(upgraded.isRadarPlus());
        assertEquals(UserQuota.RADAR_PLUS_MONTHLY_LIMIT, upgraded.getMonthlyLimit());
        verify(quotaJpaRepository, org.mockito.Mockito.times(1)).save(any(UserQuotaJpaEntity.class));
    }

    @Test
    @DisplayName("HU SR-RF-108 (Caso 2): Reentrega de pago anterior tras una suspensión más reciente mantiene el plan gratuito")
    void shouldIgnoreOutOfOrderOlderPaymentAfterNewerSuspension() {
        UUID userUuid = UserId.parseToUuid(31L);
        java.util.concurrent.atomic.AtomicReference<UserQuotaJpaEntity> storedQuota =
                new java.util.concurrent.atomic.AtomicReference<>();

        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userUuid), any()))
                .thenAnswer(inv -> Optional.ofNullable(storedQuota.get()));
        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(eq(userUuid), any()))
                .thenAnswer(inv -> Optional.ofNullable(storedQuota.get()));
        lenient().when(quotaJpaRepository.save(any(UserQuotaJpaEntity.class)))
                .thenAnswer(inv -> {
                    UserQuotaJpaEntity entity = inv.getArgument(0);
                    storedQuota.set(entity);
                    return entity;
                });

        Instant paymentTime = Instant.parse("2026-11-21T19:00:00Z");
        Instant suspensionTime = Instant.parse("2026-11-21T19:10:00Z");

        UserSubscriptionPlanEvent paymentEvent = new UserSubscriptionPlanEvent(
                userUuid, "USER", 2L, "APPROVED", paymentTime
        );
        UserSubscriptionPlanEvent suspensionEvent = new UserSubscriptionPlanEvent(
                userUuid, "USER", 2L, null, suspensionTime
        );

        // 1. Pago inicial -> activa Radar+
        eventListenerAdapter.onPaymentConfirmed(paymentEvent);
        assertTrue(storedQuota.get().isRadarPlus());
        assertEquals(50, storedQuota.get().getMonthlyLimit());

        // 2. Suspensión más reciente -> baja a plan gratuito
        eventListenerAdapter.onSubscriptionSuspended(suspensionEvent);
        assertFalse(storedQuota.get().isRadarPlus());
        assertEquals(10, storedQuota.get().getMonthlyLimit());
        assertEquals(suspensionTime, storedQuota.get().getPlanUpdatedAt());

        // 3. Reentrega tardía del mismo pago anterior -> ignorado, sigue en plan gratuito
        eventListenerAdapter.onPaymentConfirmed(paymentEvent);
        assertFalse(storedQuota.get().isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, storedQuota.get().getMonthlyLimit());
        assertEquals(suspensionTime, storedQuota.get().getPlanUpdatedAt());
        verify(quotaJpaRepository, org.mockito.Mockito.times(2)).save(any(UserQuotaJpaEntity.class));
    }

    @Test
    @DisplayName("RabbitMQ y cuotas: Un evento sin timestamp no modifica una suscripción activa más reciente")
    void shouldNotModifyActiveSubscriptionWhenEventLacksTimestamp() {
        UUID userUuid = UserId.parseToUuid(55L);
        java.util.concurrent.atomic.AtomicReference<UserQuotaJpaEntity> storedQuota =
                new java.util.concurrent.atomic.AtomicReference<>();

        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userUuid), any()))
                .thenAnswer(inv -> Optional.ofNullable(storedQuota.get()));
        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(eq(userUuid), any()))
                .thenAnswer(inv -> Optional.ofNullable(storedQuota.get()));
        lenient().when(quotaJpaRepository.save(any(UserQuotaJpaEntity.class)))
                .thenAnswer(inv -> {
                    UserQuotaJpaEntity entity = inv.getArgument(0);
                    storedQuota.set(entity);
                    return entity;
                });

        Instant validPaymentTime = Instant.parse("2026-11-25T10:00:00Z");

        // 1. Usuario adquiere Radar+ con timestamp válido
        eventListenerAdapter.onPaymentConfirmed(new UserSubscriptionPlanEvent(
                userUuid, "USER", 2L, "APPROVED", validPaymentTime
        ));
        assertNotNull(storedQuota.get());
        assertTrue(storedQuota.get().isRadarPlus());
        assertEquals(UserQuota.RADAR_PLUS_MONTHLY_LIMIT, storedQuota.get().getMonthlyLimit());
        assertEquals(validPaymentTime, storedQuota.get().getPlanUpdatedAt());

        // 2. Llega un evento antiguo o reentregado SIN timestamp (null) que intenta suspender el plan
        UserSubscriptionPlanEvent unversionedEvent = new UserSubscriptionPlanEvent(
                userUuid, "USER", 2L, "SUSPENDED", null
        );
        eventListenerAdapter.onSubscriptionSuspended(unversionedEvent);

        // 3. La suscripción activa DEBE conservarse intacta (Radar+, límite 50, planUpdatedAt preservado)
        assertTrue(storedQuota.get().isRadarPlus());
        assertEquals(UserQuota.RADAR_PLUS_MONTHLY_LIMIT, storedQuota.get().getMonthlyLimit());
        assertEquals(validPaymentTime, storedQuota.get().getPlanUpdatedAt());

        // 4. Mismo comportamiento si llega un evento de pago sin timestamp intentando alterar
        UserSubscriptionPlanEvent unversionedPayment = new UserSubscriptionPlanEvent(
                userUuid, "USER", 1L, "APPROVED", null
        );
        eventListenerAdapter.onPaymentConfirmed(unversionedPayment);
        assertTrue(storedQuota.get().isRadarPlus());
        assertEquals(UserQuota.RADAR_PLUS_MONTHLY_LIMIT, storedQuota.get().getMonthlyLimit());
    }

    @Test
    @DisplayName("HU SR-RF-108 (Caso 3): Cambio de mes reinicia el consumo y conserva Radar+ mientras la suscripción siga activa")
    void shouldPreserveRadarPlusPlanAndResetUsageOnNewBillingPeriod() {
        UserId userId = UserId.fromFlexible("45");
        UUID userUuid = userId.value();
        java.util.Map<String, UserQuotaJpaEntity> quotasByPeriod = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.atomic.AtomicReference<BillingPeriod> activePeriod =
                new java.util.concurrent.atomic.AtomicReference<>(BillingPeriod.of("2026-11"));

        queryTryOnJobsService.setBillingPeriodSupplier(activePeriod::get);

        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userUuid), any()))
                .thenAnswer(inv -> Optional.ofNullable(quotasByPeriod.get(inv.getArgument(1))));
        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(eq(userUuid), any()))
                .thenAnswer(inv -> Optional.ofNullable(quotasByPeriod.get(inv.getArgument(1))));
        lenient().when(quotaJpaRepository.findFirstByUserIdOrderByBillingPeriodDescLastResetAtDesc(userUuid))
                .thenAnswer(inv -> quotasByPeriod.values().stream()
                        .max(java.util.Comparator.comparing(UserQuotaJpaEntity::getBillingPeriod)
                                .thenComparing(UserQuotaJpaEntity::getLastResetAt)));
        lenient().when(quotaJpaRepository.save(any(UserQuotaJpaEntity.class)))
                .thenAnswer(inv -> {
                    UserQuotaJpaEntity entity = inv.getArgument(0);
                    quotasByPeriod.put(entity.getBillingPeriod(), entity);
                    return entity;
                });

        // 1. En noviembre el usuario paga Radar+ y consume 7 intentos
        Instant paymentAt = Instant.parse("2026-11-10T15:00:00Z");
        eventListenerAdapter.onPaymentConfirmed(new UserSubscriptionPlanEvent(
                userUuid, "USER", 2L, "APPROVED", paymentAt
        ));
        UserQuotaJpaEntity novemberQuota = quotasByPeriod.get("2026-11");
        assertNotNull(novemberQuota);
        novemberQuota.setUsedCount(7);
        quotasByPeriod.put("2026-11", novemberQuota);

        // 2. Cambia al mes siguiente (diciembre) sin nuevo evento de Pagos
        activePeriod.set(BillingPeriod.of("2026-12"));
        UserQuota decemberQuota = queryTryOnJobsService.getUserQuota(userId);

        assertEquals("2026-12", decemberQuota.getBillingPeriod().getValue());
        assertEquals(0, decemberQuota.getUsedCount());
        assertEquals(50, decemberQuota.getRemainingCount());
        assertTrue(decemberQuota.isRadarPlus());
        assertEquals(UserQuota.RADAR_PLUS_MONTHLY_LIMIT, decemberQuota.getMonthlyLimit());
        assertEquals(paymentAt, decemberQuota.getPlanUpdatedAt());
    }

    @Test
    @DisplayName("HU SR-RF-108 (Caso 4): Pago aprobado de Radar+ sin occurredAt tras una suspensión conserva el plan gratuito")
    void shouldKeepFreePlanWhenPaymentArrivesWithoutOccurredAtAfterSuspension() {
        UUID userUuid = UserId.parseToUuid(55L);
        BillingPeriod currentPeriod = BillingPeriod.current();
        Instant suspensionTime = Instant.parse("2026-11-21T19:10:00Z");

        UserQuotaJpaEntity initialQuota = new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userUuid,
                UserQuota.DEFAULT_FREE_MONTHLY_LIMIT,
                3,
                currentPeriod.getValue(),
                false,
                Instant.now(),
                suspensionTime
        );
        java.util.concurrent.atomic.AtomicReference<UserQuotaJpaEntity> storedQuota =
                new java.util.concurrent.atomic.AtomicReference<>(initialQuota);

        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(eq(userUuid), any()))
                .thenAnswer(inv -> Optional.ofNullable(storedQuota.get()));
        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userUuid), any()))
                .thenAnswer(inv -> Optional.ofNullable(storedQuota.get()));
        lenient().when(quotaJpaRepository.save(any(UserQuotaJpaEntity.class)))
                .thenAnswer(inv -> {
                    UserQuotaJpaEntity entity = inv.getArgument(0);
                    storedQuota.set(entity);
                    return entity;
                });

        // Llega pago aprobado de Radar+ sin timestamp/occurredAt -> NO debe reactivar Radar+ ni usar Instant.now()
        eventListenerAdapter.onPaymentConfirmed(new UserSubscriptionPlanEvent(
                userUuid, "USER", 2L, "APPROVED", null
        ));

        assertFalse(storedQuota.get().isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, storedQuota.get().getMonthlyLimit());
        assertEquals(3, storedQuota.get().getUsedCount());
        assertEquals(suspensionTime, storedQuota.get().getPlanUpdatedAt());
        verify(quotaJpaRepository, never()).save(any(UserQuotaJpaEntity.class));
    }

    @Test
    @DisplayName("HU SR-RF-108 (Caso 5): Pago y suspensión en paralelo preservan la suspensión más reciente y el consumo concurrente")
    void shouldPreserveNewerSuspensionAndUsedCountWhenPaymentAndSuspensionRunInParallel() throws Exception {
        UUID userUuid = UserId.parseToUuid(77L);
        BillingPeriod currentPeriod = BillingPeriod.current();
        Instant olderPaymentTime = Instant.parse("2026-11-21T19:00:00Z");
        Instant newerSuspensionTime = Instant.parse("2026-11-21T19:10:00Z");

        UserQuotaJpaEntity quotaRow = new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userUuid,
                UserQuota.DEFAULT_FREE_MONTHLY_LIMIT,
                4,
                currentPeriod.getValue(),
                false,
                Instant.now(),
                null
        );
        java.util.concurrent.atomic.AtomicReference<UserQuotaJpaEntity> storedQuota =
                new java.util.concurrent.atomic.AtomicReference<>(quotaRow);

        java.util.concurrent.CountDownLatch paymentEnteredService = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch allowPaymentToProceed = new java.util.concurrent.CountDownLatch(1);

        // El hilo del pago entra primero y queda retenido al intentar adquirir el bloqueo en BD mientras se procesa
        // la suspensión y un consumo concurrente; al reanudar, la comparación/actualización atómica descarta el pago obsoleto.
        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(eq(userUuid), any()))
                .thenAnswer(inv -> {
                    if (Thread.currentThread().getName().contains("payment-thread")) {
                        paymentEnteredService.countDown();
                        try {
                            assertTrue(allowPaymentToProceed.await(5, java.util.concurrent.TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return Optional.ofNullable(storedQuota.get());
                });
        lenient().when(quotaJpaRepository.findByUserIdAndBillingPeriod(eq(userUuid), any()))
                .thenAnswer(inv -> Optional.ofNullable(storedQuota.get()));
        lenient().when(quotaJpaRepository.save(any(UserQuotaJpaEntity.class)))
                .thenAnswer(inv -> {
                    UserQuotaJpaEntity entity = inv.getArgument(0);
                    storedQuota.set(entity);
                    return entity;
                });

        UserSubscriptionPlanEvent paymentEvent = new UserSubscriptionPlanEvent(
                userUuid, "USER", 2L, "APPROVED", olderPaymentTime
        );
        UserSubscriptionPlanEvent suspensionEvent = new UserSubscriptionPlanEvent(
                userUuid, "USER", 2L, null, newerSuspensionTime
        );

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<?> paymentFuture = executor.submit(() -> {
                Thread.currentThread().setName("payment-thread");
                eventListenerAdapter.onPaymentConfirmed(paymentEvent);
            });

            assertTrue(paymentEnteredService.await(5, java.util.concurrent.TimeUnit.SECONDS));

            java.util.concurrent.Future<?> suspensionFuture = executor.submit(() -> {
                Thread.currentThread().setName("suspension-thread");
                eventListenerAdapter.onSubscriptionSuspended(suspensionEvent);
                // Simula un consumo concurrente registrado tras aplicar la suspensión (usedCount 4 -> 5)
                storedQuota.get().setUsedCount(5);
            });

            suspensionFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
            allowPaymentToProceed.countDown();
            paymentFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // Prevalece la suspensión más reciente (19:10) y se preserva el consumo concurrente (5)
        assertFalse(storedQuota.get().isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, storedQuota.get().getMonthlyLimit());
        assertEquals(newerSuspensionTime, storedQuota.get().getPlanUpdatedAt());
        assertEquals(5, storedQuota.get().getUsedCount());

        // Además, un cambio posterior válido (19:20) actualiza el plan sin sobrescribir usedCount (5)
        Instant renewalTime = Instant.parse("2026-11-21T19:20:00Z");
        eventListenerAdapter.onPaymentConfirmed(new UserSubscriptionPlanEvent(
                userUuid, "USER", 2L, "APPROVED", renewalTime
        ));
        assertTrue(storedQuota.get().isRadarPlus());
        assertEquals(UserQuota.RADAR_PLUS_MONTHLY_LIMIT, storedQuota.get().getMonthlyLimit());
        assertEquals(renewalTime, storedQuota.get().getPlanUpdatedAt());
        assertEquals(5, storedQuota.get().getUsedCount());
    }

    @Test
    @DisplayName("HU SR-RF-108: Cubrir guardas de TryOnRepositoryAdapter, fallback de período y exclusión de otros planes en suspensión")
    void shouldCoverAtomicQuotaAdapterEdgeCasesAndListenerPlanBranches() {
        UserId userId = UserId.of(UUID.randomUUID());
        BillingPeriod currentPeriod = BillingPeriod.current();
        Instant olderTime = Instant.parse("2026-11-21T18:00:00Z");
        Instant newerTime = Instant.parse("2026-11-21T19:00:00Z");

        // 1. Guardas nulas y constructor sin quotaJpaRepository en TryOnRepositoryAdapter
        assertNull(repositoryAdapter.updateQuotaPlanAtomically(null, currentPeriod, true, newerTime));
        assertNull(repositoryAdapter.updateQuotaPlanAtomically(userId, null, true, newerTime));
        assertNull(repositoryAdapter.updateQuotaPlanAtomically(userId, currentPeriod, true, null));

        TryOnRepositoryAdapter adapterWithoutQuotaRepo = new TryOnRepositoryAdapter(
                consentJpaRepository,
                photoJpaRepository,
                new TryOnPersistenceMapper()
        );
        assertNull(adapterWithoutQuotaRepo.updateQuotaPlanAtomically(userId, currentPeriod, true, newerTime));
        assertTrue(adapterWithoutQuotaRepo.findLatestQuotaByUserId(userId).isEmpty());
        assertTrue(repositoryAdapter.findLatestQuotaByUserId(null).isEmpty());

        // 2. Sin fila en el período actual: período previo con planUpdatedAt más reciente descarta evento antiguo
        UserQuotaJpaEntity newerPreviousPeriodEntity = new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userId.value(),
                UserQuota.RADAR_PLUS_MONTHLY_LIMIT,
                2,
                "2026-10",
                true,
                Instant.now(),
                newerTime
        );
        when(quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(userId.value(), currentPeriod.getValue()))
                .thenReturn(Optional.empty());
        when(quotaJpaRepository.findFirstByUserIdOrderByBillingPeriodDescLastResetAtDesc(userId.value()))
                .thenReturn(Optional.of(newerPreviousPeriodEntity));

        UserQuota staleResult = repositoryAdapter.updateQuotaPlanAtomically(userId, currentPeriod, false, olderTime);
        assertNotNull(staleResult);
        assertTrue(staleResult.isRadarPlus());
        assertEquals(0, staleResult.getUsedCount());
        verify(quotaJpaRepository, never()).save(any(UserQuotaJpaEntity.class));

        // 3. Sin fila previa en BD: crea cuota gratuita por defecto y aplica downgrade
        when(quotaJpaRepository.findFirstByUserIdOrderByBillingPeriodDescLastResetAtDesc(userId.value()))
                .thenReturn(Optional.empty());
        when(quotaJpaRepository.save(any(UserQuotaJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        UserQuota defaultDowngraded = repositoryAdapter.updateQuotaPlanAtomically(userId, currentPeriod, false, olderTime);
        assertFalse(defaultDowngraded.isRadarPlus());
        assertEquals(UserQuota.DEFAULT_FREE_MONTHLY_LIMIT, defaultDowngraded.getMonthlyLimit());
        assertEquals(olderTime, defaultDowngraded.getPlanUpdatedAt());

        // 4. Sin fila en el período actual: período previo con timestamp anterior se actualiza con evento más reciente
        UserQuotaJpaEntity olderPreviousPeriodEntity = new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userId.value(),
                UserQuota.DEFAULT_FREE_MONTHLY_LIMIT,
                1,
                "2026-10",
                false,
                Instant.now(),
                olderTime
        );
        when(quotaJpaRepository.findFirstByUserIdOrderByBillingPeriodDescLastResetAtDesc(userId.value()))
                .thenReturn(Optional.of(olderPreviousPeriodEntity));
        UserQuota upgradedFromPrevious = repositoryAdapter.updateQuotaPlanAtomically(
                userId, currentPeriod, true, newerTime
        );
        assertTrue(upgradedFromPrevious.isRadarPlus());
        assertEquals(UserQuota.RADAR_PLUS_MONTHLY_LIMIT, upgradedFromPrevious.getMonthlyLimit());
        assertEquals(newerTime, upgradedFromPrevious.getPlanUpdatedAt());

        // 5. QueryTryOnJobsServiceImpl: proveedor nulo de período y sobrecarga de 2 argumentos sin occurredAt
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(userId.value(), currentPeriod.getValue()))
                .thenReturn(Optional.empty());
        when(quotaJpaRepository.findFirstByUserIdOrderByBillingPeriodDescLastResetAtDesc(userId.value()))
                .thenReturn(Optional.empty());
        queryTryOnJobsService.setBillingPeriodSupplier(null);
        UserQuota preserved = queryTryOnJobsService.updateUserSubscriptionPlan(userId, false);
        assertFalse(preserved.isRadarPlus());

        // 6. TryOnEventListenerAdapter.onSubscriptionSuspended ignora planes distintos a Radar+ (ej. planId=1)
        UserId otherPlanUser = UserId.of(UUID.randomUUID());
        eventListenerAdapter.onSubscriptionSuspended(new UserSubscriptionPlanEvent(
                otherPlanUser.value(), "USER", 1L, "SUSPENDED", newerTime
        ));
        verify(quotaJpaRepository, never()).findByUserIdAndBillingPeriodForUpdate(eq(otherPlanUser.value()), any());

        // 7. findOrCreateQuotaForUpdate y colisión concurrente de plan tras insertQuotaIfAbsentAndLock
        UserQuotaJpaEntity existingRow = new UserQuotaJpaEntity(
                UUID.randomUUID(),
                userId.value(),
                UserQuota.RADAR_PLUS_MONTHLY_LIMIT,
                3,
                currentPeriod.getValue(),
                true,
                Instant.now(),
                olderTime
        );
        when(quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(userId.value(), currentPeriod.getValue()))
                .thenReturn(Optional.of(existingRow));
        UserQuota existingFound = repositoryAdapter.findOrCreateQuotaForUpdate(userId, currentPeriod);
        assertTrue(existingFound.isRadarPlus());
        assertEquals(3, existingFound.getUsedCount());

        UserId raceUser = UserId.of(UUID.randomUUID());
        UserQuotaJpaEntity winnerRow = new UserQuotaJpaEntity(
                UUID.randomUUID(),
                raceUser.value(),
                UserQuota.RADAR_PLUS_MONTHLY_LIMIT,
                1,
                currentPeriod.getValue(),
                true,
                Instant.now(),
                newerTime
        );
        when(quotaJpaRepository.findByUserIdAndBillingPeriodForUpdate(raceUser.value(), currentPeriod.getValue()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerRow))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerRow));
        when(quotaJpaRepository.findFirstByUserIdOrderByBillingPeriodDescLastResetAtDesc(raceUser.value()))
                .thenReturn(Optional.empty());

        UserQuota createdLocked = repositoryAdapter.findOrCreateQuotaForUpdate(raceUser, currentPeriod);
        assertEquals(1, createdLocked.getUsedCount());
        assertTrue(createdLocked.isRadarPlus());

        UserQuota staleWinner = repositoryAdapter.updateQuotaPlanAtomically(
                raceUser, currentPeriod, false, olderTime
        );
        assertTrue(staleWinner.isRadarPlus());
        assertEquals(newerTime, staleWinner.getPlanUpdatedAt());
        assertEquals(1, staleWinner.getUsedCount());
    }

    @Test
    @DisplayName("HU SR-RF-24: Si la prenda no existe aún en BD local, hidrata desde CatalogGarmentLookupPort, persiste y valida elegibilidad")
    void shouldHydrateCatalogGarmentFromRestFallbackWhenMissingInLocalDatabase() {
        edu.dosw.styleradar.virtualtryon.domain.port.out.CatalogGarmentLookupPort lookupPort =
                mock(edu.dosw.styleradar.virtualtryon.domain.port.out.CatalogGarmentLookupPort.class);
        QueryTryOnJobsServiceImpl serviceWithLookup = new QueryTryOnJobsServiceImpl(
                repositoryAdapter,
                redisQuotaCacheAdapter,
                biometricDataRepositoryPort,
                repositoryAdapter,
                blobStoragePort,
                userMeasurementPort,
                garmentSizeMeasurementPort,
                lookupPort
        );

        UserId userId = UserId.of(UUID.randomUUID());
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        GarmentId missingRemoteId = GarmentId.of(UUID.randomUUID());
        BillingPeriod currentPeriod = BillingPeriod.current();

        when(catalogGarmentJpaRepository.findById(garmentId.value())).thenReturn(Optional.empty());
        CatalogGarment remoteGarment = new CatalogGarment(
                garmentId,
                "Vestido largo negro",
                GarmentCategory.ONE_PIECE,
                "https://cdn.styleradar.co/vestido.jpg",
                true,
                Instant.now(),
                Set.of("S", "M", "L")
        );
        when(lookupPort.findGarmentById(garmentId)).thenReturn(Optional.of(remoteGarment));
        when(catalogGarmentJpaRepository.save(any(CatalogGarmentJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(quotaJpaRepository.findByUserIdAndBillingPeriod(userId.value(), currentPeriod.getValue()))
                .thenReturn(Optional.of(new UserQuotaJpaEntity(
                        UUID.randomUUID(), userId.value(), 10, 0, currentPeriod.getValue(), false, Instant.now()
                )));
        when(biometricDataRepositoryPort.findConsentByUserId(userId))
                .thenReturn(Optional.of(new BiometricConsent(
                        UUID.randomUUID(), userId, "v1.0", true, List.of("VIRTUAL_TRY_ON_INFERENCE"), Instant.now(), null, "127.0.0.1"
                )));
        when(garmentSizeMeasurementPort.findSizeChartByGarmentId(garmentId)).thenReturn(Optional.empty());
        when(userMeasurementPort.findMeasurementsByUserId(userId)).thenReturn(Optional.empty());

        GarmentValidationResult result = serviceWithLookup.validateGarmentEligibility(
                userId, garmentId, "M", List.of()
        );

        assertTrue(result.eligibleForTryOn());
        assertEquals(GarmentCategory.ONE_PIECE, result.garment().getCategory());
        verify(catalogGarmentJpaRepository).save(any(CatalogGarmentJpaEntity.class));

        when(catalogGarmentJpaRepository.findById(missingRemoteId.value())).thenReturn(Optional.empty());
        when(lookupPort.findGarmentById(missingRemoteId)).thenReturn(Optional.empty());
        assertThrows(GarmentNotFoundException.class,
                () -> serviceWithLookup.validateGarmentEligibility(userId, missingRemoteId, "M", List.of()));

        GarmentId unavailableRemoteId = GarmentId.of(UUID.randomUUID());
        when(catalogGarmentJpaRepository.findById(unavailableRemoteId.value())).thenReturn(Optional.empty());
        when(lookupPort.findGarmentById(unavailableRemoteId))
                .thenThrow(new edu.dosw.styleradar.virtualtryon.domain.exception.CatalogServiceUnavailableException(
                        "Catálogo 503"));
        assertThrows(edu.dosw.styleradar.virtualtryon.domain.exception.CatalogServiceUnavailableException.class,
                () -> serviceWithLookup.validateGarmentEligibility(userId, unavailableRemoteId, "M", List.of()));
    }

    @Test
    @DisplayName("RabbitMQ Cuotas: Evento con timestamp vacío/inválido (occurredAt == null) no degrada ni modifica cuota activa de Radar+")
    void shouldNotModifyActiveRadarPlusQuotaWhenTimestampIsNull() {
        UserId radarUser = UserId.of(UUID.randomUUID());
        BillingPeriod currentPeriod = BillingPeriod.current();

        UserQuotaJpaEntity activeEntity = new UserQuotaJpaEntity(
                UUID.randomUUID(),
                radarUser.value(),
                UserQuota.RADAR_PLUS_MONTHLY_LIMIT,
                5,
                currentPeriod.getValue(),
                true,
                Instant.now(),
                Instant.now().minusSeconds(3600)
        );

        when(quotaJpaRepository.findByUserIdAndBillingPeriod(radarUser.value(), currentPeriod.getValue()))
                .thenReturn(Optional.of(activeEntity));

        // Intento de actualización con occurredAt == null (proveniente de un timestamp vacío o corrupto)
        UserQuota preserved = queryTryOnJobsService.updateUserSubscriptionPlan(radarUser, false, null);

        assertTrue(preserved.isRadarPlus(), "La cuota activa de Radar+ no debe ser degradada ante eventos sin timestamp válido");
        assertEquals(UserQuota.RADAR_PLUS_MONTHLY_LIMIT, preserved.getMonthlyLimit());
        verify(quotaJpaRepository, never()).findByUserIdAndBillingPeriodForUpdate(eq(radarUser.value()), any());
    }
}

