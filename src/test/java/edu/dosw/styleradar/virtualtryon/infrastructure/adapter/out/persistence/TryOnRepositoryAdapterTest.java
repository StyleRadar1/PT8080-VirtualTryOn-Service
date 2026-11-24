package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence;

import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.CatalogGarmentJpaEntity;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TryOnRepositoryAdapterTest {

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
    private PlatformTransactionManager transactionManager;

    private TryOnRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TryOnRepositoryAdapter(
                consentJpaRepository,
                photoJpaRepository,
                quotaJpaRepository,
                catalogGarmentJpaRepository,
                tryOnJobJpaRepository,
                jobAuditLogJpaRepository,
                tryOnOutfitJpaRepository,
                new TryOnPersistenceMapper(),
                transactionManager
        );
    }

    @Test
    @DisplayName("Si insertRow colisiona por clave duplicada en REQUIRES_NEW, hace rollback de la tx interna y conserva la retirada más reciente")
    void shouldRecoverFromConcurrentInsertCollisionAndPreserveNewerWithdrawal() {
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        Instant olderRestTimestamp = Instant.parse("2026-11-23T18:00:00Z");
        Instant newerWithdrawnTimestamp = Instant.parse("2026-11-23T18:01:00Z");

        CatalogGarment delayedRestGarment = new CatalogGarment(
                garmentId,
                "Vestido Visible REST",
                GarmentCategory.ONE_PIECE,
                "https://cdn.styleradar.co/vestido.jpg",
                true,
                olderRestTimestamp,
                Set.of("M")
        );
        CatalogGarmentJpaEntity concurrentWithdrawnEntity = new CatalogGarmentJpaEntity(
                garmentId.value(),
                "Vestido Retirado RabbitMQ",
                "ONE_PIECE",
                "https://cdn.styleradar.co/vestido.jpg",
                false,
                "M",
                newerWithdrawnTimestamp
        );

        SimpleTransactionStatus innerTxStatus = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(innerTxStatus);
        when(catalogGarmentJpaRepository.findByIdForUpdate(garmentId.value()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrentWithdrawnEntity));
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(catalogGarmentJpaRepository)
                .insertRow(eq(garmentId.value()), anyString(), anyString(), anyString(), eq(true), anyString(), eq(olderRestTimestamp));

        CatalogGarment result = adapter.upsertCatalogGarmentIfNewer(delayedRestGarment);

        assertFalse(result.isVisible());
        assertEquals(newerWithdrawnTimestamp, result.getUpdatedAt());
        verify(transactionManager).rollback(innerTxStatus);
        verify(catalogGarmentJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cuando no existe fila previa, insertRow inserta en REQUIRES_NEW y devuelve la prenda recién persistida")
    void shouldInsertRowInRequiresNewTransactionWhenGarmentIsAbsent() {
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        Instant restTimestamp = Instant.parse("2026-11-23T18:00:00Z");

        CatalogGarment hydratedGarment = new CatalogGarment(
                garmentId,
                "Chaqueta Denim",
                GarmentCategory.TOP,
                "https://cdn.styleradar.co/chaqueta.jpg",
                true,
                restTimestamp,
                Set.of("S", "M")
        );
        CatalogGarmentJpaEntity persistedEntity = new CatalogGarmentJpaEntity(
                garmentId.value(),
                "Chaqueta Denim",
                "TOP",
                "https://cdn.styleradar.co/chaqueta.jpg",
                true,
                "M,S",
                restTimestamp
        );

        SimpleTransactionStatus innerTxStatus = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(innerTxStatus);
        when(catalogGarmentJpaRepository.findByIdForUpdate(garmentId.value()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(persistedEntity));
        when(catalogGarmentJpaRepository.insertRow(
                eq(garmentId.value()),
                anyString(),
                anyString(),
                anyString(),
                anyBoolean(),
                anyString(),
                eq(restTimestamp)
        )).thenReturn(1);

        CatalogGarment result = adapter.upsertCatalogGarmentIfNewer(hydratedGarment);

        assertTrue(result.isVisible());
        assertEquals(restTimestamp, result.getUpdatedAt());
        verify(transactionManager).commit(innerTxStatus);
        verify(catalogGarmentJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Si ya existe la fila bloqueada y llega un evento estrictamente más reciente, actualiza la fila existente")
    void shouldUpdateLockedExistingRowWhenIncomingTimestampIsStrictlyNewer() {
        GarmentId garmentId = GarmentId.of(UUID.randomUUID());
        Instant initialTimestamp = Instant.parse("2026-11-23T18:00:00Z");
        Instant withdrawnTimestamp = Instant.parse("2026-11-23T18:05:00Z");

        CatalogGarmentJpaEntity existingEntity = new CatalogGarmentJpaEntity(
                garmentId.value(),
                "Camisa Blanca",
                "TOP",
                "https://cdn.styleradar.co/camisa.jpg",
                true,
                "M",
                initialTimestamp
        );
        CatalogGarment incomingWithdrawn = new CatalogGarment(
                garmentId,
                "Camisa Retirada",
                GarmentCategory.TOP,
                "https://cdn.styleradar.co/camisa.jpg",
                false,
                withdrawnTimestamp,
                Set.of("M")
        );

        when(catalogGarmentJpaRepository.findByIdForUpdate(garmentId.value()))
                .thenReturn(Optional.of(existingEntity));
        when(catalogGarmentJpaRepository.save(existingEntity)).thenReturn(existingEntity);

        CatalogGarment result = adapter.upsertCatalogGarmentIfNewer(incomingWithdrawn);

        assertFalse(result.isVisible());
        assertEquals(withdrawnTimestamp, result.getUpdatedAt());
        verify(catalogGarmentJpaRepository, never()).insertRow(any(), any(), any(), any(), anyBoolean(), any(), any());
        verify(catalogGarmentJpaRepository).save(existingEntity);
    }
}
