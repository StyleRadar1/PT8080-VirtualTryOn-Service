package edu.dosw.styleradar.virtualtryon.infrastructure.exception;

import edu.dosw.styleradar.virtualtryon.domain.exception.AiProviderTimeoutException;
import edu.dosw.styleradar.virtualtryon.domain.exception.BiometricPhotoNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.CatalogServiceUnavailableException;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.DomainException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.filter.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas Unitarias de Reglas de Dominio y Casos Negativos.
 * Valida la captura de excepciones de negocio y su traducción a RFC 7807 (ProblemDetail).
 * Mapeadas directamente a las HUs de Jira (V03, V04, V05).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

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
        ProblemDetail missingPartPd = handler.handleMissingServletRequestPartException(
                new MissingServletRequestPartException("photo")
        );
        assertThat(missingPartPd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(missingPartPd.getProperties().get("errorCode")).isEqualTo("MISSING_REQUEST_PART");
        assertThat(missingPartPd.getDetail()).isEqualTo("Falta la parte requerida 'photo' en la solicitud.");

        ProblemDetail missingParamPd = handler.handleMissingServletRequestParameterException(
                new MissingServletRequestParameterException("selectedSize", "String")
        );
        assertThat(missingParamPd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(missingParamPd.getDetail()).isEqualTo("Falta el parámetro requerido 'selectedSize' en la solicitud.");

        ProblemDetail payloadTooLargePd = handler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(5L * 1024 * 1024)
        );
        assertThat(payloadTooLargePd.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(payloadTooLargePd.getProperties().get("errorCode")).isEqualTo("PAYLOAD_TOO_LARGE");

        ProblemDetail unauthorizedPd = handler.handleAuthenticationException(
                new BadCredentialsException("Token JWT inválido")
        );
        assertThat(unauthorizedPd.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(unauthorizedPd.getProperties().get("errorCode")).isEqualTo("UNAUTHORIZED");

        ProblemDetail forbiddenPd = handler.handleAccessDeniedException(
                new AccessDeniedException("Sin permisos biométricos")
        );
        assertThat(forbiddenPd.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(forbiddenPd.getProperties().get("errorCode")).isEqualTo("FORBIDDEN");

        UUID missingUserId = UUID.randomUUID();
        ProblemDetail photoNotFoundPd = handler.handleBiometricPhotoNotFoundException(
                new BiometricPhotoNotFoundException(missingUserId)
        );
        assertThat(photoNotFoundPd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(photoNotFoundPd.getTitle()).isEqualTo("Fotografía Biométrica No Encontrada");
        assertThat(photoNotFoundPd.getProperties().get("errorCode")).isEqualTo("BIOMETRIC_PHOTO_NOT_FOUND");
    }

    @Test
    @DisplayName("HU SR-RF-23/24: Errores de validación @Valid retornan 400 ProblemDetail con propiedad estándar 'errors'")
    void shouldHandleValidationExceptionUsingErrorsProperty() throws Exception {
        org.springframework.validation.BeanPropertyBindingResult bindingResult =
                new org.springframework.validation.BeanPropertyBindingResult(new Object(), "startTryOnRequest");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "startTryOnRequest", "garmentId", "es obligatorio"
        ));
        org.springframework.web.bind.MethodArgumentNotValidException validationEx =
                new org.springframework.web.bind.MethodArgumentNotValidException(null, bindingResult);

        ProblemDetail pd = handler.handleValidationException(validationEx);
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("VALIDATION_FAILED");
        assertThat(pd.getProperties()).containsKey("errors").doesNotContainKey("invalidFields");
    }

    @Test
    @DisplayName("HU SR-RF-24: Fallos temporales o timeouts de Catálogo (CatalogServiceUnavailableException) retornan 503 ProblemDetail")
    void testCatalogServiceUnavailable() {
        ProblemDetail pd = handler.handleCatalogServiceUnavailableException(
                new CatalogServiceUnavailableException("Catálogo respondió 503", new RuntimeException("503"))
        );

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(pd.getTitle()).isEqualTo("Servicio de Catálogo No Disponible");
        assertThat(pd.getProperties().get("errorCode")).isEqualTo("CATALOG_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("BUG-009 / BUG-022 / BUG-031 / BUG-032: Mapea excepciones HTTP/formato en español sin filtrar tipos Java e incluye correlationId del MDC")
    void testHttpAndValidationFormatHandlersAndCorrelationId() {
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "corr-tryon-42");

        ProblemDetail garmentNotFoundPd = handler.handleGarmentNotFoundException(
                new GarmentNotFoundException(UUID.randomUUID())
        );
        assertThat(garmentNotFoundPd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(garmentNotFoundPd.getProperties().get("errorCode")).isEqualTo("GARMENT_NOT_FOUND");
        assertThat(garmentNotFoundPd.getProperties().get("correlationId")).isEqualTo("corr-tryon-42");

        MethodArgumentTypeMismatchException typeMismatch = new MethodArgumentTypeMismatchException(
                "abc", UUID.class, "jobId", null, new IllegalArgumentException("Invalid UUID string: abc")
        );
        ProblemDetail mismatchPd = handler.handleMethodArgumentTypeMismatchException(typeMismatch);
        assertThat(mismatchPd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(mismatchPd.getDetail()).isEqualTo("El parámetro 'jobId' tiene un formato inválido.");
        assertThat(mismatchPd.getDetail()).doesNotContain("java.util.UUID");
        assertThat(mismatchPd.getProperties().get("correlationId")).isEqualTo("corr-tryon-42");

        ProblemDetail noResourcePd = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/try-on/desconocido")
        );
        assertThat(noResourcePd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(noResourcePd.getDetail()).isEqualTo("El recurso solicitado no existe.");

        ProblemDetail illegalArgPd = handler.handleIllegalArgumentException(
                new IllegalArgumentException("Invalid UUID string: xyz")
        );
        assertThat(illegalArgPd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(illegalArgPd.getDetail()).isEqualTo("Uno de los parámetros enviados tiene un formato inválido.");

        ProblemDetail unreadablePd = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("JSON parse error: Unexpected character", (HttpInputMessage) null)
        );
        assertThat(unreadablePd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(unreadablePd.getDetail()).doesNotContain("JSON parse error");

        ProblemDetail methodNotAllowedPd = handler.handleHttpRequestMethodNotSupportedException(
                new HttpRequestMethodNotSupportedException("DELETE")
        );
        assertThat(methodNotAllowedPd.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(methodNotAllowedPd.getProperties().get("errorCode")).isEqualTo("METHOD_NOT_ALLOWED");

        ProblemDetail mediaTypePd = handler.handleHttpMediaTypeNotSupportedException(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, java.util.List.of(MediaType.APPLICATION_JSON))
        );
        assertThat(mediaTypePd.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        assertThat(mediaTypePd.getProperties().get("errorCode")).isEqualTo("UNSUPPORTED_MEDIA_TYPE");

        ProblemDetail multipartPd = handler.handleMultipartException(
                new MultipartException("Current request is not a multipart request")
        );
        assertThat(multipartPd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(multipartPd.getProperties().get("errorCode")).isEqualTo("INVALID_MULTIPART_REQUEST");
    }
}
