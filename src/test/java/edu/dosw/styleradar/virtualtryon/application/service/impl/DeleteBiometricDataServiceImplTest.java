package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.dto.event.UserAccountDeletedEvent;
import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.application.service.QueryTryOnJobsService;
import edu.dosw.styleradar.virtualtryon.domain.event.TryOnJobDomainEvent;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.TryOnEventPublisherPort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.amqp.TryOnEventListenerAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.TryOnRepositoryAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.JobAuditLogJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.TryOnJobJpaEntity;
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
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para DeleteBiometricDataServiceImpl y purga automática por evento
 * user.account.deleted en cumplimiento de la Ley 1581 de 2012 (Habeas Data · SR-RF-27).
 */
@ExtendWith(MockitoExtension.class)
class DeleteBiometricDataServiceImplTest {

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
    private TryOnEventPublisherPort eventPublisherPort;

    @Mock
    private QueryTryOnJobsService queryTryOnJobsService;

    private TryOnPersistenceMapper mapper;
    private TryOnRepositoryAdapter repositoryAdapter;
    private DeleteBiometricDataServiceImpl deleteService;
    private TryOnEventListenerAdapter eventListenerAdapter;

    @BeforeEach
    void setUp() {
        mapper = new TryOnPersistenceMapper();
        repositoryAdapter = new TryOnRepositoryAdapter(
                consentJpaRepository,
                photoJpaRepository,
                quotaJpaRepository,
                catalogGarmentJpaRepository,
                tryOnJobJpaRepository,
                jobAuditLogJpaRepository,
                tryOnOutfitJpaRepository,
                mapper
        );
        deleteService = new DeleteBiometricDataServiceImpl(
                repositoryAdapter,
                repositoryAdapter,
                blobStoragePort,
                eventPublisherPort
        );
        eventListenerAdapter = new TryOnEventListenerAdapter(queryTryOnJobsService, deleteService);

        lenient().when(tryOnJobJpaRepository.save(any(TryOnJobJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jobAuditLogJpaRepository.save(any(JobAuditLogJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("HU SR-RF-27 (Ley 1581): Dado un TryOnJob existente y el titular autenticado, Cuando invoca deleteBiometricPhoto, Entonces purga los blobs en Azure, marca photoPurged=true, elimina el outfit, guarda auditoría PHOTO_PURGED y publica evento")
    void shouldPurgeJobAndBiometricPhotoWhenOwnerRequestsDeletion() {
        UserId ownerId = UserId.of(UUID.randomUUID());
        UUID jobId = UUID.randomUUID();
        TryOnJob job = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(ownerId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/silhouette.png?sr=b&sp=r&spr=https")
                .build();
        job.complete(
                SasSignedUrl.of("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/render.png?sr=b&sp=r&spr=https"),
                1200L
        );
        TryOnJobJpaEntity jobEntity = mapper.toJobJpaEntity(job);
        BiometricPhoto photo = new BiometricPhoto(
                UUID.randomUUID(),
                ownerId,
                "image/png",
                400,
                600,
                4096L,
                SasSignedUrl.of("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/silhouette.png?sr=b&sp=r&spr=https"),
                Instant.now()
        );

        when(tryOnJobJpaRepository.findById(jobId)).thenReturn(Optional.of(jobEntity));
        when(photoJpaRepository.findByUserId(ownerId.value())).thenReturn(Optional.of(mapper.toPhotoJpaEntity(photo)));

        deleteService.deleteBiometricPhoto(jobId, ownerId, false);

        verify(tryOnJobJpaRepository).save(any(TryOnJobJpaEntity.class));
        verify(tryOnOutfitJpaRepository).deleteByJobId(jobId);
        verify(photoJpaRepository).deleteByUserId(ownerId.value());
        verify(jobAuditLogJpaRepository).save(any(JobAuditLogJpaEntity.class));
        verify(eventPublisherPort).publishJobEvent(any(TryOnJobDomainEvent.class));
    }

    @Test
    @DisplayName("HU SR-RF-27: Dado un photoId directo o un usuario sin permisos o un ID inexistente, Cuando se invoca deleteBiometricPhoto, Entonces valida titularidad (403), elimina por photoId o lanza JobNotFoundException (404)")
    void shouldDeleteByDirectPhotoIdAndEnforceOwnershipAndNotFound() {
        UserId ownerId = UserId.of(UUID.randomUUID());
        UserId intruderId = UserId.of(UUID.randomUUID());
        UUID jobId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        TryOnJob job = new TryOnJobBuilder()
                .withId(jobId)
                .withUserId(ownerId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/s.png?sp=rw")
                .build();
        when(tryOnJobJpaRepository.findById(jobId)).thenReturn(Optional.of(mapper.toJobJpaEntity(job)));

        // Intruso intentando borrar job de otro usuario -> AccessDeniedException
        assertThrows(AccessDeniedException.class,
                () -> deleteService.deleteBiometricPhoto(jobId, intruderId, false));

        // Admin puede borrar job de otro usuario (incluso si photoUrl original tenía permisos no-lectura)
        deleteService.deleteBiometricPhoto(jobId, intruderId, true);

        // Borrado directo por photoId
        BiometricPhoto photo = new BiometricPhoto(
                photoId,
                ownerId,
                "image/png",
                400,
                600,
                4096L,
                SasSignedUrl.of("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/p.png?sr=b&sp=r&spr=https"),
                Instant.now()
        );
        BiometricPhotoJpaEntity photoEntity = mapper.toPhotoJpaEntity(photo);
        when(tryOnJobJpaRepository.findById(photoId)).thenReturn(Optional.empty());
        when(photoJpaRepository.findById(photoId)).thenReturn(Optional.of(photoEntity));

        assertThrows(AccessDeniedException.class,
                () -> deleteService.deleteBiometricPhoto(photoId, intruderId, false));

        deleteService.deleteBiometricPhoto(photoId, ownerId, false);
        verify(photoJpaRepository).deleteByUserId(ownerId.value());

        // ID inexistente ni en jobs ni en photos -> JobNotFoundException
        UUID missingId = UUID.randomUUID();
        when(tryOnJobJpaRepository.findById(missingId)).thenReturn(Optional.empty());
        when(photoJpaRepository.findById(missingId)).thenReturn(Optional.empty());
        assertThrows(JobNotFoundException.class,
                () -> deleteService.deleteBiometricPhoto(missingId, ownerId, false));
    }

    @Test
    @DisplayName("HU SR-RF-27: Dado el evento AMQP user.account.deleted, Cuando TryOnEventListenerAdapter lo recibe, Entonces purga todas las fotografías y trabajos del usuario")
    void shouldPurgeAllBiometricDataWhenUserAccountDeletedEventReceived() {
        UserId userId = UserId.of(UUID.randomUUID());
        TryOnJob activeJob = new TryOnJobBuilder()
                .withId(UUID.randomUUID())
                .withUserId(userId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/s1.png?sr=b&sp=r&spr=https")
                .build();
        TryOnJob alreadyPurgedJob = new TryOnJobBuilder()
                .withId(UUID.randomUUID())
                .withUserId(userId)
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/s2.png?sr=b&sp=r&spr=https")
                .build();
        alreadyPurgedJob.purgeBiometricPhoto();

        BiometricPhoto photo = new BiometricPhoto(
                UUID.randomUUID(),
                userId,
                "image/png",
                400,
                600,
                4096L,
                SasSignedUrl.of("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/s1.png?sr=b&sp=r&spr=https"),
                Instant.now()
        );
        when(photoJpaRepository.findByUserId(userId.value())).thenReturn(Optional.of(mapper.toPhotoJpaEntity(photo)));
        when(tryOnJobJpaRepository.findByUserIdOrderByCreatedAtDesc(userId.value())).thenReturn(List.of(
                mapper.toJobJpaEntity(activeJob),
                mapper.toJobJpaEntity(alreadyPurgedJob)
        ));

        eventListenerAdapter.onUserAccountDeleted(new UserAccountDeletedEvent(userId.value(), Instant.now()));
        eventListenerAdapter.onUserAccountDeleted(null);
        eventListenerAdapter.onUserAccountDeleted(new UserAccountDeletedEvent(null, Instant.now()));

        TryOnEventListenerAdapter listenerWithoutDeleteService = new TryOnEventListenerAdapter(queryTryOnJobsService);
        listenerWithoutDeleteService.onUserAccountDeleted(new UserAccountDeletedEvent(userId.value(), Instant.now()));

        assertTrue(alreadyPurgedJob.isPhotoPurged());
        verify(eventPublisherPort).publishJobEvent(any(TryOnJobDomainEvent.class));
    }
}
