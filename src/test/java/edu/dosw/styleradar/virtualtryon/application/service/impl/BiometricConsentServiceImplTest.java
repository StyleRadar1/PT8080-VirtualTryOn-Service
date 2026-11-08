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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
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
 * Pruebas unitarias, de adaptadores y de verificación real en Azure Blob Storage para
 * BiometricConsentServiceImpl, TryOnRepositoryAdapter (JPA) y AzureBlobStorageAdapter (SR-RF-23).
 */
@ExtendWith(MockitoExtension.class)
class BiometricConsentServiceImplTest {

    private static final UUID RAW_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UserId USER_ID = UserId.of(RAW_USER_ID);
    private static final String AZURE_BASE_URL = "https://styleradarblob.blob.core.windows.net";
    private static final String CONTAINER = "biometric-silhouettes";
    private static final String TEST_ACCOUNT_KEY = "U3R5bGVSYWRhclRlc3RTdG9yYWdlQWNjb3VudEtleTIwMjZQb3J0YWw4MDgwU2Vj";
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
                TEST_ACCOUNT_KEY,
                SAS_TOKEN,
                "",
                Duration.ofMinutes(60),
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
    @DisplayName("HU SR-RF-23 (Positivo - Carga, Firma SAS por Recurso HMAC-SHA256 y Verificación de Lectura en Azure): Dada una foto PNG 400x600 px, Cuando se sube, Entonces ejecuta PUT BlockBlob, genera un SAS firmado por recurso (sp=r, sr=b, sig) sin alterar parámetros y verifica la lectura GET")
    void shouldUploadGeneratePerResourceSignedSasAndVerifyReadViaAzureRestApi() throws Exception {
        byte[] photoBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(400, 600);

        // 1. Expect PUT BlockBlob + GET de verificación inmediata de lectura con SAS por recurso (sp=r&spr=https&sv=2024-11-04&sr=b&sig=...)
        mockAzureServer.expect(requestTo(Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID)))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_BLOB_TYPE, AzureBlobStorageAdapter.BLOCK_BLOB_TYPE))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_MS_VERSION, "2024-11-04"))
                .andRespond(withSuccess());

        mockAzureServer.expect(requestTo(Matchers.allOf(
                        Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID),
                        Matchers.containsString("sp=r&spr=https&sv=2024-11-04&sr=b&sig=")
                )))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_MS_VERSION, "2024-11-04"))
                .andRespond(withSuccess(photoBytes, MediaType.IMAGE_PNG));

        BiometricPhoto uploaded = consentService.uploadPhoto(USER_ID, true, "image/png", photoBytes);

        assertThat(uploaded.getWidth()).isEqualTo(400);
        assertThat(uploaded.getHeight()).isEqualTo(600);
        assertThat(uploaded.getPhotoUrl().value())
                .contains(CONTAINER)
                .contains("sp=r&spr=https&sv=2024-11-04&sr=b&sig=")
                .doesNotContain("sp=racwd");

        BiometricPhoto retrieved = consentService.getPhotoByUserId(USER_ID);
        assertThat(retrieved.getPhotoId()).isEqualTo(uploaded.getPhotoId());
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 2. Expect GET Blob adicional usando exactamente la misma URL SAS de solo lectura sin modificar parámetros firmados
        mockAzureServer.expect(requestTo(uploaded.getPhotoUrl().value()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_MS_VERSION, "2024-11-04"))
                .andRespond(withSuccess(photoBytes, MediaType.IMAGE_PNG));

        Optional<byte[]> storedBytes = blobStorageAdapter.retrieveBlobBytes(uploaded.getPhotoUrl());
        assertThat(storedBytes).isPresent();
        assertThat(storedBytes.get()).hasSize(photoBytes.length);
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 3. Al subir una segunda foto, elimina la anterior (DELETE) y sube + verifica lectura de la nueva (PUT + GET)
        int queryIdx = uploaded.getPhotoUrl().value().indexOf('?');
        String expectedDeleteUrl = uploaded.getPhotoUrl().value().substring(0, queryIdx) + "?sv=2024-11-04&sr=b&sp=racwd";
        mockAzureServer.expect(requestTo(expectedDeleteUrl))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_DELETE_SNAPSHOTS, "include"))
                .andRespond(withNoContent());

        byte[] secondPhotoBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(500, 750);
        mockAzureServer.expect(requestTo(Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID)))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_BLOB_TYPE, AzureBlobStorageAdapter.BLOCK_BLOB_TYPE))
                .andRespond(withSuccess());

        mockAzureServer.expect(requestTo(Matchers.containsString("sp=r&spr=https&sv=2024-11-04&sr=b&sig=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(secondPhotoBytes, MediaType.IMAGE_PNG));

        BiometricPhoto secondUpload = consentService.uploadPhoto(
                USER_ID,
                true,
                "image/png",
                secondPhotoBytes
        );
        assertThat(secondUpload.getWidth()).isEqualTo(500);
        assertThat(secondUpload.getPhotoId()).isEqualTo(uploaded.getPhotoId());
        mockAzureServer.verify();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Positivo - Consentimiento Previo y SAS Pre-firmado Intacto): Dado un token de solo lectura pre-firmado (sp=r), Cuando se sube una foto JPEG, Entonces preserva la firma original sin modificar parámetros")
    void shouldUploadPhotoPreservingPreSignedReadSasIntact() throws Exception {
        String preSignedReadToken = "se=2027-12-31T23%3A59%3A59Z&sp=r&spr=https&sv=2024-11-04&sr=c&sig= intactSignature123%3D".replace(" ", "");
        RestClient.Builder preSignedBuilder = RestClient.builder();
        MockRestServiceServer preSignedServer = MockRestServiceServer.bindTo(preSignedBuilder).build();
        AzureBlobStorageAdapter preSignedAdapter = new AzureBlobStorageAdapter(
                preSignedBuilder,
                AZURE_BASE_URL,
                CONTAINER,
                "   ",
                preSignedReadToken,
                "2024-11-04"
        );
        BiometricConsentServiceImpl customService = new BiometricConsentServiceImpl(repositoryAdapter, preSignedAdapter);

        customService.grantConsent(USER_ID, "v1.0", true, List.of("VIRTUAL_TRY_ON_INFERENCE"), "127.0.0.1");
        byte[] photoBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(350, 520);

        preSignedServer.expect(requestTo(Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID)))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        preSignedServer.expect(requestTo(Matchers.endsWith("?" + preSignedReadToken)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(photoBytes, MediaType.IMAGE_JPEG));

        BiometricPhoto uploaded = customService.uploadPhoto(USER_ID, false, "image/jpeg", photoBytes);

        assertThat(uploaded.getContentType()).isEqualTo("image/jpeg");
        assertThat(uploaded.getPhotoUrl().value()).endsWith(".jpg?" + preSignedReadToken);
        preSignedServer.verify();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Seguridad SAS y Errores REST API): Valida firma por recurso sin sasToken previo, rechazo de token de lectura con permisos de escritura y fallos en PUT/GET/DELETE")
    void shouldHandlePerBlobSigningValidationAndRestErrorsGracefully() {
        SasSignedUrl sampleUrl = SasSignedUrl.of(AZURE_BASE_URL + "/" + CONTAINER + "/test.png");

        // 1. Error 500 en PUT lanza IllegalStateException
        mockAzureServer.expect(requestTo(Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID)))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withServerError());

        assertThatThrownBy(() -> blobStorageAdapter.storeBiometricPhoto(USER_ID, "image/png", new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Azure Blob Storage");
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 2. Si la verificación de lectura posterior al PUT retorna vacío, lanza IllegalStateException
        mockAzureServer.expect(requestTo(Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID)))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=r&spr=https")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[0], MediaType.IMAGE_PNG));

        assertThatThrownBy(() -> blobStorageAdapter.storeBiometricPhoto(USER_ID, "image/png", new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verificación de lectura");
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 3. Error 500 en GET retorna Optional.empty()
        mockAzureServer.expect(requestTo(sampleUrl.value()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThat(blobStorageAdapter.retrieveBlobBytes(sampleUrl)).isEmpty();
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 4. Error 500 en DELETE no propaga excepción no controlada
        mockAzureServer.expect(requestTo(sampleUrl.value() + "?sv=2024-11-04&sr=b&sp=racwd"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        blobStorageAdapter.deleteBlob(sampleUrl);
        mockAzureServer.verify();

        // 5. Adaptador con accountKey pero sin sasToken firma dinámicamente PUT (sp=racw), GET (sp=r) y DELETE (sp=d)
        RestClient.Builder keyOnlyBuilder = RestClient.builder();
        MockRestServiceServer keyOnlyServer = MockRestServiceServer.bindTo(keyOnlyBuilder).build();
        AzureBlobStorageAdapter keyOnlyAdapter = new AzureBlobStorageAdapter(
                keyOnlyBuilder,
                AZURE_BASE_URL,
                CONTAINER,
                TEST_ACCOUNT_KEY,
                "",
                "",
                Duration.ofMinutes(30),
                "2024-11-04"
        );
        keyOnlyServer.expect(requestTo(Matchers.containsString("sp=racw&spr=https&sv=2024-11-04&sr=b&sig=")))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        keyOnlyServer.expect(requestTo(Matchers.containsString("sp=r&spr=https&sv=2024-11-04&sr=b&sig=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[]{10, 20}, MediaType.IMAGE_PNG));
        keyOnlyServer.expect(requestTo(Matchers.containsString("sp=d&spr=https&sv=2024-11-04&sr=b&sig=")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        SasSignedUrl generatedUrl = keyOnlyAdapter.storeBiometricPhoto(USER_ID, "image/png", new byte[]{10, 20});
        keyOnlyAdapter.deleteBlob(generatedUrl);
        keyOnlyServer.verify();

        // 6. Rechazar pre-signed read token que contenga permisos distintos de sp=r (sin mutar su firma)
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(
                AZURE_BASE_URL,
                CONTAINER,
                "",
                "",
                "sv=2024-11-04&sr=b&sp=racwd&sig=abc",
                Duration.ofMinutes(60),
                "2024-11-04"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sp=r");

        // 7. Verificar constructor @Autowired de Spring y validación de AZURE_STORAGE_BASE_URL
        AzureBlobStorageAdapter springConstructed = new AzureBlobStorageAdapter(
                AZURE_BASE_URL,
                CONTAINER,
                TEST_ACCOUNT_KEY,
                "",
                "sv=2024-11-04&sr=b&sp=r&spr=https&sig=xyz",
                Duration.ofMinutes(60),
                "2024-11-04"
        );
        assertThat(springConstructed.generatePerBlobReadOnlySasToken("u1/photo.png", Instant.parse("2027-12-31T23:59:59Z")))
                .contains("se=2027-12-31T23%3A59%3A59Z&sp=r&spr=https&sv=2024-11-04&sr=b&sig=");

        assertThatThrownBy(() -> new AzureBlobStorageAdapter(
                "  ",
                CONTAINER,
                "",
                "",
                "",
                Duration.ofMinutes(60),
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

    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_LIVE_STORAGE_KEY", matches = ".+")
    @DisplayName("HU SR-RF-23 (Integración Real Azure Blob Storage): Sube un PNG real a Azure, genera SAS por recurso (sr=b, sp=r) con HMAC-SHA256, verifica lectura HTTP GET real en Azure y elimina el blob")
    void shouldUploadGenerateValidSasAndReadRealFileInAzureBlobStorage() throws Exception {
        String liveBaseUrl = System.getenv().getOrDefault("AZURE_LIVE_BASE_URL", "https://styleradarpt8080blob.blob.core.windows.net");
        String liveContainer = System.getenv().getOrDefault("AZURE_LIVE_CONTAINER", "biometric-silhouettes-qa");
        String liveKey = System.getenv("AZURE_LIVE_STORAGE_KEY");

        AzureBlobStorageAdapter liveAdapter = new AzureBlobStorageAdapter(
                liveBaseUrl,
                liveContainer,
                liveKey,
                "",
                "",
                Duration.ofMinutes(30),
                "2024-11-04"
        );

        byte[] realPngBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(400, 600);
        SasSignedUrl readOnlyBlobUrl = liveAdapter.storeBiometricPhoto(USER_ID, "image/png", realPngBytes);

        assertThat(readOnlyBlobUrl.value())
                .startsWith(liveBaseUrl + "/" + liveContainer + "/" + RAW_USER_ID)
                .contains("sp=r&spr=https&sv=2024-11-04&sr=b&sig=");

        Optional<byte[]> downloadedFromAzure = liveAdapter.retrieveBlobBytes(readOnlyBlobUrl);
        assertThat(downloadedFromAzure).isPresent();
        assertThat(downloadedFromAzure.get()).isEqualTo(realPngBytes);

        liveAdapter.deleteBlob(readOnlyBlobUrl);
    }
}
