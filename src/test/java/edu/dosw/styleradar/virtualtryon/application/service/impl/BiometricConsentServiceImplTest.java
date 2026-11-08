package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.validation.PhotoValidationChainTest;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external.AzureBlobStorageAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.TryOnRepositoryAdapter;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricConsentJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.entity.BiometricPhotoJpaEntity;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricConsentJpaRepository;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence.repository.BiometricPhotoJpaRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Pruebas unitarias y de adaptadores para BiometricConsentServiceImpl, TryOnRepositoryAdapter (JPA)
 * y AzureBlobStorageAdapter (REST API real con RestClient y MockRestServiceServer) (SR-RF-23).
 */
@ExtendWith(MockitoExtension.class)
class BiometricConsentServiceImplTest {

    private static final UUID RAW_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UserId USER_ID = UserId.of(RAW_USER_ID);
    private static final String AZURE_BASE_URL = "https://styleradarblob.blob.core.windows.net";
    private static final String CONTAINER = "biometric-silhouettes";
    private static final String SAS_TOKEN = "?sv=2024-11-04&sr=b&sp=racwd";

    @Mock
    private BiometricConsentJpaRepository consentJpaRepository;

    @Mock
    private BiometricPhotoJpaRepository photoJpaRepository;

    private Map<UUID, BiometricConsentJpaEntity> persistedConsents;
    private Map<UUID, BiometricPhotoJpaEntity> persistedPhotos;

    private TryOnPersistenceMapper persistenceMapper;
    private TryOnRepositoryAdapter repositoryAdapter;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockAzureServer;
    private AzureBlobStorageAdapter blobStorageAdapter;
    private BiometricConsentServiceImpl consentService;

    @BeforeEach
    void setUp() {
        persistedConsents = new ConcurrentHashMap<>();
        persistedPhotos = new ConcurrentHashMap<>();

        lenient().when(consentJpaRepository.save(any(BiometricConsentJpaEntity.class))).thenAnswer(inv -> {
            BiometricConsentJpaEntity entity = inv.getArgument(0);
            persistedConsents.put(entity.getUserId(), entity);
            return entity;
        });
        lenient().when(consentJpaRepository.findFirstByUserIdOrderByConsentedAtDesc(any(UUID.class))).thenAnswer(inv -> {
            UUID userId = inv.getArgument(0);
            return Optional.ofNullable(persistedConsents.get(userId));
        });

        lenient().when(photoJpaRepository.save(any(BiometricPhotoJpaEntity.class))).thenAnswer(inv -> {
            BiometricPhotoJpaEntity entity = inv.getArgument(0);
            persistedPhotos.put(entity.getUserId(), entity);
            return entity;
        });
        lenient().when(photoJpaRepository.findByUserId(any(UUID.class))).thenAnswer(inv -> {
            UUID userId = inv.getArgument(0);
            return Optional.ofNullable(persistedPhotos.get(userId));
        });

        persistenceMapper = new TryOnPersistenceMapper();
        repositoryAdapter = new TryOnRepositoryAdapter(consentJpaRepository, photoJpaRepository, persistenceMapper);

        restClientBuilder = RestClient.builder();
        mockAzureServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        blobStorageAdapter = new AzureBlobStorageAdapter(
                restClientBuilder,
                AZURE_BASE_URL + "/",
                CONTAINER,
                SAS_TOKEN,
                "2024-11-04"
        );
        consentService = new BiometricConsentServiceImpl(repositoryAdapter, blobStorageAdapter);
    }

    @Test
    @DisplayName("HU SR-RF-23 (Positivo): Dado un usuario que otorga consentimiento biométrico, Cuando se consulta y luego se revoca, Entonces persiste en JPA y actualiza su validez")
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
    @DisplayName("HU SR-RF-23 (Positivo - Carga Foto Azure REST API): Dada una foto PNG frontal de cuerpo completo 400x600 px, Cuando se sube, Entonces ejecuta PUT BlockBlob hacia Azure, permite GET/DELETE HTTP y persiste en JPA")
    void shouldUploadAndRetrieveBiometricPhotoViaAzureRestApi() throws Exception {
        byte[] photoBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(400, 600);

        // 1. Expect PUT BlockBlob en Azure Blob Storage para la primera carga
        mockAzureServer.expect(requestTo(Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID)))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_BLOB_TYPE, AzureBlobStorageAdapter.BLOCK_BLOB_TYPE))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_MS_VERSION, "2024-11-04"))
                .andRespond(withSuccess());

        BiometricPhoto uploaded = consentService.uploadPhoto(USER_ID, true, "image/png", photoBytes);

        assertThat(uploaded.getWidth()).isEqualTo(400);
        assertThat(uploaded.getHeight()).isEqualTo(600);
        assertThat(uploaded.getPhotoUrl().value())
                .contains(CONTAINER)
                .contains("sv=2024-11-04")
                .contains("sr=b")
                .contains("sp=r")
                .contains("spr=https")
                .doesNotContain("sp=racwd");

        BiometricPhoto retrieved = consentService.getPhotoByUserId(USER_ID);
        assertThat(retrieved.getPhotoId()).isEqualTo(uploaded.getPhotoId());
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 2. Expect GET Blob desde Azure Blob Storage usando la URL de solo lectura (sp=r)
        mockAzureServer.expect(requestTo(uploaded.getPhotoUrl().value()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_MS_VERSION, "2024-11-04"))
                .andRespond(withSuccess(photoBytes, MediaType.IMAGE_PNG));

        Optional<byte[]> storedBytes = blobStorageAdapter.retrieveBlobBytes(uploaded.getPhotoUrl());
        assertThat(storedBytes).isPresent();
        assertThat(storedBytes.get()).hasSize(photoBytes.length);
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 3. Al subir una segunda foto, elimina la anterior en Azure (DELETE con sasToken interno) y sube la nueva (PUT)
        int queryIdx = uploaded.getPhotoUrl().value().indexOf('?');
        String expectedDeleteUrl = uploaded.getPhotoUrl().value().substring(0, queryIdx) + "?sv=2024-11-04&sr=b&sp=racwd";
        mockAzureServer.expect(requestTo(expectedDeleteUrl))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_DELETE_SNAPSHOTS, "include"))
                .andRespond(withNoContent());

        mockAzureServer.expect(requestTo(Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID)))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_BLOB_TYPE, AzureBlobStorageAdapter.BLOCK_BLOB_TYPE))
                .andRespond(withSuccess());

        BiometricPhoto secondUpload = consentService.uploadPhoto(
                USER_ID,
                true,
                "image/png",
                PhotoValidationChainTest.createFullBodyFrontalPngBytes(500, 750)
        );
        assertThat(secondUpload.getWidth()).isEqualTo(500);
        assertThat(secondUpload.getPhotoId()).isEqualTo(uploaded.getPhotoId());
        mockAzureServer.verify();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Positivo - Consentimiento Previo y sin SAS Token): Dado un usuario con consentimiento persistido, Cuando sube una foto JPEG, Entonces usa el consentimiento previo")
    void shouldUploadPhotoWhenConsentAlreadyPersisted() throws Exception {
        RestClient.Builder noSasBuilder = RestClient.builder();
        MockRestServiceServer noSasServer = MockRestServiceServer.bindTo(noSasBuilder).build();
        AzureBlobStorageAdapter noSasAdapter = new AzureBlobStorageAdapter(
                noSasBuilder,
                AZURE_BASE_URL,
                CONTAINER,
                "   ",
                "2024-11-04"
        );
        BiometricConsentServiceImpl customService = new BiometricConsentServiceImpl(repositoryAdapter, noSasAdapter);

        customService.grantConsent(USER_ID, "v1.0", true, List.of("VIRTUAL_TRY_ON_INFERENCE"), "127.0.0.1");
        byte[] photoBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(350, 520);

        noSasServer.expect(requestTo(Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID)))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        BiometricPhoto uploaded = customService.uploadPhoto(USER_ID, false, "image/jpeg", photoBytes);

        assertThat(uploaded.getContentType()).isEqualTo("image/jpeg");
        assertThat(uploaded.getPhotoUrl().value()).endsWith(".jpg");
        noSasServer.verify();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Errores Azure Blob REST API): Cuando Azure Blob Storage responde 500 en PUT, GET o DELETE, Entonces maneja las fallas de forma controlada")
    void shouldHandleAzureRestClientErrorsGracefully() {
        SasSignedUrl sampleUrl = SasSignedUrl.of(AZURE_BASE_URL + "/" + CONTAINER + "/test.png");

        // Error 500 en PUT lanza IllegalStateException
        mockAzureServer.expect(requestTo(Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID)))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withServerError());

        assertThatThrownBy(() -> blobStorageAdapter.storeBiometricPhoto(USER_ID, "image/png", new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Azure Blob Storage");
        mockAzureServer.verify();
        mockAzureServer.reset();

        // Error 500 en GET retorna Optional.empty()
        mockAzureServer.expect(requestTo(sampleUrl.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThat(blobStorageAdapter.retrieveBlobBytes(sampleUrl)).isEmpty();
        mockAzureServer.verify();
        mockAzureServer.reset();

        // Respuesta vacía en GET retorna Optional.empty()
        mockAzureServer.expect(requestTo(sampleUrl.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[0], MediaType.IMAGE_PNG));

        assertThat(blobStorageAdapter.retrieveBlobBytes(sampleUrl)).isEmpty();
        mockAzureServer.verify();
        mockAzureServer.reset();

        // Error 500 en DELETE no propaga excepción no controlada
        mockAzureServer.expect(requestTo(sampleUrl.value() + "?sv=2024-11-04&sr=b&sp=racwd"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        blobStorageAdapter.deleteBlob(sampleUrl);
        mockAzureServer.verify();

        // Verificar constructor @Autowired de Spring y validación de AZURE_STORAGE_BASE_URL
        AzureBlobStorageAdapter springConstructed = new AzureBlobStorageAdapter(
                AZURE_BASE_URL,
                CONTAINER,
                "",
                "sv=2024-11-04&ss=b&srt=sco&sp=rwdlacx",
                "2024-11-04"
        );
        assertThat(springConstructed).isNotNull();
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(
                "  ",
                CONTAINER,
                "",
                "",
                "2024-11-04"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_BASE_URL");
    }

    @Test
    @DisplayName("HU SR-RF-23 (Negativo - Foto No Encontrada, Sin Consentimiento, Horizontal y Propósitos Vacíos): Valida casos negativos del servicio y mapeador")
    void shouldFailWhenPhotoNotFoundOrInvalidAndMapEmptyPurposes() throws Exception {
        assertThatThrownBy(() -> consentService.getPhotoByUserId(USER_ID))
                .isInstanceOf(JobNotFoundException.class);

        byte[] photoBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(400, 600);
        assertThatThrownBy(() -> consentService.uploadPhoto(USER_ID, false, "image/png", photoBytes))
                .isInstanceOf(ConsentRequiredException.class);

        assertThatThrownBy(() -> consentService.uploadPhoto(
                USER_ID,
                true,
                "image/png",
                PhotoValidationChainTest.createFullBodyFrontalPngBytes(600, 400)
        )).isInstanceOf(InvalidPhotoException.class);

        BiometricConsent savedConsent = consentService.grantConsent(USER_ID, "v1.0", true, List.of("P1", "P2"), "10.0.0.1");
        BiometricConsentJpaEntity entityWithEmptyPurposes = persistenceMapper.toConsentJpaEntity(savedConsent);
        entityWithEmptyPurposes.setPurposes("");
        when(consentJpaRepository.findFirstByUserIdOrderByConsentedAtDesc(RAW_USER_ID))
                .thenReturn(Optional.of(entityWithEmptyPurposes));

        BiometricConsent foundConsent = consentService.getConsentByUserId(USER_ID);
        assertThat(foundConsent.getPurposes()).isEmpty();
        verify(consentJpaRepository).save(any(BiometricConsentJpaEntity.class));
    }
}
