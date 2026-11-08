package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external.AzureBlobStorageAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.TryOnRepositoryAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricConsentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricPhotoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias y de adaptadores para BiometricConsentServiceImpl, TryOnRepositoryAdapter
 * y AzureBlobStorageAdapter (SR-RF-23).
 */
@ExtendWith(MockitoExtension.class)
class BiometricConsentServiceImplTest {

    private static final UUID RAW_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UserId USER_ID = UserId.of(RAW_USER_ID);

    @Mock
    private BiometricConsentJpaRepository consentJpaRepository;

    @Mock
    private BiometricPhotoJpaRepository photoJpaRepository;

    private TryOnPersistenceMapper persistenceMapper;
    private TryOnRepositoryAdapter fallbackRepositoryAdapter;
    private AzureBlobStorageAdapter blobStorageAdapter;
    private BiometricConsentServiceImpl consentService;

    @BeforeEach
    void setUp() {
        persistenceMapper = new TryOnPersistenceMapper();
        fallbackRepositoryAdapter = new TryOnRepositoryAdapter(null, null, persistenceMapper);
        blobStorageAdapter = new AzureBlobStorageAdapter("https://styleradarblob.blob.core.windows.net/");
        consentService = new BiometricConsentServiceImpl(fallbackRepositoryAdapter, blobStorageAdapter);
    }

    private static byte[] createVerticalPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Positivo): Dado un usuario que otorga consentimiento biométrico, Cuando se consulta y luego se revoca, Entonces actualiza su estado de validez")
    void shouldGrantGetAndRevokeConsent() {
        BiometricConsent granted = consentService.grantConsent(
                USER_ID,
                "v1.0-LEY1581-2026",
                true,
                List.of("VIRTUAL_TRY_ON_INFERENCE"),
                "190.24.10.1"
        );

        assertThat(granted.isValidForInference()).isTrue();
        assertThat(granted.getIpAddress()).isEqualTo("190.24.10.1");

        BiometricConsent fetched = consentService.getConsentByUserId(USER_ID);
        assertThat(fetched.getConsentId()).isEqualTo(granted.getConsentId());

        BiometricConsent revoked = consentService.revokeConsent(USER_ID);
        assertThat(revoked.isValidForInference()).isFalse();
        assertThat(revoked.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Negativo): Dado un usuario que rechaza el consentimiento (accepted=false) o no existe, Cuando se invoca el servicio, Entonces lanza ConsentRequiredException")
    void shouldThrowWhenConsentRejectedOrMissing() {
        assertThatThrownBy(() -> consentService.grantConsent(USER_ID, "v1.0", false, List.of(), "127.0.0.1"))
                .isInstanceOf(ConsentRequiredException.class);

        assertThatThrownBy(() -> consentService.getConsentByUserId(USER_ID))
                .isInstanceOf(ConsentRequiredException.class);
    }

    @Test
    @DisplayName("HU SR-RF-23 (Positivo - Carga Foto): Dada una foto PNG vertical 400x600 px con consentimiento, Cuando se sube, Entonces la almacena en Blob Storage y persiste sus metadatos")
    void shouldUploadAndRetrieveBiometricPhoto() throws Exception {
        byte[] photoBytes = createVerticalPng(400, 600);

        BiometricPhoto uploaded = consentService.uploadPhoto(USER_ID, true, "image/png", photoBytes);

        assertThat(uploaded.getWidth()).isEqualTo(400);
        assertThat(uploaded.getHeight()).isEqualTo(600);
        assertThat(uploaded.getPhotoUrl().value()).contains("biometric-silhouettes");

        BiometricPhoto retrieved = consentService.getPhotoByUserId(USER_ID);
        assertThat(retrieved.getPhotoId()).isEqualTo(uploaded.getPhotoId());

        Optional<byte[]> storedBytes = blobStorageAdapter.retrieveBlobBytes(uploaded.getPhotoUrl());
        assertThat(storedBytes).isPresent();

        BiometricPhoto secondUpload = consentService.uploadPhoto(USER_ID, true, "image/png", createVerticalPng(500, 700));
        assertThat(secondUpload.getWidth()).isEqualTo(500);
        assertThat(blobStorageAdapter.retrieveBlobBytes(uploaded.getPhotoUrl())).isEmpty();

        blobStorageAdapter.deleteBlob(secondUpload.getPhotoUrl());
        assertThat(blobStorageAdapter.retrieveBlobBytes(secondUpload.getPhotoUrl())).isEmpty();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Positivo - Consentimiento Previo): Dado un usuario con consentimiento previamente guardado, Cuando sube una foto con flag false, Entonces usa el consentimiento persistido")
    void shouldUploadPhotoWhenConsentAlreadyPersisted() throws Exception {
        consentService.grantConsent(USER_ID, "v1.0", true, List.of("VIRTUAL_TRY_ON_INFERENCE"), "127.0.0.1");
        byte[] photoBytes = createVerticalPng(350, 500);

        BiometricPhoto uploaded = consentService.uploadPhoto(USER_ID, false, "image/jpeg", photoBytes);

        assertThat(uploaded.getContentType()).isEqualTo("image/jpeg");
        assertThat(uploaded.getPhotoUrl().value()).contains(".jpg");
    }

    @Test
    @DisplayName("HU SR-RF-23 (Negativo - Foto No Encontrada e Imagen Inválida): Cuando se consulta una foto inexistente o se sube sin consentimiento, Entonces lanza las excepciones de dominio")
    void shouldFailWhenPhotoNotFoundOrInvalid() throws Exception {
        assertThatThrownBy(() -> consentService.getPhotoByUserId(USER_ID))
                .isInstanceOf(JobNotFoundException.class);

        byte[] photoBytes = createVerticalPng(400, 600);
        assertThatThrownBy(() -> consentService.uploadPhoto(USER_ID, false, "image/png", photoBytes))
                .isInstanceOf(ConsentRequiredException.class);

        assertThatThrownBy(() -> consentService.uploadPhoto(USER_ID, true, "image/png", createVerticalPng(600, 400)))
                .isInstanceOf(InvalidPhotoException.class);
    }

    @Test
    @DisplayName("HU SR-RF-23 (Persistencia JPA): Dado TryOnRepositoryAdapter con repositorios JPA activos, Cuando guarda y consulta consentimiento y foto, Entonces delega en JPA y mapea correctamente")
    void shouldDelegateToJpaRepositoriesWhenPresent() throws Exception {
        TryOnRepositoryAdapter jpaAdapter = new TryOnRepositoryAdapter(
                consentJpaRepository,
                photoJpaRepository,
                persistenceMapper
        );
        BiometricConsentServiceImpl serviceWithJpa = new BiometricConsentServiceImpl(jpaAdapter, blobStorageAdapter);

        when(consentJpaRepository.save(any(BiometricConsentJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BiometricConsent savedConsent = serviceWithJpa.grantConsent(USER_ID, "v1.0", true, List.of("P1", "P2"), "10.0.0.1");
        assertThat(savedConsent.getPurposes()).containsExactly("P1", "P2");

        BiometricConsentJpaEntity entityWithEmptyPurposes = persistenceMapper.toConsentJpaEntity(savedConsent);
        entityWithEmptyPurposes.setPurposes("");
        when(consentJpaRepository.findFirstByUserIdOrderByConsentedAtDesc(RAW_USER_ID))
                .thenReturn(Optional.of(entityWithEmptyPurposes));

        BiometricConsent foundConsent = serviceWithJpa.getConsentByUserId(USER_ID);
        assertThat(foundConsent.getPurposes()).isEmpty();

        BiometricPhoto existingPhoto = new BiometricPhoto(
                UUID.randomUUID(),
                USER_ID,
                "image/png",
                400,
                600,
                100L,
                SasSignedUrl.of("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/old.png"),
                savedConsent.getConsentedAt()
        );
        BiometricPhotoJpaEntity existingPhotoEntity = persistenceMapper.toPhotoJpaEntity(existingPhoto);

        when(photoJpaRepository.findByUserId(RAW_USER_ID)).thenReturn(Optional.of(existingPhotoEntity));
        when(photoJpaRepository.save(any(BiometricPhotoJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] validPng = createVerticalPng(400, 600);
        BiometricPhoto updatedPhoto = serviceWithJpa.uploadPhoto(USER_ID, true, "image/png", validPng);
        assertThat(updatedPhoto.getPhotoId()).isEqualTo(existingPhoto.getPhotoId());

        BiometricPhoto foundPhoto = serviceWithJpa.getPhotoByUserId(USER_ID);
        assertThat(foundPhoto.getWidth()).isEqualTo(400);

        verify(consentJpaRepository).save(any(BiometricConsentJpaEntity.class));
        verify(photoJpaRepository).save(any(BiometricPhotoJpaEntity.class));
    }
}

