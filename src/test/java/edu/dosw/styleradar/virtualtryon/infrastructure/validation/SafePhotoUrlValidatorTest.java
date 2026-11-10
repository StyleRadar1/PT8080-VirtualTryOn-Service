package edu.dosw.styleradar.virtualtryon.infrastructure.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SafePhotoUrlValidatorTest {

    @Test
    @DisplayName("SSRF QA/PROD: Acepta URLs oficiales pertenecientes a Azure Blob Storage (*.blob.core.windows.net)")
    void shouldAcceptAzureBlobStorageUrlsInAnyEnvironment() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        prodEnv.setProperty("styleradar.tryon.security.allow-localhost-urls", "false");

        SafePhotoUrlValidator validator = new SafePhotoUrlValidator(prodEnv);

        assertThat(validator.isValid("https://styleradarpt8080blob.blob.core.windows.net/biometric-silhouettes-prod/photo.jpg", null)).isTrue();
        assertThat(validator.isValid("https://storageaccount.blob.core.windows.net/container/item.png", null)).isTrue();
    }

    @Test
    @DisplayName("SSRF QA/PROD: Rechaza localhost y 127.0.0.1 estrictamente en entornos de QA y PROD")
    void shouldRejectLocalhostAndLoopbackInQaAndProd() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        prodEnv.setProperty("styleradar.tryon.security.allow-localhost-urls", "false");

        SafePhotoUrlValidator prodValidator = new SafePhotoUrlValidator(prodEnv);

        assertThat(prodValidator.isValid("http://localhost:8500/photos/sample.jpg", null)).isFalse();
        assertThat(prodValidator.isValid("http://127.0.0.1:8500/photos/sample.jpg", null)).isFalse();
        assertThat(prodValidator.isValid("https://localhost/photos/sample.jpg", null)).isFalse();

        MockEnvironment qaEnv = new MockEnvironment();
        qaEnv.setActiveProfiles("qa");
        qaEnv.setProperty("styleradar.tryon.security.allow-localhost-urls", "false");

        SafePhotoUrlValidator qaValidator = new SafePhotoUrlValidator(qaEnv);

        assertThat(qaValidator.isValid("http://localhost:8500/photos/sample.jpg", null)).isFalse();
        assertThat(qaValidator.isValid("http://127.0.0.1:8500/photos/sample.jpg", null)).isFalse();
    }

    @Test
    @DisplayName("SSRF DEV/TEST: Permite localhost y 127.0.0.1 exclusivamente en desarrollo local o pruebas controladas")
    void shouldAllowLocalhostOnlyInDevOrTestEnvironment() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        devEnv.setProperty("styleradar.tryon.security.allow-localhost-urls", "true");

        SafePhotoUrlValidator devValidator = new SafePhotoUrlValidator(devEnv);

        assertThat(devValidator.isValid("http://localhost:8500/photos/sample.jpg", null)).isTrue();
        assertThat(devValidator.isValid("http://127.0.0.1:8500/photos/sample.jpg", null)).isTrue();
        assertThat(devValidator.isValid("https://localhost:8080/image.png", null)).isTrue();
    }

    @Test
    @DisplayName("SSRF General: Rechaza direcciones de metadatos de nube, IPs privadas RFC 1918 y dominios externos no autorizados")
    void shouldRejectMaliciousSsrfUrlsInAllEnvironments() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        devEnv.setProperty("styleradar.tryon.security.allow-localhost-urls", "true");

        SafePhotoUrlValidator validator = new SafePhotoUrlValidator(devEnv);

        // Metadata Azure/AWS
        assertThat(validator.isValid("http://169.254.169.254/latest/meta-data", null)).isFalse();
        // Redes privadas internas
        assertThat(validator.isValid("http://10.0.0.1:8080/internal", null)).isFalse();
        assertThat(validator.isValid("http://192.168.1.1/admin", null)).isFalse();
        // Dominios externos arbitrarios
        assertThat(validator.isValid("https://attacker-controlled-site.com/avatar.jpg", null)).isFalse();
        // Esquemas inseguros
        assertThat(validator.isValid("file:///etc/passwd", null)).isFalse();
        assertThat(validator.isValid("gopher://127.0.0.1:70", null)).isFalse();
    }

    @Test
    @DisplayName("Validador maneja valores nulos o vacíos delegando en @NotBlank")
    void shouldReturnTrueForNullOrBlankValues() {
        SafePhotoUrlValidator validator = new SafePhotoUrlValidator(new MockEnvironment());

        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
    }

    @Test
    @DisplayName("Constructor por defecto sin contexto de Spring opera en modo seguro bloqueando localhost")
    void defaultConstructorOperatesInSecureMode() {
        SafePhotoUrlValidator validator = new SafePhotoUrlValidator();

        assertThat(validator.isLocalhostAllowed()).isFalse();
        assertThat(validator.isValid("http://localhost:8500/test.jpg", null)).isFalse();
        assertThat(validator.isValid("https://mystorage.blob.core.windows.net/container/test.jpg", null)).isTrue();
    }
}
