package edu.dosw.styleradar.virtualtryon.infrastructure.exception;

import edu.dosw.styleradar.virtualtryon.domain.exception.AiProviderTimeoutException;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.DomainException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global centralizado de excepciones bajo el estándar RFC 7807 (Problem Details).
 * Intercepta excepciones de dominio y validaciones, retornando respuestas estructuradas
 * sin exponer detalles internos de la arquitectura o stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_TYPE_URL = "https://styleradar.dosw.edu/errors/";

    /**
     * Captura JobNotFoundException -> HTTP 404 Not Found
     */
    @ExceptionHandler(JobNotFoundException.class)
    public ProblemDetail handleJobNotFoundException(JobNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Trabajo de Probador Virtual No Encontrado");
        problem.setType(URI.create(BASE_TYPE_URL + "job-not-found"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura QuotaExceededException -> HTTP 429 Too Many Requests
     */
    @ExceptionHandler(QuotaExceededException.class)
    public ProblemDetail handleQuotaExceededException(QuotaExceededException ex) {
        log.warn("Límite de cuota excedido: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Cuota Mensual Excedida");
        problem.setType(URI.create(BASE_TYPE_URL + "quota-exceeded"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura InvalidPhotoException -> HTTP 422 Unprocessable Entity
     */
    @ExceptionHandler(InvalidPhotoException.class)
    public ProblemDetail handleInvalidPhotoException(InvalidPhotoException ex) {
        log.warn("Fotografía inválida: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Fotografía No Procesable");
        problem.setType(URI.create(BASE_TYPE_URL + "invalid-photo"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura ConsentRequiredException -> HTTP 400 Bad Request
     */
    @ExceptionHandler(ConsentRequiredException.class)
    public ProblemDetail handleConsentRequiredException(ConsentRequiredException ex) {
        log.warn("Falta consentimiento legal: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Consentimiento Legal Requerido");
        problem.setType(URI.create(BASE_TYPE_URL + "consent-required"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura InvalidGarmentException -> HTTP 400 Bad Request (SR-RF-24)
     */
    @ExceptionHandler(InvalidGarmentException.class)
    public ProblemDetail handleInvalidGarmentException(InvalidGarmentException ex) {
        log.warn("Prenda incompatible o inválida: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Prenda Incompatible para Prueba Virtual");
        problem.setType(URI.create(BASE_TYPE_URL + "invalid-garment"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura AiProviderTimeoutException -> HTTP 504 Gateway Timeout (SR-RF-25)
     */
    @ExceptionHandler(AiProviderTimeoutException.class)
    public ProblemDetail handleAiProviderTimeoutException(AiProviderTimeoutException ex) {
        log.warn("Timeout en motor de IA: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage());
        problem.setTitle("Tiempo de Espera de IA Agotado");
        problem.setType(URI.create(BASE_TYPE_URL + "ai-timeout"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura cualquier otra excepción genérica de dominio -> HTTP 400 Bad Request
     */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleGenericDomainException(DomainException ex) {
        log.warn("Error de regla de dominio: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Regla de Negocio Incumplida");
        problem.setType(URI.create(BASE_TYPE_URL + "domain-violation"));
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura errores de validación de Bean Validation (@Valid, @NotNull, etc.) -> HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Error de validación en parámetros de entrada");
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Uno o más campos de la petición no cumplen con las validaciones requeridas."
        );
        problem.setTitle("Error de Validación de Entrada");
        problem.setType(URI.create(BASE_TYPE_URL + "validation-error"));
        problem.setProperty("errorCode", "VALIDATION_FAILED");
        problem.setProperty("invalidFields", fieldErrors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura ausencia de partes multipart o parámetros obligatorios -> HTTP 400 Bad Request (SR-RF-23)
     */
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ProblemDetail handleMissingMultipartOrParamException(Exception ex) {
        log.warn("Falta parte multipart o parámetro requerido: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Parámetro o Archivo Multipart Faltante");
        problem.setType(URI.create(BASE_TYPE_URL + "missing-request-part"));
        problem.setProperty("errorCode", "MISSING_REQUEST_PART");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura archivos multipart que exceden el límite del contenedor -> HTTP 413 Payload Too Large (SR-RF-23)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("Archivo cargado excede el tamaño máximo permitido: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "La fotografía supera el límite máximo permitido de 5 MB."
        );
        problem.setTitle("Archivo Demasiado Grande");
        problem.setType(URI.create(BASE_TYPE_URL + "payload-too-large"));
        problem.setProperty("errorCode", "PAYLOAD_TOO_LARGE");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura fallos de autenticación (401 Unauthorized) en endpoints protegidos (SR-RF-23)
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        log.warn("Fallo de autenticación en solicitud: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Autenticación Requerida o Inválida");
        problem.setType(URI.create(BASE_TYPE_URL + "unauthorized"));
        problem.setProperty("errorCode", "UNAUTHORIZED");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Captura accesos denegados por rol o titularidad de datos biométricos y probador virtual (403 Forbidden) (SR-RF-23, SR-RF-26)
     */
    @ExceptionHandler({AccessDeniedException.class, edu.dosw.styleradar.virtualtryon.domain.exception.JobAccessDeniedException.class})
    public ProblemDetail handleAccessDeniedException(Exception ex) {
        log.warn("Acceso denegado a recurso biométrico o probador virtual: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Acceso Denegado");
        problem.setType(URI.create(BASE_TYPE_URL + "forbidden"));
        problem.setProperty("errorCode", "FORBIDDEN");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Fallback para cualquier excepción no controlada -> HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUncaughtException(Exception ex) {
        log.error("Error no controlado en el servidor: ", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ha ocurrido un error interno en el servidor. Por favor, contacte al soporte de StyleRadar."
        );
        problem.setTitle("Error Interno del Servidor");
        problem.setType(URI.create(BASE_TYPE_URL + "internal-server-error"));
        problem.setProperty("errorCode", "INTERNAL_SERVER_ERROR");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
