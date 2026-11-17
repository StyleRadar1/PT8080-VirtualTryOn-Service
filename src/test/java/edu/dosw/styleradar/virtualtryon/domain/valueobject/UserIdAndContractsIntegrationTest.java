package edu.dosw.styleradar.virtualtryon.domain.valueobject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.application.dto.event.UserAccountDeletedEvent;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.AuthenticatedUser;
import edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Unificación de Identificadores y Contratos Reales (Auth & Catálogo)")
class UserIdAndContractsIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "StyleRadarSuperSecureSecretKeyForTestingPurposes2026AtLeast256Bits!".getBytes()
    );

    @Test
    @DisplayName("UserId: Convierte determinísticamente IDs numéricos de Auth y UUIDs estándar")
    void shouldConvertUserIdsDeterministically() {
        UUID originalUuid = UUID.randomUUID();
        assertEquals(originalUuid, UserId.parseToUuid(originalUuid));
        assertEquals(originalUuid, UserId.parseToUuid(originalUuid.toString()));
        assertEquals(originalUuid, UserId.fromString(originalUuid.toString()).value());

        // ID numérico de Auth como número y como String
        UUID fromLong = UserId.parseToUuid(12345L);
        UUID fromString = UserId.parseToUuid("12345");
        UUID fromPrefixed = UserId.parseToUuid("styleradar-user-12345");

        assertNotNull(fromLong);
        assertEquals(fromLong, fromString, "El UUID generado desde Long y String debe ser idéntico");
        assertEquals(fromLong, fromPrefixed, "El UUID con prefijo debe ser idempotente");

        // Casos nulos o vacíos
        assertNull(UserId.parseToUuid(null));
        assertNull(UserId.parseToUuid("   "));
    }

    @Test
    @DisplayName("GarmentId: Convierte determinísticamente IDs numéricos y UUIDs de Catálogo")
    void shouldConvertGarmentIdsDeterministically() {
        UUID originalUuid = UUID.randomUUID();
        assertEquals(originalUuid, GarmentId.parseToUuid(originalUuid));
        assertEquals(originalUuid, GarmentId.parseToUuid(originalUuid.toString()));
        assertEquals(originalUuid, GarmentId.fromString(originalUuid.toString()).value());

        UUID fromLong = GarmentId.parseToUuid(1001L);
        UUID fromString = GarmentId.parseToUuid("1001");
        UUID fromPrefixed = GarmentId.parseToUuid("styleradar-garment-1001");

        assertNotNull(fromLong);
        assertEquals(fromLong, fromString);
        assertEquals(fromLong, fromPrefixed);

        assertNull(GarmentId.parseToUuid(null));
        assertNull(GarmentId.parseToUuid("   "));
    }

    @Test
    @DisplayName("Unificación Crítica: El token JWT de Auth y el evento RabbitMQ UserAccountDeletedEvent generan el MISMO UUID")
    void shouldGenerateIdenticalUuidForJwtAndRabbitMqEvent() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);
        JwtTokenProvider jwtProvider = new JwtTokenProvider(TEST_SECRET, Duration.ofHours(2), fixedClock);

        Long authNumericUserId = 987654L;
        String authNumericString = "987654";

        // 1. Simulación de token JWT generado por Auth con subject numérico
        String authGeneratedJwt = io.jsonwebtoken.Jwts.builder()
                .subject(authNumericString)
                .claim("roles", List.of("COMPRADOR", "USER"))
                .issuedAt(java.util.Date.from(fixedClock.instant()))
                .expiration(java.util.Date.from(fixedClock.instant().plus(Duration.ofHours(1))))
                .signWith(new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(TEST_SECRET), "HmacSHA256"))
                .compact();

        AuthenticatedUser authenticatedUser = jwtProvider.validateAndExtractUser(authGeneratedJwt);
        assertNotNull(authenticatedUser);
        UUID userUuidFromJwt = authenticatedUser.userId();

        // 2. Simulación de evento styleradar.usuarios.cuenta.eliminada de RabbitMQ con id numérico
        String rabbitMqPayload = """
                {
                  "usuarioId": 987654,
                  "fechaEliminacion": "2026-11-17T12:00:00Z"
                }
                """;

        UserAccountDeletedEvent event = objectMapper.readValue(rabbitMqPayload, UserAccountDeletedEvent.class);
        assertNotNull(event);
        UUID userUuidFromRabbitMq = event.userId();

        // 3. Verificación de consistencia total (Ley 1581)
        assertEquals(userUuidFromJwt, userUuidFromRabbitMq,
                "CRÍTICO: El UUID del usuario autenticado vía JWT de Auth y el UUID del evento de RabbitMQ deben ser estrictamente idénticos para garantizar la purga biométrica");
        assertEquals(UserId.parseToUuid(authNumericUserId), userUuidFromJwt);
    }

    @Test
    @DisplayName("Catálogo: Deserializa evento prenda.publicada real confirmado por Nicolás con alias oficiales")
    void shouldDeserializeRealPrendaPublicadaEventFromCatalogo() throws Exception {
        String realPayload = """
                {
                  "prendaId": "1001",
                  "nombre": "Camisa Polo Estilo Slim",
                  "categoria": "TOPS",
                  "imagenUrl": "https://storage.styleradar.co/garments/polo-1001.png"
                }
                """;

        CatalogGarmentUpdatedEvent event = objectMapper.readValue(realPayload, CatalogGarmentUpdatedEvent.class);

        assertNotNull(event);
        assertEquals(GarmentId.parseToUuid("1001"), event.garmentId());
        assertEquals("Camisa Polo Estilo Slim", event.name());
        assertEquals("TOPS", event.category());
        assertEquals("https://storage.styleradar.co/garments/polo-1001.png", event.imageUrl());
        assertTrue(event.visible(), "Una prenda publicada sin flags restrictivos debe ser visible por defecto");
        assertNotNull(event.updatedAt());
    }

    @Test
    @DisplayName("Seguridad: AuthenticatedUser reconoce ADMIN_STYLERADAR y autoriza acceso administrativo")
    void shouldRecognizeAdminStyleradarRole() {
        UUID callerUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        AuthenticatedUser adminUser = new AuthenticatedUser(
                callerUserId,
                "ADMIN_STYLERADAR",
                List.of("ADMIN_STYLERADAR", "COMPRADOR")
        );

        assertTrue(adminUser.canAccessUser(targetUserId), "ADMIN_STYLERADAR debe tener acceso a cualquier usuario objetivo");
        assertEquals("ADMIN_STYLERADAR", adminUser.role());
    }
}
