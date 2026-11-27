package edu.dosw.styleradar.virtualtryon.infrastructure.exception;

import edu.dosw.styleradar.virtualtryon.domain.exception.AiProviderTimeoutException;
import edu.dosw.styleradar.virtualtryon.domain.exception.BiometricPhotoNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.CatalogServiceUnavailableException;
import edu.dosw.styleradar.virtualtryon.domain.exception.ConsentRequiredException;
import edu.dosw.styleradar.virtualtryon.domain.exception.DomainException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotEligibleException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.GarmentSizeUnavailableException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidGarmentException;
import edu.dosw.styleradar.virtualtryon.domain.exception.InvalidPhotoException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobAccessDeniedException;
import edu.dosw.styleradar.virtualtryon.domain.exception.JobNotFoundException;
import edu.dosw.styleradar.virtualtryon.domain.exception.QuotaExceededException;
import edu.dosw.styleradar.virtualtryon.domain.exception.TryOnSelectionIncompleteException;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.filter.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    private static final String BASE_TYPE_URL = "https://api.styleradar.com/errors/";

    @ExceptionHandler(BiometricPhotoNotFoundException.class)
    public ProblemDetail handleBiometricPhotoNotFoundException(BiometricPhotoNotFoundException ex) {
        return buildProblem(
                HttpStatus.NOT_FOUND,
                "Fotografía Biométrica No Encontrada",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "photo-not-found"
        );
    }

    @ExceptionHandler(GarmentNotFoundException.class)
    public ProblemDetail handleGarmentNotFoundException(GarmentNotFoundException ex) {
        log.warn("Prenda no encontrada: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.NOT_FOUND,
                "Prenda No Encontrada",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "garment-not-found"
        );
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ProblemDetail handleJobNotFoundException(JobNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.NOT_FOUND,
                "Trabajo de Probador Virtual No Encontrado",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "job-not-found"
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("Ruta o recurso HTTP no encontrado: {}", ex.getResourcePath());
        return buildProblem(
                HttpStatus.NOT_FOUND,
                "Recurso No Encontrado",
                "El recurso solicitado no existe.",
                "RESOURCE_NOT_FOUND",
                BASE_TYPE_URL + "not-found"
        );
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ProblemDetail handleQuotaExceededException(QuotaExceededException ex) {
        log.warn("Límite de cuota excedido: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.TOO_MANY_REQUESTS,
                "Cuota Mensual Excedida",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "quota-exceeded"
        );
    }

    @ExceptionHandler(InvalidPhotoException.class)
    public ProblemDetail handleInvalidPhotoException(InvalidPhotoException ex) {
        log.warn("Fotografía inválida: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Fotografía No Procesable",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "invalid-photo"
        );
    }

    @ExceptionHandler(ConsentRequiredException.class)
    public ProblemDetail handleConsentRequiredException(ConsentRequiredException ex) {
        log.warn("Falta consentimiento legal: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Consentimiento Legal Requerido",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "consent-required"
        );
    }

    @ExceptionHandler(GarmentNotEligibleException.class)
    public ProblemDetail handleGarmentNotEligibleException(GarmentNotEligibleException ex) {
        log.warn("Prenda no disponible o no elegible para prueba virtual: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Prenda No Elegible",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "garment-not-eligible"
        );
    }

    @ExceptionHandler(TryOnSelectionIncompleteException.class)
    public ProblemDetail handleTryOnSelectionIncompleteException(TryOnSelectionIncompleteException ex) {
        log.warn("Selección incompleta para prueba virtual: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Selección Incompleta",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "tryon-selection-incomplete"
        );
    }

    @ExceptionHandler(GarmentSizeUnavailableException.class)
    public ProblemDetail handleGarmentSizeUnavailableException(GarmentSizeUnavailableException ex) {
        log.warn("Talla no disponible para la prenda: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Talla No Disponible",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "size-unavailable"
        );
    }

    @ExceptionHandler(InvalidGarmentException.class)
    public ProblemDetail handleInvalidGarmentException(InvalidGarmentException ex) {
        log.warn("Prenda incompatible o inválida: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Prenda Incompatible para Prueba Virtual",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "invalid-garment"
        );
    }

    @ExceptionHandler(AiProviderTimeoutException.class)
    public ProblemDetail handleAiProviderTimeoutException(AiProviderTimeoutException ex) {
        log.warn("Timeout en motor de IA: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.GATEWAY_TIMEOUT,
                "Tiempo de Espera de IA Agotado",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "ai-timeout"
        );
    }

    @ExceptionHandler(CatalogServiceUnavailableException.class)
    public ProblemDetail handleCatalogServiceUnavailableException(CatalogServiceUnavailableException ex) {
        log.warn("Servicio de catálogo temporalmente no disponible: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Servicio de Catálogo No Disponible",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "catalog-service-unavailable"
        );
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleGenericDomainException(DomainException ex) {
        log.warn("Error de regla de dominio: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Regla de Negocio Incumplida",
                ex.getMessage(),
                ex.getErrorCode(),
                BASE_TYPE_URL + "domain-violation"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Error de validación en parámetros de entrada");
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problem = buildProblem(
                HttpStatus.BAD_REQUEST,
                "Error de Validación de Entrada",
                "Uno o más campos de la petición no cumplen con las validaciones requeridas.",
                "VALIDATION_FAILED",
                BASE_TYPE_URL + "validation-error"
        );
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String paramName = (ex.getName() != null && !ex.getName().isBlank()) ? ex.getName() : "parámetro";
        log.warn("Parámetro '{}' con formato inválido: {}", paramName, ex.getMessage());
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Parámetro de Entrada Inválido",
                "El parámetro '" + paramName + "' tiene un formato inválido.",
                "INVALID_PARAMETER",
                BASE_TYPE_URL + "invalid-parameter"
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Argumento inválido en solicitud: {}", ex.getMessage());
        String detail = sanitizeClientMessage(
                ex.getMessage(),
                "Uno de los parámetros enviados tiene un formato inválido."
        );
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Solicitud Inválida",
                detail,
                "BAD_REQUEST",
                BASE_TYPE_URL + "bad-request"
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Cuerpo JSON malformado o no legible: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Cuerpo de Solicitud Inválido",
                "El cuerpo de la solicitud JSON está malformado o contiene tipos inválidos.",
                "MALFORMED_REQUEST_BODY",
                BASE_TYPE_URL + "malformed-request-body"
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.warn("Método HTTP no soportado: {}", ex.getMethod());
        String method = ex.getMethod();
        return buildProblem(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Método HTTP No Permitido",
                "El método HTTP '" + method + "' no está soportado para este recurso.",
                "METHOD_NOT_ALLOWED",
                BASE_TYPE_URL + "method-not-allowed"
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        log.warn("Tipo de contenido no soportado: {}", ex.getContentType());
        return buildProblem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Tipo de Contenido No Soportado",
                "El tipo de contenido enviado no es compatible con este recurso.",
                "UNSUPPORTED_MEDIA_TYPE",
                BASE_TYPE_URL + "unsupported-media-type"
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingServletRequestPartException(MissingServletRequestPartException ex) {
        log.warn("Falta parte multipart requerida: {}", ex.getRequestPartName());
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Parámetro o Archivo Multipart Faltante",
                "Falta la parte requerida '" + ex.getRequestPartName() + "' en la solicitud.",
                "MISSING_REQUEST_PART",
                BASE_TYPE_URL + "missing-request-part"
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.warn("Falta parámetro requerido: {}", ex.getParameterName());
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Parámetro o Archivo Multipart Faltante",
                "Falta el parámetro requerido '" + ex.getParameterName() + "' en la solicitud.",
                "MISSING_REQUEST_PART",
                BASE_TYPE_URL + "missing-request-part"
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("Archivo cargado excede el tamaño máximo permitido: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Archivo Demasiado Grande",
                "La fotografía supera el límite máximo permitido de 5 MB.",
                "PAYLOAD_TOO_LARGE",
                BASE_TYPE_URL + "payload-too-large"
        );
    }

    @ExceptionHandler(MultipartException.class)
    public ProblemDetail handleMultipartException(MultipartException ex) {
        log.warn("Solicitud multipart inválida o incompleta: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Solicitud Multipart Inválida",
                "La solicitud multipart está incompleta o tiene un formato inválido.",
                "INVALID_MULTIPART_REQUEST",
                BASE_TYPE_URL + "invalid-multipart-request"
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        log.warn("Fallo de autenticación en solicitud: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.UNAUTHORIZED,
                "Autenticación Requerida o Inválida",
                ex.getMessage(),
                "UNAUTHORIZED",
                BASE_TYPE_URL + "unauthorized"
        );
    }

    @ExceptionHandler({AccessDeniedException.class, JobAccessDeniedException.class})
    public ProblemDetail handleAccessDeniedException(Exception ex) {
        log.warn("Acceso denegado a recurso biométrico o probador virtual: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.FORBIDDEN,
                "Acceso Denegado",
                ex.getMessage(),
                "FORBIDDEN",
                BASE_TYPE_URL + "forbidden"
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUncaughtException(Exception ex) {
        log.error("Error no controlado en el servidor: ", ex);
        return buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error Interno del Servidor",
                "Ha ocurrido un error interno en el servidor. Por favor, contacte al soporte de StyleRadar.",
                "INTERNAL_SERVER_ERROR",
                BASE_TYPE_URL + "internal-server-error"
        );
    }

    private ProblemDetail buildProblem(
            HttpStatus status,
            String title,
            String detail,
            String errorCode,
            String typeUri
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        if (typeUri != null) {
            problem.setType(URI.create(typeUri));
        }
        problem.setProperty("code", errorCode);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("timestamp", Instant.now().toString());
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }

    private static String sanitizeClientMessage(String rawMessage, String fallback) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return fallback;
        }
        if (rawMessage.contains("Invalid UUID string")
                || rawMessage.contains("java.")
                || rawMessage.contains("org.springframework")
                || rawMessage.contains("No enum constant")) {
            return fallback;
        }
        return rawMessage;
    }
}
