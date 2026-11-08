package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

/**
 * Manejador de errores de seguridad HTTP (401 Unauthorized y 403 Forbidden)
 * bajo el estándar RFC 7807 (ProblemDetail) y OWASP A9 (Security Logging).
 */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityProblemHandler.class);
    private static final String BASE_TYPE_URL = "https://styleradar.dosw.edu/errors/";

    private final ObjectMapper objectMapper;

    public SecurityProblemHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        log.warn("Acceso no autenticado (401) hacia {} {}: {}",
                request.getMethod(), request.getRequestURI(), authException.getMessage());
        response.setHeader("WWW-Authenticate", "Bearer");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Se requiere un token Bearer JWT válido para acceder a los recursos biométricos."
        );
        problem.setTitle("Autenticación Requerida o Inválida");
        problem.setType(URI.create(BASE_TYPE_URL + "unauthorized"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", "UNAUTHORIZED");
        problem.setProperty("timestamp", Instant.now());
        writeProblemDetail(response, HttpStatus.UNAUTHORIZED, problem);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        log.warn("Acceso denegado (403) hacia {} {}: {}",
                request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "No cuenta con los permisos o la titularidad requerida sobre los datos biométricos solicitados."
        );
        problem.setTitle("Acceso Denegado a Datos Biométricos");
        problem.setType(URI.create(BASE_TYPE_URL + "forbidden"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", "FORBIDDEN");
        problem.setProperty("timestamp", Instant.now());
        writeProblemDetail(response, HttpStatus.FORBIDDEN, problem);
    }

    private void writeProblemDetail(
            HttpServletResponse response,
            HttpStatus status,
            ProblemDetail problem
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}

