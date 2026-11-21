package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotEligibleException;
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
        assertThrows(InvalidGarmentException.class,
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
        assertThat(legacySlugEvent.garmentId()).isNotNull();

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
}

