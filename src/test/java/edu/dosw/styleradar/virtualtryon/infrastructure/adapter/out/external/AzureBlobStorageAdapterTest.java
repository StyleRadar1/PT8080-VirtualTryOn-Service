package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AzureBlobStorageAdapterTest {

    private static final String STORAGE_BASE_URL = "https://styleradarstorage.blob.core.windows.net";
    private static final String CONTAINER = "biometric-silhouettes";
    // 32-byte valid Base64 account key
    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private AzureBlobStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        adapter = new AzureBlobStorageAdapter(
                restClientBuilder,
                STORAGE_BASE_URL,
                CONTAINER,
                VALID_KEY,
                Duration.ofMinutes(30),
                "2024-11-04"
        );
    }

    @Test
    @DisplayName("Validación de parámetros de inicialización: Requiere https, container, key de 32 bytes y duración positiva")
    void shouldValidateInitializationParametersStrictly() {
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(restClientBuilder, null, CONTAINER, VALID_KEY, Duration.ofMinutes(10), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_BASE_URL es obligatoria");

        assertThatThrownBy(() -> new AzureBlobStorageAdapter(restClientBuilder, "ftp://storage", CONTAINER, VALID_KEY, Duration.ofMinutes(10), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("protocolo seguro https://");

        assertThatThrownBy(() -> new AzureBlobStorageAdapter(restClientBuilder, STORAGE_BASE_URL, "  ", VALID_KEY, Duration.ofMinutes(10), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_CONTAINER es obligatorio");

        assertThatThrownBy(() -> new AzureBlobStorageAdapter(restClientBuilder, STORAGE_BASE_URL, CONTAINER, null, Duration.ofMinutes(10), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_ACCOUNT_KEY es obligatoria");

        // Key < 32 bytes
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(restClientBuilder, STORAGE_BASE_URL, CONTAINER, shortKey, Duration.ofMinutes(10), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("longitud mínima de 32 bytes");

        // Invalid Base64
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(restClientBuilder, STORAGE_BASE_URL, CONTAINER, "invalid@@base64", Duration.ofMinutes(10), "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("formato Base64 válido");

        // Non-positive duration
        assertThatThrownBy(() -> new AzureBlobStorageAdapter(restClientBuilder, STORAGE_BASE_URL, CONTAINER, VALID_KEY, Duration.ZERO, "2024-11-04"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("debe ser positiva");
    }

    @Test
    @DisplayName("generateReadOnlySignedUrl: Debe firmar token Service SAS con sr=b, sp=r, spr=https y no exponer llaves")
    void shouldGenerateValidReadOnlySignedUrl() {
        SasSignedUrl sasUrl = adapter.generateReadOnlySignedUrl("user-1/silhouette.png");

        assertThat(sasUrl).isNotNull();
        assertThat(sasUrl.value())
                .startsWith(STORAGE_BASE_URL + "/" + CONTAINER + "/user-1/silhouette.png?")
                .contains("sp=r")
                .contains("sr=b")
                .contains("spr=https")
                .contains("sv=2024-11-04")
                .contains("sig=")
                .contains("se=");
    }

    @Test
    @DisplayName("extractBlobNameFromUrl: Debe extraer limpiamente rutas relativas preservando segmentos de usuario")
    void shouldExtractCanonicalBlobName() {
        assertThat(adapter.extractBlobNameFromUrl(null)).isEmpty();
        assertThat(adapter.extractBlobNameFromUrl("   ")).isEmpty();
        assertThat(adapter.extractBlobNameFromUrl("user-1/photo.png")).isEqualTo("user-1/photo.png");
        assertThat(adapter.extractBlobNameFromUrl("biometric-silhouettes/user-1/photo.png")).isEqualTo("user-1/photo.png");
        assertThat(adapter.extractBlobNameFromUrl("https://styleradarstorage.blob.core.windows.net/biometric-silhouettes/user-1/photo.png?se=123&sig=abc"))
                .isEqualTo("user-1/photo.png");
    }

    @Test
    @DisplayName("stripQueryParametersForLog: Debe remover los tokens SAS para auditorías y logs seguros")
    void shouldStripQueryParametersForLog() {
        assertThat(AzureBlobStorageAdapter.stripQueryParametersForLog(null)).isEmpty();
        assertThat(AzureBlobStorageAdapter.stripQueryParametersForLog("https://storage/container/blob.png?se=123&sig=abc"))
                .isEqualTo("https://storage/container/blob.png");
        assertThat(AzureBlobStorageAdapter.stripQueryParametersForLog("user/photo.png")).isEqualTo("user/photo.png");
    }

    @Test
    @DisplayName("parseAccountNameStrict: Extrae correctamente el nombre de cuenta del host o lanza excepción")
    void shouldParseAccountNameOrThrow() {
        assertThat(AzureBlobStorageAdapter.parseAccountNameStrict("https://myaccount.blob.core.windows.net"))
                .isEqualTo("myaccount");

        assertThatThrownBy(() -> AzureBlobStorageAdapter.parseAccountNameStrict("https:///invalid"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("storeBiometricPhoto y storeRenderedResult: PUT sube BlockBlob y GET valida lectura")
    void shouldStoreBiometricPhotoSuccessfully() {
        UserId userId = UserId.of(UUID.randomUUID());
        byte[] photoBytes = new byte[]{1, 2, 3, 4};

        // PUT para subir
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(STORAGE_BASE_URL + "/" + CONTAINER + "/" + userId.value())))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_BLOB_TYPE, AzureBlobStorageAdapter.BLOCK_BLOB_TYPE))
                .andRespond(withSuccess());

        // GET para verificar lectura inmediata
        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(STORAGE_BASE_URL + "/" + CONTAINER + "/" + userId.value())))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(photoBytes, MediaType.IMAGE_PNG));

        String blobPath = adapter.storeBiometricPhoto(userId, "image/png", photoBytes);

        assertThat(blobPath).startsWith(userId.value() + "/silhouette-").endsWith(".png");
        mockServer.verify();
    }

    @Test
    @DisplayName("retrieveBlobBytes: GET recupera los bytes binarios del blob")
    void shouldRetrieveBlobBytesSuccessfully() {
        byte[] expectedBytes = new byte[]{10, 20, 30};
        String blobPath = "user-1/test.png";

        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(STORAGE_BASE_URL + "/" + CONTAINER + "/" + blobPath)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(expectedBytes, MediaType.APPLICATION_OCTET_STREAM));

        Optional<byte[]> result = adapter.retrieveBlobBytes(blobPath);

        assertThat(result).isPresent().contains(expectedBytes);
        mockServer.verify();
    }

    @Test
    @DisplayName("deleteBlob: DELETE con x-ms-delete-snapshots elimina el blob")
    void shouldDeleteBlobSuccessfully() {
        String blobPath = "user-1/test.png";

        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(STORAGE_BASE_URL + "/" + CONTAINER + "/" + blobPath)))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(AzureBlobStorageAdapter.HEADER_DELETE_SNAPSHOTS, "include"))
                .andRespond(withSuccess());

        adapter.deleteBlob(blobPath);

        mockServer.verify();
    }
}

