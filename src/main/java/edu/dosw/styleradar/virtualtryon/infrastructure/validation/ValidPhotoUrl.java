package edu.dosw.styleradar.virtualtryon.infrastructure.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validador perimetral contra Server-Side Request Forgery (SSRF - OWASP A10).
 * En ambientes de QA y PROD restringe estrictamente el origen a Azure Blob Storage (*.blob.core.windows.net).
 * En desarrollo local admite excepciones controladas de localhost / 127.0.0.1.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafePhotoUrlValidator.class)
@Documented
public @interface ValidPhotoUrl {

    String message() default "La URL de la fotografía frontal debe pertenecer al almacenamiento autorizado de Azure Blob Storage (*.blob.core.windows.net)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
