package edu.dosw.styleradar.virtualtryon.infrastructure.exception;

import edu.dosw.styleradar.virtualtryon.domain.exception.AiProviderTimeoutException;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.DomainException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas Unitarias de Reglas de Dominio y Casos Negativos.
 * Valida la captura de excepciones de negocio y su traducción a RFC 7807 (ProblemDetail).
 * Mapeadas directamente a las HUs de Jira (V03, V04, V05).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("HU SR-RF-26 / RF-27 (V05): Dado un job inexistente o ajeno (JobNotFoundException), Cuando se captura la excepción, Entonces retorna 404 ProblemDetail con código JOB_NOT_FOUND (Caso Negativo)")
    void testJobNotFound() {
        // Dado: Un identificador de trabajo que no existe en el repositorio
        UUID id = UUID.randomUUID();

        // Cuando: Se dispara JobNotFoundException
        ProblemDetail pd = handler.handleJobNotFoundException(new JobNotFoundException(id));

        // Entonces: Responde 404 Not Found con código estructurado
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getTitle()).isEqualTo("Trabajo de Probador Virtual No Encontrado");
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("JOB_NOT_FOUND");
    }

    @Test
    @DisplayName("HU SR-RF-25 (V04): Dado un usuario con límite de cuotas mensual superado (QuotaExceededException), Cuando se captura la excepción, Entonces retorna 429 ProblemDetail con código QUOTA_EXCEEDED (Caso Negativo)")
    void testQuotaExceeded() {
        // Dado: Un usuario que agotó sus créditos de pruebas virtuales
        UUID userId = UUID.randomUUID();

        // Cuando: Se dispara QuotaExceededException
        ProblemDetail pd = handler.handleQuotaExceededException(new QuotaExceededException(userId, 10));

        // Entonces: Responde 429 Too Many Requests sin bloquear hilos
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(pd.getTitle()).isEqualTo("Cuota Mensual Excedida");
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    @DisplayName("HU SR-RF-23 (V03): Dado un archivo de fotografía no procesable o corrupto (InvalidPhotoException), Cuando se captura la excepción, Entonces retorna 422 ProblemDetail con código INVALID_PHOTO (Caso Negativo)")
    void testInvalidPhoto() {
        // Dado: Una fotografía que no cumple dimensiones o está corrupta
        // Cuando: Se dispara InvalidPhotoException
        ProblemDetail pd = handler.handleInvalidPhotoException(new InvalidPhotoException("URL inválida"));

        // Entonces: Responde 422 Unprocessable Entity
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(pd.getTitle()).isEqualTo("Fotografía No Procesable");
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("INVALID_PHOTO");
    }

    @Test
    @DisplayName("HU SR-RF-23 (V03): Dado un usuario que no aceptó el consentimiento biométrico (ConsentRequiredException), Cuando se captura la excepción, Entonces retorna 400 ProblemDetail con código CONSENT_REQUIRED (Caso Negativo)")
    void testConsentRequired() {
        // Dado: Una solicitud sin autorización explícita de tratamiento biométrico (Ley 1581)
        // Cuando: Se dispara ConsentRequiredException
        ProblemDetail pd = handler.handleConsentRequiredException(new ConsentRequiredException());

        // Entonces: Responde 400 Bad Request
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getTitle()).isEqualTo("Consentimiento Legal Requerido");
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("CONSENT_REQUIRED");
    }

    @Test
    @DisplayName("HU SR-RF-24 (V03): Dado una prenda incompatible con el probador (InvalidGarmentException), Cuando se captura la excepción, Entonces retorna 400 ProblemDetail con código INVALID_GARMENT (Caso Negativo)")
    void testInvalidGarment() {
        // Dado: Una prenda que no corresponde a categorías superiores o inferiores
        UUID garmentId = UUID.randomUUID();

        // Cuando: Se dispara InvalidGarmentException
        ProblemDetail pd = handler.handleInvalidGarmentException(new InvalidGarmentException(garmentId, "Categoría no soportada"));

        // Entonces: Responde 400 Bad Request con descripción clara
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getTitle()).isEqualTo("Prenda Incompatible para Prueba Virtual");
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("INVALID_GARMENT");
    }

    @Test
    @DisplayName("HU SR-RF-25 (V04): Dado un timeout en el motor de IA externo (AiProviderTimeoutException), Cuando se captura la excepción, Entonces retorna 504 ProblemDetail con código AI_PROVIDER_TIMEOUT (Caso Negativo)")
    void testAiProviderTimeout() {
        // Dado: La API externa de IA no responde en el tiempo límite (< 30s)
        // Cuando: Se dispara AiProviderTimeoutException
        ProblemDetail pd = handler.handleAiProviderTimeoutException(new AiProviderTimeoutException("FASHN AI no respondió en 30s"));

        // Entonces: Responde 504 Gateway Timeout controlado sin exponer la foto
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT.value());
        assertThat(pd.getTitle()).isEqualTo("Tiempo de Espera de IA Agotado");
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("AI_PROVIDER_TIMEOUT");
    }

    @Test
    @DisplayName("HU Transversal: Dado una regla de negocio de dominio incumplida (DomainException), Cuando se captura la excepción, Entonces retorna 400 ProblemDetail con código personalizado (Caso Negativo)")
    void testGenericDomain() {
        // Dado: Una excepción de dominio personalizada
        DomainException customException = new DomainException("Violación regla", "RULE_ERROR") {};

        // Cuando: Se captura por el manejador genérico
        ProblemDetail pd = handler.handleGenericDomainException(customException);

        // Entonces: Responde 400 Bad Request
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getTitle()).isEqualTo("Regla de Negocio Incumplida");
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("RULE_ERROR");
    }

    @Test
    @DisplayName("HU Transversal: Dado un error no controlado en el servidor (RuntimeException), Cuando se captura la excepción, Entonces retorna 500 ProblemDetail con código INTERNAL_SERVER_ERROR (Caso Negativo)")
    void testUncaught() {
        // Dado: Una excepción no controlada en tiempo de ejecución
        // Cuando: Se captura por el manejador de fallback
        ProblemDetail pd = handler.handleUncaughtException(new RuntimeException("Error inesperado"));

        // Entonces: Responde 500 Internal Server Error seguro sin filtrar stack traces
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getTitle()).isEqualTo("Error Interno del Servidor");
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("HU SR-RF-23: Dada la ausencia de la parte multipart 'photo' o exceso de tamaño (> 5MB), Cuando se capturan las excepciones, Entonces retorna 400 y 413 ProblemDetail")
    void testMissingPartAndMaxUploadSize() {
        ProblemDetail missingPartPd = handler.handleMissingMultipartOrParamException(
                new MissingServletRequestPartException("photo")
        );
        assertThat(missingPartPd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(missingPartPd.getProperties().get("errorCode")).isEqualTo("MISSING_REQUEST_PART");

        ProblemDetail payloadTooLargePd = handler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(5L * 1024 * 1024)
        );
        assertThat(payloadTooLargePd.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(payloadTooLargePd.getProperties().get("errorCode")).isEqualTo("PAYLOAD_TOO_LARGE");
    }
}
