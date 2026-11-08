package edu.dosw.styleradar.virtualtryon.application.service.impl;

import edu.dosw.styleradar.virtualtryon.application.mapper.out.TryOnPersistenceMapper;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    @DisplayName("HU SR-RF-23 (Positivo - Carga, Firma SAS por Recurso, Persistencia de Ruta y Renovación al Vuelo): Guarda ruta limpia en DB, genera SAS en lectura y renueva al consultar")
    void shouldUploadPersistCleanPathAndGenerateFreshSasOnRead() throws Exception {
        byte[] photoBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(400, 600);

        // 1. Expect PUT BlockBlob con token dinámico de escritura (sp=racw&sr=b) + GET de verificación inmediata (sp=r&sr=b)
        mockAzureServer.expect(requestTo(Matchers.allOf(
                        Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID),
                        Matchers.containsString("sp=racw&spr=https&sv=2024-11-04&sr=b&sig=")
                )))
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
        assertThat(uploaded.getBlobPath()).startsWith(RAW_USER_ID + "/silhouette-").endsWith(".png");
        assertThat(uploaded.getPhotoUrl().value())
                .contains(CONTAINER)
                .contains("sp=r&spr=https&sv=2024-11-04&sr=b&sig=");

        // En la base de datos se persiste la ruta del blob, NO la URL temporal con firma que expira
        BiometricPhotoJpaEntity persistedEntity = persistedPhotos.get(RAW_USER_ID);
        assertThat(persistedEntity.getPhotoUrl()).isEqualTo(uploaded.getBlobPath());

        // Al consultar posteriormente, getPhotoByUserId() genera una nueva URL SAS fresca
        BiometricPhoto retrieved = consentService.getPhotoByUserId(USER_ID);
        assertThat(retrieved.getPhotoId()).isEqualTo(uploaded.getPhotoId());
        assertThat(retrieved.getBlobPath()).isEqualTo(uploaded.getBlobPath());
        assertThat(retrieved.getPhotoUrl().value()).contains("sp=r&spr=https&sv=2024-11-04&sr=b&sig=");
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 2. Expect GET Blob adicional usando la URL SAS de solo lectura generada
        mockAzureServer.expect(requestTo(retrieved.getPhotoUrl().value()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_MS_VERSION, "2024-11-04"))
                .andRespond(withSuccess(photoBytes, MediaType.IMAGE_PNG));

        Optional<byte[]> storedBytes = blobStorageAdapter.retrieveBlobBytes(retrieved.getPhotoUrl());
        assertThat(storedBytes).isPresent();
        assertThat(storedBytes.get()).hasSize(photoBytes.length);
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 3. Al subir una segunda foto (reemplazo):
        // PRIMERO sube la nueva foto a Azure y verifica su lectura, y SOLO DESPUÉS de guardar elimina la anterior en Azure
        byte[] secondPhotoBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(500, 750);
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=racw&spr=https&sv=2024-11-04&sr=b&sig=")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_BLOB_TYPE, AzureBlobStorageAdapter.BLOCK_BLOB_TYPE))
                .andRespond(withSuccess());

        mockAzureServer.expect(requestTo(Matchers.containsString("sp=r&spr=https&sv=2024-11-04&sr=b&sig=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(secondPhotoBytes, MediaType.IMAGE_PNG));

        mockAzureServer.expect(requestTo(Matchers.allOf(
                        Matchers.startsWith(AZURE_BASE_URL + "/" + CONTAINER + "/" + RAW_USER_ID),
                        Matchers.containsString("sp=d&spr=https&sv=2024-11-04&sr=b&sig=")
                )))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_DELETE_SNAPSHOTS, "include"))
                .andRespond(withNoContent());

        BiometricPhoto secondUpload = consentService.uploadPhoto(
                USER_ID,
                true,
                "image/png",
                secondPhotoBytes
        );
        assertThat(secondUpload.getWidth()).isEqualTo(500);
        assertThat(secondUpload.getPhotoId()).isNotNull();
        assertThat(secondUpload.getUserId()).isEqualTo(uploaded.getUserId());
        mockAzureServer.verify();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Aislamiento Criptográfico de Firmas SAS): La firma generada para el Blob A es inválida e incompatible para el Blob B")
    void shouldVerifySignatureIsValidForTargetBlobAndFailsForDifferentBlob() {
        String blobA = "user-123/silhouette-aaa.png";
        String blobB = "user-123/silhouette-bbb.png";
        Instant expiry = Instant.parse("2026-12-31T23:59:59Z");

        String sasTokenA = blobStorageAdapter.generatePerBlobReadOnlySasToken(blobA, expiry);
        String sasTokenB = blobStorageAdapter.generatePerBlobReadOnlySasToken(blobB, expiry);

        // Ambas tienen parámetros estándar sp=r, sr=b, spr=https
        assertThat(sasTokenA).contains("sp=r&spr=https&sv=2024-11-04&sr=b&sig=");
        assertThat(sasTokenB).contains("sp=r&spr=https&sv=2024-11-04&sr=b&sig=");

        // Las firmas computadas son completamente diferentes porque el StringToSign incluye la ruta canónica del blob
        String sigA = sasTokenA.substring(sasTokenA.indexOf("sig=") + 4);
        String sigB = sasTokenB.substring(sasTokenB.indexOf("sig=") + 4);
        assertThat(sigA).isNotEqualTo(sigB);
    }

    @Test
    @DisplayName("HU SR-RF-23 (Menor Privilegio de Operaciones): El token de lectura (sp=r) produce firmas distintas a escritura (sp=racw) o borrado (sp=d)")
    void shouldVerifyReadOnlyTokenDoesNotAllowWriteOrDelete() {
        String blob = "user-456/silhouette.png";
        Instant expiry = Instant.parse("2026-12-31T23:59:59Z");

        String readSas = blobStorageAdapter.generatePerBlobSasToken(blob, "r", expiry);
        String writeSas = blobStorageAdapter.generatePerBlobSasToken(blob, "racw", expiry);
        String deleteSas = blobStorageAdapter.generatePerBlobSasToken(blob, "d", expiry);

        assertThat(readSas).contains("sp=r&").doesNotContain("sp=racw").doesNotContain("sp=d");
        assertThat(writeSas).contains("sp=racw&");
        assertThat(deleteSas).contains("sp=d&");

        String sigRead = readSas.substring(readSas.indexOf("sig="));
        String sigWrite = writeSas.substring(writeSas.indexOf("sig="));
        String sigDelete = deleteSas.substring(deleteSas.indexOf("sig="));

        assertThat(sigRead).isNotEqualTo(sigWrite);
        assertThat(sigRead).isNotEqualTo(sigDelete);
    }

    @Test
    @DisplayName("HU SR-RF-23 (Seguridad en Logs y Sanitización de URLs): Cero exposición de parámetros sig= o URLs en mensajes de log y excepciones")
    void shouldNeverLeakSasTokenInErrorScenariosAndSanitizeLogs() {
        String fullSignedUrl = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/u1/photo.png?se=2026-12-31&sp=r&spr=https&sig=SUPER_SECRET_TOKEN%3D";
        String stripped = AzureBlobStorageAdapter.stripQueryParametersForLog(fullSignedUrl);

        assertThat(stripped)
                .isEqualTo("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/u1/photo.png")
                .doesNotContain("sig=")
                .doesNotContain("SUPER_SECRET_TOKEN");

        assertThat(AzureBlobStorageAdapter.stripQueryParametersForLog("https://example.com/blob.png"))
                .isEqualTo("https://example.com/blob.png");
        assertThat(AzureBlobStorageAdapter.stripQueryParametersForLog(null)).isEmpty();
        assertThat(AzureBlobStorageAdapter.stripQueryParametersForLog("  ")).isEmpty();

        // Error 500 en GET no propaga excepción con parámetros sensibles
        mockAzureServer.expect(requestTo(Matchers.any(String.class)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThat(blobStorageAdapter.retrieveBlobBytes(fullSignedUrl)).isEmpty();
        mockAzureServer.verify();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Política de Reintentos y Manejo de Fallos en Eliminación Ley 1581): Reintenta hasta 3 veces y propaga excepción explícita si falla")
    void shouldRetryAndThrowWhenBlobDeletionFailsInAzure() {
        // Simular 3 fallos consecutivos en DELETE
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d&spr=https")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d&spr=https")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d&spr=https")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        assertThatThrownBy(() -> blobStorageAdapter.deleteBlob("user-test/silhouette.png"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No fue posible eliminar la fotografía biométrica en Azure Blob Storage");

        mockAzureServer.verify();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Validación Estricta de Inicialización): Rechaza URLs inválidas, esquemas inseguros, claves cortas, duraciones no positivas o nombres de cuenta no identificables")
    void shouldValidateInitializationParametersStrictly() {
        RestClient.Builder builder = RestClient.builder();

        // 1. URL vacía o nula
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, "", CONTAINER, TEST_ACCOUNT_KEY, Duration.ofMinutes(60), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_BASE_URL");

        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, null, CONTAINER, TEST_ACCOUNT_KEY, Duration.ofMinutes(60), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_BASE_URL");

        // 2. Esquema no HTTPS
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, "ftp://storage.blob.core.windows.net", CONTAINER, TEST_ACCOUNT_KEY, Duration.ofMinutes(60), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("protocolo seguro https://");

        // 3. Contenedor vacío o nulo
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, AZURE_BASE_URL, "  ", TEST_ACCOUNT_KEY, Duration.ofMinutes(60), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_CONTAINER");

        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, AZURE_BASE_URL, null, TEST_ACCOUNT_KEY, Duration.ofMinutes(60), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_CONTAINER");

        // 4. Clave vacía o nula o no Base64
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, AZURE_BASE_URL, CONTAINER, "  ", Duration.ofMinutes(60), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_ACCOUNT_KEY es obligatoria");

        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, AZURE_BASE_URL, CONTAINER, null, Duration.ofMinutes(60), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_ACCOUNT_KEY es obligatoria");

        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, AZURE_BASE_URL, CONTAINER, "not-valid-base64!@#", Duration.ofMinutes(60), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64 válido");

        // 5. Clave demasiado corta (< 32 bytes)
        String shortBase64Key = "c2hvcnQta2V5"; // "short-key" (9 bytes)
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, AZURE_BASE_URL, CONTAINER, shortBase64Key, Duration.ofMinutes(60), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("longitud mínima de 32 bytes");

        // 6. Duración negativa o nula
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, AZURE_BASE_URL, CONTAINER, TEST_ACCOUNT_KEY, Duration.ZERO, "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("readSasDuration");

        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, AZURE_BASE_URL, CONTAINER, TEST_ACCOUNT_KEY, null, "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("readSasDuration");

        assertThatThrownBy(() -> new AzureBlobStorageAdapter(builder, AZURE_BASE_URL, CONTAINER, TEST_ACCOUNT_KEY, Duration.ofMinutes(-5), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("readSasDuration");

        // 7. Host sin nombre de cuenta identificable
        assertThatThrownBy(() -> AzureBlobStorageAdapter.parseAccountNameStrict("https://invalid-host-without-dot"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No fue posible determinar un nombre de cuenta válido");

        // 8. Normalización de URL terminada en / y apiVersion por defecto con null
        AzureBlobStorageAdapter trailingSlashAdapter = new AzureBlobStorageAdapter(
                builder,
                AZURE_BASE_URL + "/",
                CONTAINER,
                TEST_ACCOUNT_KEY,
                Duration.ofMinutes(60),
                ""
        );
        assertThat(trailingSlashAdapter).isNotNull();

        AzureBlobStorageAdapter nullApiVersionAdapter = new AzureBlobStorageAdapter(
                builder,
                AZURE_BASE_URL,
                CONTAINER,
                TEST_ACCOUNT_KEY,
                Duration.ofMinutes(60),
                null
        );
        assertThat(nullApiVersionAdapter).isNotNull();

        // Rechazo categórico del protocolo inseguro HTTP para transporte de datos biométricos
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(
                builder,
                "http://styleradarblob.blob.core.windows.net",
                CONTAINER,
                TEST_ACCOUNT_KEY,
                Duration.ofMinutes(60),
                null
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("protocolo seguro https://");

        // 9. Constructor @Autowired de Spring funciona con parámetros válidos
        AzureBlobStorageAdapter springAdapter = new AzureBlobStorageAdapter(
                AZURE_BASE_URL,
                CONTAINER,
                TEST_ACCOUNT_KEY,
                Duration.ofMinutes(45),
                "2024-11-04"
        );
        assertThat(springAdapter).isNotNull();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Cobertura Métodos por Defecto e Invocaciones Nulas): Valida invocaciones con nulos y métodos default de BlobStoragePort")
    void shouldCoverBlobStoragePortDefaultMethodsAndNullArgs() {
        // Métodos con argumentos nulos o vacíos retornan de inmediato sin interactuar con Azure
        blobStorageAdapter.deleteBlob((String) null);
        blobStorageAdapter.deleteBlob("   ");
        assertThat(blobStorageAdapter.retrieveBlobBytes((String) null)).isEmpty();
        assertThat(blobStorageAdapter.retrieveBlobBytes("   ")).isEmpty();

        // Invocaciones de los métodos default de BlobStoragePort con SasSignedUrl nulo
        BlobStoragePort port = blobStorageAdapter;
        port.deleteBlob((SasSignedUrl) null);
        assertThat(port.retrieveBlobBytes((SasSignedUrl) null)).isEmpty();

        // Invocación con SasSignedUrl no nulo
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        port.deleteBlob(SasSignedUrl.of("https://styleradarpt8080blob.blob.core.windows.net/biometric-silhouettes/u1/p1.png?sp=r"));
        mockAzureServer.verify();
        mockAzureServer.reset();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Reintento Exitoso y Variantes de Ruta en Eliminación): Reintento exitoso en segundo intento y extracción de nombres de blob")
    void shouldRetrySuccessfullyOnSecondAttemptAndExtractBlobNames() {
        // 1. Intento 1 falla con 500, Intento 2 tiene éxito con 204
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        blobStorageAdapter.deleteBlob("user-retry/photo.png");
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 2. Extracción con prefijo completo de almacenamiento
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        blobStorageAdapter.deleteBlob(AZURE_BASE_URL + "/" + CONTAINER + "/user-pref/photo.png");
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 3. Extracción sin barras
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        blobStorageAdapter.deleteBlob("justsinglephoto.png");
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 4. Extracción con múltiples barras
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        blobStorageAdapter.deleteBlob("/nested/dir/user-multi/photo.png");
        mockAzureServer.verify();
        mockAzureServer.reset();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Resiliencia en Reemplazo): Si la eliminación de la foto vieja falla tras confirmar la nueva, la nueva foto se preserva y no falla el flujo")
    void shouldPreserveNewPhotoWhenOldPhotoDeletionFailsDuringReplacement() throws Exception {
        byte[] firstBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(400, 600);
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=racw")))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=r")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(firstBytes, MediaType.IMAGE_PNG));

        BiometricPhoto firstPhoto = consentService.uploadPhoto(USER_ID, true, "image/png", firstBytes);
        assertThat(firstPhoto).isNotNull();
        mockAzureServer.verify();
        mockAzureServer.reset();

        byte[] secondBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(400, 600);
        // Nueva foto sube y se verifica con éxito
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=racw")))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=r")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(secondBytes, MediaType.IMAGE_PNG));

        // Los 3 reintentos de DELETE de la foto anterior fallan
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d"))).andExpect(method(HttpMethod.DELETE)).andRespond(withServerError());
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d"))).andExpect(method(HttpMethod.DELETE)).andRespond(withServerError());
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d"))).andExpect(method(HttpMethod.DELETE)).andRespond(withServerError());

        BiometricPhoto secondPhoto = consentService.uploadPhoto(USER_ID, true, "image/png", secondBytes);
        assertThat(secondPhoto).isNotNull();
        assertThat(secondPhoto.getPhotoId()).isNotNull();
        mockAzureServer.verify();
        mockAzureServer.reset();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Rutas Limpias y Respuestas Vacías en Lectura): Genera SAS dinámico para ruta limpia y maneja contenido vacío")
    void shouldCoverRetrieveBlobBytesBranches() {
        byte[] sampleBytes = new byte[]{1, 2, 3, 4};

        // 1. Recuperar con ruta limpia (sin ?) genera URL firmada al vuelo
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=r&spr=https")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(sampleBytes, MediaType.IMAGE_PNG));

        Optional<byte[]> result = blobStorageAdapter.retrieveBlobBytes("user-clean/photo.png");
        assertThat(result).isPresent().contains(sampleBytes);
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 2. Azure responde contenido vacío (0 bytes)
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=r")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[0], MediaType.IMAGE_PNG));

        Optional<byte[]> emptyResult = blobStorageAdapter.retrieveBlobBytes("https://styleradarpt8080blob.blob.core.windows.net/biometric-silhouettes/u1/photo.png?sp=r");
        assertThat(emptyResult).isEmpty();
        mockAzureServer.verify();
        mockAzureServer.reset();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Carga JPEG y Manejo de Errores en Almacenamiento): Valida extensión JPEG y fallos en verificación o PUT")
    void shouldCoverStoreBiometricPhotoJpegAndFailureScenarios() {
        byte[] jpegBytes = new byte[]{10, 20, 30};

        // 1. Carga con formato image/jpeg
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=racw")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_BLOB_TYPE, AzureBlobStorageAdapter.BLOCK_BLOB_TYPE))
                .andRespond(withSuccess());

        mockAzureServer.expect(requestTo(Matchers.containsString("sp=r")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jpegBytes, MediaType.IMAGE_JPEG));

        String storedBlobName = blobStorageAdapter.storeBiometricPhoto(USER_ID, "image/jpeg", jpegBytes);
        assertThat(storedBlobName).endsWith(".jpg");
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 2. Falla verificación de lectura inmediata -> compensa eliminando el blob huérfano recién subido
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=racw")))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        mockAzureServer.expect(requestTo(Matchers.containsString("sp=r")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        assertThatThrownBy(() -> blobStorageAdapter.storeBiometricPhoto(USER_ID, "image/png", jpegBytes))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("La verificación de lectura del archivo en Azure Blob Storage no retornó contenido");
        mockAzureServer.verify();
        mockAzureServer.reset();

        // 3. Falla solicitud PUT a Azure (no subió el archivo, no requiere compensación)
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=racw")))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withServerError());

        assertThatThrownBy(() -> blobStorageAdapter.storeBiometricPhoto(USER_ID, "image/png", jpegBytes))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No fue posible almacenar la fotografía biométrica en Azure Blob Storage");
        mockAzureServer.verify();
        mockAzureServer.reset();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Compensación de Archivo Huérfano por Fallo en Base de Datos): Elimina el nuevo blob en Azure si falla el guardado en repositorio")
    void shouldCompensateAndCleanUpNewBlobWhenDatabaseSaveFails() throws Exception {
        byte[] photoBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(400, 600);

        // 1. PUT exitoso en Azure
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=racw")))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        // 2. GET verificación exitoso en Azure
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=r")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(photoBytes, MediaType.IMAGE_PNG));

        // 3. Compensación: DELETE del nuevo blob cuando savePhoto() falla
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=d")))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        // Simular fallo en repositorio
        when(photoJpaRepository.save(any(BiometricPhotoJpaEntity.class)))
                .thenThrow(new RuntimeException("Fallo simulado de conexión en base de datos"));

        assertThatThrownBy(() -> consentService.uploadPhoto(USER_ID, true, "image/png", photoBytes))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Fallo simulado de conexión en base de datos");

        mockAzureServer.verify();
        mockAzureServer.reset();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Ramas de Dominio y Mapeadores): Valida validaciones de BiometricPhoto, SasSignedUrl, BiometricConsent y TryOnPersistenceMapper")
    void shouldCoverDomainModelAndMapperBranches() {
        // BiometricPhoto validaciones
        assertThatThrownBy(() -> new BiometricPhoto(UUID.randomUUID(), USER_ID, "image/png", 0, 100, 100, "p", SasSignedUrl.of("https://a.com/b?sp=r"), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BiometricPhoto(UUID.randomUUID(), USER_ID, "image/png", 100, -5, 100, "p", SasSignedUrl.of("https://a.com/b?sp=r"), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BiometricPhoto(UUID.randomUUID(), USER_ID, "image/png", 100, 100, 0, "p", SasSignedUrl.of("https://a.com/b?sp=r"), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BiometricPhoto(UUID.randomUUID(), USER_ID, "  ", 100, 100, 100, "p", SasSignedUrl.of("https://a.com/b?sp=r"), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);

        BiometricPhoto photo = new BiometricPhoto(UUID.randomUUID(), USER_ID, "image/png", 100, 100, 100, "d/photo.png", SasSignedUrl.of("https://a.com/c/d/photo.png?sp=r"), Instant.now());
        assertThat(photo.getBlobPath()).isEqualTo("d/photo.png");
        BiometricPhoto refreshed = photo.withFreshSignedUrl(SasSignedUrl.of("https://a.com/c/d/photo.png?sp=r&sig=new"));
        assertThat(refreshed.getPhotoUrl().value()).contains("sig=new");

        BiometricPhoto photoNoSlash = new BiometricPhoto(UUID.randomUUID(), USER_ID, "image/png", 100, 100, 100, "photo.png", SasSignedUrl.of("https://a.com?sp=r"), Instant.now());
        assertThat(photoNoSlash.getBlobPath()).isNotNull();

        // SasSignedUrl validaciones
        assertThatThrownBy(() -> SasSignedUrl.of("http://example.com/blob.png?sp=r")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
        assertThat(SasSignedUrl.of("https://example.com/blob.png?sp=r").value()).startsWith("https://");

        SasSignedUrl trailingQuestion = SasSignedUrl.of("https://example.com/blob.png?");
        assertThat(trailingQuestion.value()).endsWith("?");

        assertThatThrownBy(() -> SasSignedUrl.of("https://a.com/b?ss=b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SasSignedUrl.of("https://a.com/b?srt=c"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SasSignedUrl.of("https://a.com/b?sp=w"))
                .isInstanceOf(IllegalArgumentException.class);

        // BiometricConsent validaciones y revocación
        BiometricConsent defaultPurposesConsent = new BiometricConsent(UUID.randomUUID(), USER_ID, "v1", true, null, Instant.now(), null, "127.0.0.1");
        assertThat(defaultPurposesConsent.getPurposes()).contains("VIRTUAL_TRY_ON_INFERENCE");
        assertThat(defaultPurposesConsent.isValidForInference()).isTrue();

        defaultPurposesConsent.revoke(Instant.now());
        assertThat(defaultPurposesConsent.isValidForInference()).isFalse();

        BiometricConsent unacceptedConsent = new BiometricConsent(UUID.randomUUID(), USER_ID, "v1", false, List.of(), Instant.now(), null, "127.0.0.1");
        assertThat(unacceptedConsent.isValidForInference()).isFalse();

        assertThatThrownBy(() -> new BiometricConsent(UUID.randomUUID(), USER_ID, "  ", true, List.of(), Instant.now(), null, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);

        // TryOnPersistenceMapper normalización de referencias antiguas (URLs completas con SAS)
        BiometricPhotoJpaEntity httpEntity = new BiometricPhotoJpaEntity(UUID.randomUUID(), RAW_USER_ID, "image/png", 100, 100, 100, "http://custom.storage.com/container/user/photo.png?sp=r&sig=OLD", Instant.now());
        BiometricPhoto domainHttp = persistenceMapper.toPhotoDomain(httpEntity);
        assertThat(domainHttp.getBlobPath()).isEqualTo("user/photo.png").doesNotContain("sp=r").doesNotContain("http");

        BiometricPhotoJpaEntity httpsEntity = new BiometricPhotoJpaEntity(UUID.randomUUID(), RAW_USER_ID, "image/png", 100, 100, 100, "https://styleradarpt8080blob.blob.core.windows.net/biometric-silhouettes/user-99/old-photo.png?sp=r&sig=abc", Instant.now());
        BiometricPhoto domainHttps = persistenceMapper.toPhotoDomain(httpsEntity);
        assertThat(domainHttps.getBlobPath()).isEqualTo("user-99/old-photo.png").doesNotContain("sig=");

        BiometricPhotoJpaEntity singleSlashEntity = new BiometricPhotoJpaEntity(UUID.randomUUID(), RAW_USER_ID, "image/png", 100, 100, 100, "https://styleradarpt8080blob.blob.core.windows.net/single.png?sp=r", Instant.now());
        BiometricPhoto domainSingle = persistenceMapper.toPhotoDomain(singleSlashEntity);
        assertThat(domainSingle.getBlobPath()).isEqualTo("single.png");

        BiometricPhotoJpaEntity nullEntity = new BiometricPhotoJpaEntity(UUID.randomUUID(), RAW_USER_ID, "image/png", 100, 100, 100, null, Instant.now());
        BiometricPhoto domainNull = persistenceMapper.toPhotoDomain(nullEntity);
        assertThat(domainNull.getBlobPath()).isEmpty();

        assertThat(TryOnPersistenceMapper.normalizePersistedBlobPath("   ")).isEmpty();
        assertThat(TryOnPersistenceMapper.normalizePersistedBlobPath("user/photo.png")).isEqualTo("user/photo.png");
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
    @DisplayName("HU SR-RF-23 (Extracción Canónica de Rutas de Blob): Preserva íntegros los segmentos de usuario en rutas relativas de dos segmentos, URLs y prefijos de contenedor")
    void shouldPreserveUserFolderAndCanonicalSegmentsInExtractBlobName() {
        String twoSegmentRelative = "550e8400-e29b-41d4-a716-446655440000/silhouette-123.png";
        assertThat(blobStorageAdapter.extractBlobNameFromUrl(twoSegmentRelative))
                .isEqualTo(twoSegmentRelative);

        String leadingSlashRelative = "/550e8400-e29b-41d4-a716-446655440000/silhouette-123.png";
        assertThat(blobStorageAdapter.extractBlobNameFromUrl(leadingSlashRelative))
                .isEqualTo(twoSegmentRelative);

        String relativeWithContainer = "biometric-silhouettes/550e8400-e29b-41d4-a716-446655440000/silhouette-123.png";
        assertThat(blobStorageAdapter.extractBlobNameFromUrl(relativeWithContainer))
                .isEqualTo(twoSegmentRelative);

        String absoluteWithContainer = "/biometric-silhouettes/550e8400-e29b-41d4-a716-446655440000/silhouette-123.png";
        assertThat(blobStorageAdapter.extractBlobNameFromUrl(absoluteWithContainer))
                .isEqualTo(twoSegmentRelative);

        String fullHttpsUrl = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/550e8400-e29b-41d4-a716-446655440000/silhouette-123.png?se=2026-11-08T20%3A00%3A00Z&sp=r&sig=abc";
        assertThat(blobStorageAdapter.extractBlobNameFromUrl(fullHttpsUrl))
                .isEqualTo(twoSegmentRelative);

        String otherHostUrl = "http://localhost:8080/custom-container/550e8400-e29b-41d4-a716-446655440000/silhouette-123.png?sp=r";
        assertThat(blobStorageAdapter.extractBlobNameFromUrl(otherHostUrl))
                .isEqualTo(twoSegmentRelative);

        String singleFileName = "silhouette-standalone.png";
        assertThat(blobStorageAdapter.extractBlobNameFromUrl(singleFileName))
                .isEqualTo(singleFileName);

        assertThat(blobStorageAdapter.extractBlobNameFromUrl(null)).isEmpty();
        assertThat(blobStorageAdapter.extractBlobNameFromUrl("   ")).isEmpty();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Sincronización Transaccional y Compensación ante Rollback): Elimina nuevo blob si ocurre rollback en commit y elimina anterior tras commit exitoso")
    void shouldCompensateNewBlobOnRollbackAndCleanPreviousOnCommit() throws Exception {
        byte[] photoBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(400, 600);

        BiometricPhotoJpaEntity existingEntity = new BiometricPhotoJpaEntity(
                UUID.randomUUID(), RAW_USER_ID, "image/png", 400, 600, 1000, "user/old-silhouette.png", Instant.now()
        );
        when(photoJpaRepository.findByUserId(RAW_USER_ID))
                .thenReturn(Optional.of(existingEntity));

        mockAzureServer.expect(requestTo(Matchers.containsString("sp=racw"))).andExpect(method(HttpMethod.PUT)).andRespond(withSuccess());
        mockAzureServer.expect(requestTo(Matchers.containsString("sp=r"))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(photoBytes, MediaType.IMAGE_PNG));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            BiometricPhoto uploaded = consentService.uploadPhoto(USER_ID, true, "image/png", photoBytes);
            assertThat(uploaded).isNotNull();
            mockAzureServer.verify();
            mockAzureServer.reset();

            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            TransactionSynchronization sync = synchronizations.get(0);

            // 1. Compensación por Rollback (status != STATUS_COMMITTED)
            mockAzureServer.expect(requestTo(Matchers.containsString("sp=d"))).andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            mockAzureServer.verify();
            mockAzureServer.reset();

            // 2. Commit exitoso (afterCommit) -> elimina la foto previa
            mockAzureServer.expect(requestTo(Matchers.containsString("sp=d"))).andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());
            sync.afterCommit();
            mockAzureServer.verify();
            mockAzureServer.reset();

            // 3. Estado STATUS_COMMITTED en afterCompletion no debe re-compensar
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
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
                Duration.ofMinutes(30),
                "2024-11-04"
        );

        byte[] realPngBytes = PhotoValidationChainTest.createFullBodyFrontalPngBytes(400, 600);
        String blobPath = liveAdapter.storeBiometricPhoto(USER_ID, "image/png", realPngBytes);
        SasSignedUrl readOnlyBlobUrl = liveAdapter.generateReadOnlySignedUrl(blobPath);

        assertThat(blobPath).startsWith(RAW_USER_ID + "/silhouette-").endsWith(".png");
        assertThat(readOnlyBlobUrl.value())
                .startsWith(liveBaseUrl + "/" + liveContainer + "/" + RAW_USER_ID)
                .contains("sp=r&spr=https&sv=2024-11-04&sr=b&sig=");

        Optional<byte[]> downloadedFromAzure = liveAdapter.retrieveBlobBytes(readOnlyBlobUrl);
        assertThat(downloadedFromAzure).isPresent();
        assertThat(downloadedFromAzure.get()).isEqualTo(realPngBytes);

        liveAdapter.deleteBlob(blobPath);
    }
}
