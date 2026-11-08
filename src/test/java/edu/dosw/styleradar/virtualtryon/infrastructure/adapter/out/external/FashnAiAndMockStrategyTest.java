package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.external;

import edu.dosw.styleradar.virtualtryon.domain.exception.AiProviderTimeoutException;
import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJobBuilder;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.strategy.FashnAiStrategy;
import edu.dosw.styleradar.virtualtryon.domain.strategy.MockAiStrategy;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Pruebas unitarias y de integración HTTP para FashnAiRestClientAdapter, FashnAiStrategy y MockAiStrategy (SR-RF-25).
 */
class FashnAiAndMockStrategyTest {

    private static final String FASHN_URL = "https://ia.test/v1";
    private static final String MODEL_IMAGE_URL = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/user.png?sv=2024-11-04&sr=b&sp=r&spr=https";
    private static final String GARMENT_IMAGE_URL = "https://styleradar.dosw.edu/catalog/garments/shirt.png";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private FashnAiRestClientAdapter fashnAdapter;
    private BlobStoragePort blobStoragePort;
    private MockAiStrategy mockAiStrategy;
    private FashnAiStrategy fashnAiStrategy;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(FASHN_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        fashnAdapter = new FashnAiRestClientAdapter(builder, "clave-test", Duration.ZERO, 2);

        blobStoragePort = Mockito.mock(BlobStoragePort.class);
        when(blobStoragePort.storeRenderedResult(any(), any(), any()))
                .thenReturn(SasSignedUrl.of("https://styleradarblob.blob.core.windows.net/biometric-silhouettes/render.png?sv=2024-11-04&sr=b&sp=r&spr=https"));

        mockAiStrategy = new MockAiStrategy(blobStoragePort);
        fashnAiStrategy = new FashnAiStrategy(fashnAdapter, blobStoragePort, mockAiStrategy);
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando FASHN AI completa la ejecución tras polling en /status/{id}, Entonces retorna los bytes generados y FashnAiStrategy almacena el render en Azure Blob Storage")
    void shouldReturnGeneratedImageWhenFashnAiCompletes() {
        byte[] generated = {9, 8, 7};
        server.expect(requestTo(FASHN_URL + "/run"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer clave-test"))
                .andExpect(jsonPath("$.model_name").value("tryon-v1.6"))
                .andExpect(jsonPath("$.inputs.category").value("tops"))
                .andRespond(withSuccess("{\"id\":\"abc\",\"error\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(FASHN_URL + "/status/abc"))
                .andRespond(withSuccess("{\"id\":\"abc\",\"status\":\"processing\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(FASHN_URL + "/status/abc"))
                .andRespond(withSuccess("{\"id\":\"abc\",\"status\":\"completed\",\"output\":[\"data:image/png;base64,"
                        + Base64.getEncoder().encodeToString(generated) + "\"]}", MediaType.APPLICATION_JSON));

        TryOnJob job = new TryOnJobBuilder()
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl(MODEL_IMAGE_URL)
                .withCategory(GarmentCategory.TOPS)
                .withGarmentImageUrl(GARMENT_IMAGE_URL)
                .withProvider(AiProvider.FASHN_AI)
                .build();

        SasSignedUrl resultUrl = fashnAiStrategy.submitTryOn(job);

        assertThat(resultUrl.value()).contains("sp=r");
        assertThat(fashnAiStrategy.supportsProvider(AiProvider.FASHN_AI)).isTrue();
        assertThat(fashnAiStrategy.supportsProvider(AiProvider.MOCK_LOCAL)).isFalse();
        Mockito.verify(blobStoragePort).storeRenderedResult(eq(job.getUserId()), eq(job.getId()), eq(generated));
        server.verify();
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando FASHN AI no tiene API key o falla o termina con failed/timeout/sin id, Entonces retorna vacío y FashnAiStrategy delega en MockAiStrategy")
    void shouldFallbackToMockAiStrategyWhenFashnAiHasNoKeyOrFails() {
        FashnAiRestClientAdapter withoutKey = new FashnAiRestClientAdapter(builder, " ", Duration.ZERO, 2);
        assertThat(withoutKey.generateTryOnRender(MODEL_IMAGE_URL, GARMENT_IMAGE_URL, GarmentCategory.TOPS)).isEmpty();
        assertThat(withoutKey.queryExternalJobStatus("abc")).isEqualTo(JobStatus.COMPLETED);

        FashnAiRestClientAdapter springConstructed = new FashnAiRestClientAdapter(FASHN_URL, "");
        assertThat(springConstructed.generateTryOnRender(MODEL_IMAGE_URL, GARMENT_IMAGE_URL, GarmentCategory.TOPS)).isEmpty();

        // Caso 1: Error 500 en /run -> FashnAiStrategy hace fallback a MockAiStrategy
        server.expect(requestTo(FASHN_URL + "/run")).andRespond(withServerError());
        TryOnJob job = new TryOnJobBuilder()
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl(MODEL_IMAGE_URL)
                .withCategory(GarmentCategory.BOTTOMS)
                .withProvider(AiProvider.FASHN_AI)
                .build();
        SasSignedUrl fallbackUrl = fashnAiStrategy.submitTryOn(job);
        assertThat(fallbackUrl.value()).contains("sp=r");
        server.verify();
        server.reset();

        // Caso 2: FASHN AI devuelve status=failed
        server.expect(requestTo(FASHN_URL + "/run"))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(FASHN_URL + "/status/abc"))
                .andRespond(withSuccess("{\"status\":\"failed\",\"error\":\"imagen invalida\"}", MediaType.APPLICATION_JSON));
        assertThat(fashnAdapter.generateTryOnRender(MODEL_IMAGE_URL, GARMENT_IMAGE_URL, GarmentCategory.ONE_PIECES)).isEmpty();
        server.verify();
        server.reset();

        // Caso 3: FASHN AI no termina a tiempo (timeout de intentos)
        server.expect(requestTo(FASHN_URL + "/run"))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(FASHN_URL + "/status/abc"))
                .andRespond(withSuccess("{\"status\":\"processing\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(FASHN_URL + "/status/abc"))
                .andRespond(withSuccess("{\"status\":\"in_queue\"}", MediaType.APPLICATION_JSON));
        assertThat(fashnAdapter.generateTryOnRender(MODEL_IMAGE_URL, GARMENT_IMAGE_URL, GarmentCategory.TOPS)).isEmpty();
        server.verify();
        server.reset();

        // Caso 4: FASHN AI no devuelve id en /run
        server.expect(requestTo(FASHN_URL + "/run"))
                .andRespond(withSuccess("{\"error\":\"sin creditos\"}", MediaType.APPLICATION_JSON));
        assertThat(fashnAdapter.generateTryOnRender(MODEL_IMAGE_URL, GARMENT_IMAGE_URL, GarmentCategory.TOPS)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando se consulta checkStatus en FashnAiStrategy y MockAiStrategy, Entonces mapea completed, failed, processing y errores de red")
    void shouldCheckExternalJobStatusInStrategies() {
        assertThatThrownBy(() -> fashnAiStrategy.checkStatus(" "))
                .isInstanceOf(AiProviderTimeoutException.class);

        server.expect(requestTo(FASHN_URL + "/status/job-1"))
                .andRespond(withSuccess("{\"status\":\"completed\"}", MediaType.APPLICATION_JSON));
        assertThat(fashnAiStrategy.checkStatus("job-1")).isEqualTo(JobStatus.COMPLETED);
        server.verify();
        server.reset();

        server.expect(requestTo(FASHN_URL + "/status/job-2"))
                .andRespond(withSuccess("{\"status\":\"canceled\"}", MediaType.APPLICATION_JSON));
        assertThat(fashnAiStrategy.checkStatus("job-2")).isEqualTo(JobStatus.FAILED);
        server.verify();
        server.reset();

        server.expect(requestTo(FASHN_URL + "/status/job-3"))
                .andRespond(withSuccess("{\"status\":\"processing\"}", MediaType.APPLICATION_JSON));
        assertThat(fashnAiStrategy.checkStatus("job-3")).isEqualTo(JobStatus.PROCESSING);
        server.verify();
        server.reset();

        server.expect(requestTo(FASHN_URL + "/status/job-4")).andRespond(withServerError());
        assertThat(fashnAiStrategy.checkStatus("job-4")).isEqualTo(JobStatus.FAILED);
        server.verify();

        assertThat(FashnAiRestClientAdapter.resolveCategoryCode(GarmentCategory.TOPS)).isEqualTo("tops");
        assertThat(FashnAiRestClientAdapter.resolveCategoryCode(GarmentCategory.BOTTOMS)).isEqualTo("bottoms");
        assertThat(FashnAiRestClientAdapter.resolveCategoryCode(GarmentCategory.ONE_PIECES)).isEqualTo("one-pieces");
        assertThat(FashnAiRestClientAdapter.resolveCategoryCode(null)).isEqualTo("auto");
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando MockAiStrategy ejecuta composición gráfica con imágenes grandes (> 1600 px) y distintas categorías, Entonces acota dimensiones y genera PNG válido")
    void shouldComposeAndBoundImagesInMockAiStrategy() {
        assertThat(mockAiStrategy.supportsProvider(AiProvider.MOCK_LOCAL)).isTrue();
        assertThat(mockAiStrategy.checkStatus("any")).isEqualTo(JobStatus.COMPLETED);

        BufferedImage hugeUserPhoto = new BufferedImage(1800, 2400, BufferedImage.TYPE_INT_RGB);
        BufferedImage garmentImage = new BufferedImage(400, 500, BufferedImage.TYPE_INT_ARGB);

        byte[] renderedBytes = mockAiStrategy.composeOverlay(hugeUserPhoto, garmentImage, GarmentCategory.ONE_PIECES);
        BufferedImage decoded = mockAiStrategy.decodeImage(renderedBytes);

        assertThat(decoded).isNotNull();
        assertThat(decoded.getHeight()).isEqualTo(1600);
        assertThat(decoded.getWidth()).isEqualTo(1200);
        assertThat(mockAiStrategy.decodeImage(null)).isNull();
    }

    @Test
    @DisplayName("HU SR-RF-25: Cuando MockAiStrategy recibe URLs de Azure Blob Storage con bytes reales, Entonces decodifica y compone las imágenes del usuario y prenda")
    void shouldSubmitTryOnWithRealBlobBytesInMockAiStrategy() throws Exception {
        BufferedImage realUserPhoto = new BufferedImage(300, 500, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream userPhotoStream = new ByteArrayOutputStream();
        ImageIO.write(realUserPhoto, "png", userPhotoStream);

        BufferedImage realGarment = new BufferedImage(150, 200, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream garmentStream = new ByteArrayOutputStream();
        ImageIO.write(realGarment, "png", garmentStream);

        String photoUrl = "https://styleradarblob.blob.core.windows.net/biometric-silhouettes/user/photo.png";
        String garmentUrl = "https://styleradarblob.blob.core.windows.net/catalog-garments/garment.png";

        when(blobStoragePort.retrieveBlobBytes(photoUrl)).thenReturn(Optional.of(userPhotoStream.toByteArray()));
        when(blobStoragePort.retrieveBlobBytes(garmentUrl)).thenReturn(Optional.of(garmentStream.toByteArray()));

        TryOnJob job = new TryOnJobBuilder()
                .withUserId(UserId.of(UUID.randomUUID()))
                .withGarmentId(GarmentId.of(UUID.randomUUID()))
                .withPhotoUrl(photoUrl)
                .withCategory(GarmentCategory.TOPS)
                .withGarmentImageUrl(garmentUrl)
                .withProvider(AiProvider.MOCK_LOCAL)
                .build();

        SasSignedUrl result = mockAiStrategy.submitTryOn(job);
        assertThat(result).isNotNull();
        Mockito.verify(blobStoragePort).retrieveBlobBytes(photoUrl);
        Mockito.verify(blobStoragePort).retrieveBlobBytes(garmentUrl);
        Mockito.verify(blobStoragePort).storeRenderedResult(eq(job.getUserId()), eq(job.getId()), any(byte[].class));
    }
}

