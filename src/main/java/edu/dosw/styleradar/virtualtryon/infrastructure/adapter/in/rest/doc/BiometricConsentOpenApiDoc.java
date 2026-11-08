package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.doc;

import edu.dosw.styleradar.virtualtryon.application.dto.request.BiometricConsentRequest;
import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricConsentResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.BiometricPhotoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Contrato declarativo OpenAPI 3.0 para la gestión del consentimiento biométrico (Ley 1581 de 2012)
 * y la carga validada de fotografías frontales de silueta del usuario (SR-RF-23).
 */
@Tag(
        name = "Biometric Consent & Photos (SR-RF-23)",
        description = "Operaciones de consentimiento informado de datos biométricos (Ley 1581) y carga validada de fotografía frontal del usuario"
)
@RequestMapping("/api/v1/try-on")
@SecurityRequirement(name = "bearerAuth")
public interface BiometricConsentOpenApiDoc {

    @Operation(
            summary = "Otorgar consentimiento de tratamiento de datos biométricos (SR-RF-23 / Ley 1581)",
            description = "Registra la autorización explícita del usuario para el procesamiento de su silueta en el probador virtual."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Consentimiento biométrico registrado exitosamente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BiometricConsentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "El consentimiento fue rechazado o los datos de entrada son inválidos",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/consents")
    ResponseEntity<BiometricConsentResponse> grantConsent(@Valid @RequestBody BiometricConsentRequest request);

    @Operation(
            summary = "Consultar estado del consentimiento biométrico del usuario (SR-RF-23)",
            description = "Obtiene el registro de consentimiento biométrico más reciente otorgado por el usuario."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Consentimiento biométrico encontrado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BiometricConsentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "El usuario aún no ha otorgado consentimiento biométrico",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/consents/{userId}")
    ResponseEntity<BiometricConsentResponse> getConsent(
            @Parameter(description = "UUID del usuario", required = true) @PathVariable("userId") UUID userId
    );

    @Operation(
            summary = "Revocar consentimiento de tratamiento de datos biométricos (Ley 1581)",
            description = "Revoca la autorización de tratamiento de datos biométricos del usuario registrando la fecha de revocación."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Consentimiento biométrico revocado exitosamente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BiometricConsentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "El usuario no posee un consentimiento registrado para revocar",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @DeleteMapping("/consents/{userId}")
    ResponseEntity<BiometricConsentResponse> revokeConsent(
            @Parameter(description = "UUID del usuario", required = true) @PathVariable("userId") UUID userId
    );

    @Operation(
            summary = "Cargar fotografía frontal de silueta del usuario (SR-RF-23)",
            description = "Valida y almacena la fotografía frontal del usuario verificando consentimiento biométrico, formato JPEG o PNG, tamaño máximo de 5 MB, dimensiones mínimas 300x400 px y orientación vertical."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Fotografía frontal validada y almacenada exitosamente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BiometricPhotoResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Falta el consentimiento informado de tratamiento de datos biométricos",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Formato no soportado, tamaño superior a 5 MB, dimensiones menores a 300x400 px u orientación horizontal",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<BiometricPhotoResponse> uploadPhoto(
            @Parameter(description = "UUID del usuario propietario de la silueta", required = true)
            @RequestParam("userId") UUID userId,
            @Parameter(description = "Aceptación explícita de tratamiento biométrico (Ley 1581)", required = false)
            @RequestParam(value = "biometricConsent", defaultValue = "true") boolean biometricConsent,
            @Parameter(description = "Archivo de imagen frontal en formato JPEG o PNG (máx. 5 MB, mín. 300x400 px vertical)", required = true)
            @RequestPart("photo") MultipartFile photo
    );

    @Operation(
            summary = "Consultar fotografía frontal cargada por el usuario (SR-RF-23)",
            description = "Retorna los metadatos validados (dimensiones, formato, peso y URL SAS) de la fotografía frontal activa del usuario."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Metadatos de la fotografía frontal recuperados exitosamente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BiometricPhotoResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "El usuario no tiene una fotografía frontal cargada",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/photos/{userId}")
    ResponseEntity<BiometricPhotoResponse> getPhoto(
            @Parameter(description = "UUID del usuario", required = true) @PathVariable("userId") UUID userId
    );
}

