package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.doc;

import edu.dosw.styleradar.virtualtryon.application.dto.request.StartTryOnRequest;
import edu.dosw.styleradar.virtualtryon.application.dto.response.TryOnJobResponse;
import edu.dosw.styleradar.virtualtryon.application.dto.response.UserQuotaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Contrato de documentación OpenAPI 3.0 para el microservicio de Probador Virtual.
 * Separa las anotaciones de documentación de la lógica del controlador REST.
 */
@Tag(name = "Virtual Try-On", description = "Endpoints de orquestación de Virtual Try-On con IA, consulta de estados y privacidad biométrica")
public interface VirtualTryOnOpenApiDoc {

    @Operation(
            summary = "Iniciar trabajo de prueba virtual (Virtual Try-On)",
            description = "Recibe la fotografía frontal del usuario y la prenda seleccionada. Valida el consentimiento biométrico, " +
                    "verifica la disponibilidad de cuota mensual y despacha el trabajo asíncrono hacia el motor de IA. " +
                    "Retorna HTTP 202 Accepted con la cabecera Location hacia el recurso del trabajo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Trabajo aceptado y encolado exitosamente",
                    content = @Content(schema = @Schema(implementation = TryOnJobResponse.class))),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos o falta consentimiento biométrico",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Fotografía no procesable (formato inválido o corrupto)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Cuota mensual de pruebas virtuales excedida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<TryOnJobResponse> startTryOn(StartTryOnRequest request);

    @Operation(
            summary = "Consultar estado o resultado de un trabajo",
            description = "Permite al cliente realizar sondeo (polling) no bloqueante para consultar si la IA ha terminado de renderizar la prenda. " +
                    "Una vez completado, incluye la URL de la imagen resultante de alta resolución."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del trabajo recuperado exitosamente",
                    content = @Content(schema = @Schema(implementation = TryOnJobResponse.class))),
            @ApiResponse(responseCode = "404", description = "Trabajo no encontrado con el identificador proporcionado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<TryOnJobResponse> getTryOnJob(
            @Parameter(description = "Identificador único del trabajo (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            UUID jobId
    );

    @Operation(
            summary = "Consultar saldo de cuotas del usuario",
            description = "Consulta la cantidad de pruebas virtuales gratuitas consumidas y restantes en el mes calendario para el usuario autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo de cuotas obtenido exitosamente",
                    content = @Content(schema = @Schema(implementation = UserQuotaResponse.class))),
            @ApiResponse(responseCode = "401", description = "Usuario no autenticado (token JWT requerido)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<UserQuotaResponse> getUserQuotas(
            @Parameter(description = "Identificador del usuario (UUID)", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
            UUID userId
    );

    @Operation(
            summary = "Derecho al olvido: Borrado físico de fotografía biométrica",
            description = "En cumplimiento de la Ley 1581 de Habeas Data, ejecuta la purga criptográfica inmediata de la fotografía frontal " +
                    "del usuario en el almacenamiento de objetos, revocando cualquier enlace temporal de acceso."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Fotografía purgada físicamente de manera irreversible"),
            @ApiResponse(responseCode = "404", description = "Fotografía o trabajo no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<Void> deleteBiometricPhoto(
            @Parameter(description = "Identificador del trabajo o foto a eliminar", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            UUID jobId
    );
}
