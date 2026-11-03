package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.dosw.styleradar.virtualtryon.application.dto.request.IniciarTryOnRequest;
import edu.dosw.styleradar.virtualtryon.infrastructure.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de API (Capa de Adaptador REST / Controladores Web) mediante MockMvc.
 * Valida contratos HTTP, cabeceras y códigos de respuesta alineados a las HUs de Jira.
 */
@WebMvcTest(controllers = VirtualTryOnRestController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class VirtualTryOnRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("HU SR-RF-25 (V04): Dado una solicitud válida con foto y prenda compatible, Cuando se envía POST /api/v1/probador/jobs, Entonces responde 202 Accepted con cabecera Location y estado PENDIENTE")
    void testIniciarTryOnExitoso() throws Exception {
        // Dado: Un usuario con consentimiento y prenda válida
        IniciarTryOnRequest request = new IniciarTryOnRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "https://styleradar.blob.core.windows.net/photos/user-test.jpg",
                true
        );

        // Cuando: Se envía la petición asíncrona de prueba virtual
        // Entonces: El sistema responde 202 Accepted inmediatamente con Location hacia el job
        mockMvc.perform(post("/api/v1/probador/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andExpect(jsonPath("$.status", is("PENDIENTE")));
    }

    @Test
    @DisplayName("HU SR-RF-23 (V03): Dado un cuerpo de solicitud vacío sin campos obligatorios, Cuando se envía POST /api/v1/probador/jobs, Entonces responde 400 Bad Request con ProblemDetail de validación (Caso Negativo)")
    void testIniciarTryOnInvalido() throws Exception {
        // Dado: Un request vacío inválido
        // Cuando: Se invoca el endpoint de inicio de job
        // Entonces: Rechaza con 400 y detalle de campos inválidos
        mockMvc.perform(post("/api/v1/probador/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Error de Validación de Entrada")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.invalidFields", notNullValue()));
    }

    @Test
    @DisplayName("HU SR-RF-26 (V05): Dado un jobId existente de un job completado, Cuando se envía GET /api/v1/probador/jobs/{id}, Entonces responde 200 OK con estado COMPLETADO y URL temporal firmada SAS")
    void testConsultarJob() throws Exception {
        // Dado: Un identificador de trabajo generado previamente
        UUID jobId = UUID.randomUUID();

        // Cuando: Se consulta el resultado del procesamiento
        // Entonces: Retorna 200 OK con la URL generada del resultado
        mockMvc.perform(get("/api/v1/probador/jobs/{jobId}", jobId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", is(jobId.toString())))
                .andExpect(jsonPath("$.status", is("COMPLETADO")))
                .andExpect(jsonPath("$.resultImageUrl", notNullValue()));
    }

    @Test
    @DisplayName("HU SR-RF-25 (V04): Dado un usuario autenticado con userId, Cuando se envía GET /api/v1/probador/cuotas, Entonces responde 200 OK con cuotas consumidas y saldo restante")
    void testConsultarCuotas() throws Exception {
        // Dado: Un usuario autenticado con identificador específico
        UUID userId = UUID.randomUUID();

        // Cuando: Consulta su saldo de cuota mensual
        // Entonces: Retorna 200 OK con el límite y el saldo disponible
        mockMvc.perform(get("/api/v1/probador/cuotas")
                        .param("userId", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(userId.toString())))
                .andExpect(jsonPath("$.monthlyLimit", is(10)))
                .andExpect(jsonPath("$.remainingCount", is(7)));
    }

    @Test
    @DisplayName("HU SR-RF-25 (V04): Dado un usuario sin userId explícito en la URL, Cuando se envía GET /api/v1/probador/cuotas, Entonces responde 200 OK asignando el usuario por defecto de sesión")
    void testConsultarCuotasDefaultUser() throws Exception {
        // Dado: Una consulta sin parámetro userId
        UUID defaultUserId = UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d");

        // Cuando: Se solicita el balance de cuota
        // Entonces: Responde 200 OK asignando el usuario por defecto
        mockMvc.perform(get("/api/v1/probador/cuotas")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(defaultUserId.toString())));
    }

    @Test
    @DisplayName("HU SR-RF-27 (V05): Dado un jobId de foto corporal existente, Cuando se envía DELETE /api/v1/probador/fotos-personales/{id}, Entonces responde 204 No Content para purga física definitiva (Ley 1581)")
    void testBorrarFotoBiometrica() throws Exception {
        // Dado: Un jobId con fotografía biométrica almacenada
        UUID jobId = UUID.randomUUID();

        // Cuando: El usuario solicita el borrado irreversible por derecho al olvido
        // Entonces: El sistema purga la foto y responde 204 No Content
        mockMvc.perform(delete("/api/v1/probador/fotos-personales/{jobId}", jobId))
                .andExpect(status().isNoContent());
    }
}
