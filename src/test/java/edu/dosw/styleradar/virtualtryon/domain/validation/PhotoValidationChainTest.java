package edu.dosw.styleradar.virtualtryon.domain.validation;

import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricConsent;
import edu.dosw.styleradar.virtualtryon.domain.model.BiometricPhoto;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de los validadores de la cadena de responsabilidad (Chain of Responsibility)
 * y objetos de valor asociados al requerimiento SR-RF-23 (Carga de silueta y consentimiento biométrico).
 */
class PhotoValidationChainTest {

    private static final UUID RAW_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UserId USER_ID = UserId.of(RAW_USER_ID);

    private TryOnValidatorHandler validationChain;

    @BeforeEach
    void setUp() {
        BiometricConsentValidator consentValidator = new BiometricConsentValidator();
        ImageFormatResolutionValidator formatValidator = new ImageFormatResolutionValidator();
        PoseReferenceValidator poseValidator = new PoseReferenceValidator();
        consentValidator.setNext(formatValidator).setNext(poseValidator);
        this.validationChain = consentValidator;
    }

    private static byte[] createPngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Positivo): Dada una fotografía PNG vertical de 400x600 px con consentimiento aceptado, Cuando se ejecuta la cadena de validación, Entonces aprueba y registra sus dimensiones")
    void shouldValidateValidVerticalPhotoWithConsent() throws Exception {
        byte[] validPng = createPngBytes(400, 600);
        TryOnValidationContext context = new TryOnValidationContext(USER_ID, true, "image/png", validPng);

        assertThatCode(() -> validationChain.validate(context)).doesNotThrowAnyException();

        assertThat(context.getImageWidth()).isEqualTo(400);
        assertThat(context.getImageHeight()).isEqualTo(600);
        assertThat(context.getImageSizeBytes()).isEqualTo(validPng.length);
        assertThat(context.getDecodedImage()).isNotNull();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Negativo - Consentimiento): Dada una solicitud sin consentimiento biométrico, Cuando se ejecuta la cadena, Entonces lanza ConsentRequiredException")
    void shouldThrowConsentRequiredWhenNotAccepted() throws Exception {
        byte[] validPng = createPngBytes(400, 600);
        TryOnValidationContext context = new TryOnValidationContext(USER_ID, false, "image/png", validPng);

        assertThatThrownBy(() -> validationChain.validate(context))
                .isInstanceOf(ConsentRequiredException.class)
                .hasMessageContaining(RAW_USER_ID.toString());
    }

    @Test
    @DisplayName("HU SR-RF-23 (Negativo - Vacía): Dada una fotografía vacía o nula, Cuando se valida el formato, Entonces lanza InvalidPhotoException")
    void shouldThrowWhenPhotoBytesAreEmptyOrNull() {
        TryOnValidationContext emptyContext = new TryOnValidationContext(USER_ID, true, "image/png", new byte[0]);
        TryOnValidationContext nullContext = new TryOnValidationContext(USER_ID, true, "image/png", null);

        assertThatThrownBy(() -> validationChain.validate(emptyContext))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("no puede estar vacía");

        assertThatThrownBy(() -> validationChain.validate(nullContext))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("no puede estar vacía");
        assertThat(nullContext.getImageBytes()).isNull();
        assertThat(nullContext.getImageSizeBytes()).isZero();
    }

    @Test
    @DisplayName("HU SR-RF-23 (Negativo - Formato): Dada una fotografía con formato MIME no permitido (GIF o nulo), Cuando se valida, Entonces lanza InvalidPhotoException")
    void shouldThrowWhenContentTypeIsUnsupported() throws Exception {
        byte[] png = createPngBytes(400, 600);
        TryOnValidationContext gifContext = new TryOnValidationContext(USER_ID, true, "image/gif", png);
        TryOnValidationContext nullTypeContext = new TryOnValidationContext(USER_ID, true, null, png);

        assertThatThrownBy(() -> validationChain.validate(gifContext))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("JPEG o PNG");

        assertThatThrownBy(() -> validationChain.validate(nullTypeContext))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("JPEG o PNG");
    }

    @Test
    @DisplayName("HU SR-RF-23 (Negativo - Peso > 5 MB): Dada una fotografía que supera los 5 MB, Cuando se valida, Entonces lanza InvalidPhotoException")
    void shouldThrowWhenPhotoExceedsMaxSize() {
        byte[] oversized = new byte[(int) ImageFormatResolutionValidator.MAX_SIZE_BYTES + 10];
        TryOnValidationContext context = new TryOnValidationContext(USER_ID, true, "image/jpeg", oversized);

        assertThatThrownBy(() -> validationChain.validate(context))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    @DisplayName("HU SR-RF-23 (Negativo - Bytes Corruptos): Dados bytes que no corresponden a una imagen decodificable, Cuando se valida, Entonces lanza InvalidPhotoException")
    void shouldThrowWhenBytesAreNotAValidImage() {
        byte[] corrupted = new byte[]{10, 20, 30, 40};
        TryOnValidationContext context = new TryOnValidationContext(USER_ID, true, "image/png", corrupted);

        assertThatThrownBy(() -> validationChain.validate(context))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("imagen válida");
    }

    @Test
    @DisplayName("HU SR-RF-23 (Negativo - Dimensiones < 300x400): Dada una imagen menor a 300x400 píxeles, Cuando se valida, Entonces lanza InvalidPhotoException")
    void shouldThrowWhenImageIsSmallerThanMinimumDimensions() throws Exception {
        byte[] smallWidth = createPngBytes(250, 500);
        byte[] smallHeight = createPngBytes(350, 350);

        TryOnValidationContext widthCtx = new TryOnValidationContext(USER_ID, true, "image/png", smallWidth);
        TryOnValidationContext heightCtx = new TryOnValidationContext(USER_ID, true, "image/png", smallHeight);

        assertThatThrownBy(() -> validationChain.validate(widthCtx))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("300x400");

        assertThatThrownBy(() -> validationChain.validate(heightCtx))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("300x400");
    }

    @Test
    @DisplayName("HU SR-RF-23 (Negativo - Orientación Horizontal): Dada una imagen horizontal (600x450 px), Cuando se valida la pose, Entonces lanza InvalidPhotoException")
    void shouldThrowWhenImageIsHorizontal() throws Exception {
        byte[] landscape = createPngBytes(600, 450);
        TryOnValidationContext context = new TryOnValidationContext(USER_ID, true, "image/png", landscape);

        assertThatThrownBy(() -> validationChain.validate(context))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("orientación vertical");
    }

    @Test
    @DisplayName("HU SR-RF-23 (Pose sin decodificación previa): Dado un PoseReferenceValidator invocado sin imagen decodificada, Cuando valida, Entonces lanza InvalidPhotoException")
    void shouldThrowWhenPoseValidatorCalledWithoutDecodedImage() {
        PoseReferenceValidator poseValidator = new PoseReferenceValidator();
        TryOnValidationContext context = new TryOnValidationContext(USER_ID, true, "image/png", new byte[]{1, 2});

        assertThatThrownBy(() -> poseValidator.validate(context))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("decodificada");
    }

    @Test
    @DisplayName("HU SR-RF-23 (Value Objects y Modelos): Dados UserId, SasSignedUrl, BiometricConsent y BiometricPhoto, Cuando se instancian y operan, Entonces cumplen sus invariantes")
    void shouldValidateValueObjectsAndDomainModels() {
        UserId parsedUserId = UserId.fromString(RAW_USER_ID.toString());
        assertThat(parsedUserId).isEqualTo(USER_ID);

        SasSignedUrl url = SasSignedUrl.of(" https://styleradarblob.blob.core.windows.net/biometric-silhouettes/pose.png ");
        assertThat(url.value()).startsWith("https://");

        assertThatThrownBy(() -> SasSignedUrl.of("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SasSignedUrl.of("ftp://invalid.url"))
                .isInstanceOf(IllegalArgumentException.class);

        Instant now = Instant.now();
        BiometricConsent consent = new BiometricConsent(
                UUID.randomUUID(),
                USER_ID,
                "v1.0",
                true,
                null,
                now,
                null,
                "127.0.0.1"
        );
        assertThat(consent.isValidForInference()).isTrue();
        assertThat(consent.getPurposes()).containsExactly("VIRTUAL_TRY_ON_INFERENCE");

        consent.revoke(now.plusSeconds(60));
        assertThat(consent.isValidForInference()).isFalse();
        assertThat(consent.getRevokedAt()).isNotNull();

        assertThatThrownBy(() -> new BiometricConsent(UUID.randomUUID(), USER_ID, " ", true, List.of(), now, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        BiometricPhoto photo = new BiometricPhoto(
                UUID.randomUUID(),
                USER_ID,
                "image/png",
                400,
                600,
                1024L,
                url,
                now
        );
        assertThat(photo.getWidth()).isEqualTo(400);
        assertThat(photo.getHeight()).isEqualTo(600);

        assertThatThrownBy(() -> new BiometricPhoto(UUID.randomUUID(), USER_ID, "", 400, 600, 1024L, url, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BiometricPhoto(UUID.randomUUID(), USER_ID, "image/png", 0, 600, 1024L, url, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BiometricPhoto(UUID.randomUUID(), USER_ID, "image/png", 400, 0, 1024L, url, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BiometricPhoto(UUID.randomUUID(), USER_ID, "image/png", 400, 600, 0L, url, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

