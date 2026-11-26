package edu.dosw.styleradar.virtualtryon.domain.valueobject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.dosw.styleradar.virtualtryon.application.dto.event.CatalogGarmentUpdatedEvent;
import edu.dosw.styleradar.virtualtryon.application.dto.event.UserAccountDeletedEvent;
import edu.dosw.styleradar.virtualtryon.application.dto.event.UserSubscriptionPlanEvent;
import edu.dosw.styleradar.virtualtryon.application.dto.request.BiometricConsentRequest;
import edu.dosw.styleradar.virtualtryon.application.dto.request.StartTryOnRequest;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
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
        assertNull(UserId.parseToUuid("not-numeric-and-not-uuid"));

        // fromFlexible y branches de UserId
        UserId uObj = UserId.of(originalUuid);
        assertEquals(originalUuid, UserId.parseToUuid(uObj));
        assertEquals(uObj, UserId.fromFlexible(originalUuid));
        assertEquals(uObj, UserId.fromFlexible(uObj));
        assertEquals(fromLong, UserId.fromFlexible("12345").value());
        assertNull(UserId.fromFlexible(null));
        assertNull(UserId.fromFlexible("not-valid"));
    }

    @Test
    @DisplayName("GarmentId: Distingue identificadores malformados (BUG-009) de IDs numéricos y UUIDs válidos de Catálogo")
    void shouldConvertGarmentIdsDeterministically() {
        UUID originalUuid = UUID.randomUUID();
        assertEquals(originalUuid, GarmentId.parseToUuid(originalUuid));
        assertEquals(originalUuid, GarmentId.parseToUuid(originalUuid.toString()));
        assertEquals(originalUuid, GarmentId.fromString(originalUuid.toString()).value());

        // Identificadores numéricos soportados históricamente
        UUID fromLong = GarmentId.parseToUuid(1001L);
        UUID fromString = GarmentId.parseToUuid("1001");
        UUID fromPrefixed = GarmentId.parseToUuid("styleradar-garment-1001");

        assertNotNull(fromLong);
        assertEquals(fromLong, fromString);
        assertEquals(fromLong, fromPrefixed);

        // BUG-009: Identificadores malformados o arbitrarios retornan null en parseToUuid y fallan en fromString
        assertNull(GarmentId.parseToUuid("polo-casual-slim"));
        assertNull(GarmentId.parseToUuid("invalid-id-format"));
        assertNull(GarmentId.parseToUuid("123-abc"));
        assertNull(GarmentId.parseToUuid("styleradar-garment-xyz"));
        assertNull(GarmentId.parseToUuid(null));
        assertNull(GarmentId.parseToUuid("   "));

        assertThrows(IllegalArgumentException.class, () -> GarmentId.fromString("polo-casual-slim"));
        assertThrows(IllegalArgumentException.class, () -> GarmentId.fromString("invalid-id-format"));
        assertThrows(NullPointerException.class, () -> GarmentId.fromString(null));

        // fromFlexible y branches de GarmentId
        GarmentId gObj = GarmentId.of(originalUuid);
        assertEquals(originalUuid, GarmentId.parseToUuid(gObj));
        assertEquals(originalUuid, gObj.getValue());
        assertEquals(gObj, GarmentId.fromFlexible(originalUuid));
        assertEquals(gObj, GarmentId.fromFlexible(gObj));
        assertEquals(fromLong, GarmentId.fromFlexible("1001").value());
        assertNull(GarmentId.fromFlexible("polo-casual-slim"));
        assertNull(GarmentId.fromFlexible(null));
        assertNull(GarmentId.fromFlexible("   "));
    }

    @Test
    @DisplayName("DTOs: StartTryOnRequest y BiometricConsentRequest constructores sobrecargados y acceso")
    void shouldCoverRequestDtosConstructors() {
        UUID u = UUID.randomUUID();
        UUID g = UUID.randomUUID();

        StartTryOnRequest req1 = new StartTryOnRequest(g, "https://photo.url/img.jpg", true);
        assertNull(req1.userId());
        assertEquals(g, req1.garmentId());
        assertEquals("https://photo.url/img.jpg", req1.photoUrl());
        assertTrue(req1.biometricConsent());

        StartTryOnRequest req2 = new StartTryOnRequest(u, g, "https://photo.url/img.jpg", true);
        assertEquals(u, req2.userId());

        BiometricConsentRequest cReq1 = new BiometricConsentRequest(u, "v1.0", true, List.of("TEST"), "127.0.0.1");
        assertEquals(u, cReq1.userId());
        assertEquals("v1.0", cReq1.termsVersion());
        assertTrue(cReq1.accepted());
    }

    @Test
    @DisplayName("JwtTokenProvider: Soporta generateTokenForSubject, roles por defecto y validación de vigencia")
    void shouldCoverJwtTokenProviderBranches() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);
        JwtTokenProvider jwtProvider = new JwtTokenProvider(TEST_SECRET, Duration.ofHours(2), fixedClock);

        // Subject numérico con rol explícito
        String token1 = jwtProvider.generateTokenForSubject("12345", "COMPRADOR");
        assertNotNull(token1);
        AuthenticatedUser user1 = jwtProvider.validateAndExtractUser(token1);
        assertEquals(UserId.parseToUuid("12345"), user1.userId());
        assertEquals("COMPRADOR", user1.role());

        // Rol nulo o en blanco -> COMPRADOR por defecto
        String token2 = jwtProvider.generateTokenForSubject("67890", null);
        AuthenticatedUser user2 = jwtProvider.validateAndExtractUser(token2);
        assertEquals("COMPRADOR", user2.role());

        String token3 = jwtProvider.generateTokenForSubject("67890", "   ");
        AuthenticatedUser user3 = jwtProvider.validateAndExtractUser(token3);
        assertEquals("COMPRADOR", user3.role());

        // Token con subject UUID estándar vía generateToken(UUID, String)
        UUID standardUuid = UUID.randomUUID();
        String token4 = jwtProvider.generateToken(standardUuid, "USER");
        AuthenticatedUser user4 = jwtProvider.validateAndExtractUser(token4);
        assertEquals(standardUuid, user4.userId());

        // Validaciones de argumentos inválidos
        assertThrows(IllegalArgumentException.class, () -> jwtProvider.generateToken(null, "COMPRADOR"));
        assertThrows(IllegalArgumentException.class, () -> jwtProvider.generateTokenForSubject(null, "COMPRADOR"));
        assertThrows(IllegalArgumentException.class, () -> jwtProvider.generateTokenForSubject("   ", "COMPRADOR"));

        // Claims alternos de userId ("user_id", "id", "usuarioId")
        String customClaimsJwt = io.jsonwebtoken.Jwts.builder()
                .claim("user_id", standardUuid.toString())
                .claim("role", "COMPRADOR")
                .issuedAt(java.util.Date.from(fixedClock.instant()))
                .expiration(java.util.Date.from(fixedClock.instant().plus(Duration.ofHours(1))))
                .signWith(new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(TEST_SECRET), "HmacSHA256"))
                .compact();
        AuthenticatedUser customUser = jwtProvider.validateAndExtractUser(customClaimsJwt);
        assertEquals(standardUuid, customUser.userId());
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

    @Test
    @DisplayName("Catálogo: Deserializa los 3 eventos reales de RabbitMQPublisherAdapter con LocalDateTime sin Z, fechaModeracion y tildes")
    void shouldDeserializeRealItemCatalogEventsWithLocalDateTimeAndAccents() throws Exception {
        UUID garmentUuid = UUID.randomUUID();
        UUID storeUuid = UUID.randomUUID();

        // 1. styleradar.catalogo.prenda.publicada
        String publicadaPayload = """
                {
                  "eventoId": "%s",
                  "productor": "ms-item-catalogo",
                  "itemId": "%s",
                  "prendaId": "%s",
                  "tiendaId": "%s",
                  "almacenId": "%s",
                  "nombrePrenda": "Pantalón Clásico",
                  "categoria": "Pantalón",
                  "color": "Negro",
                  "marca": "StyleCo",
                  "estilo": "Casual",
                  "precio": 120000,
                  "imagenUrl": "https://storage.styleradar.co/garments/pant-1.png",
                  "fechaPublicacion": "2026-11-21T18:00:00"
                }
                """.formatted(UUID.randomUUID(), garmentUuid, garmentUuid, storeUuid, storeUuid);

        CatalogGarmentUpdatedEvent publicada = objectMapper.readValue(publicadaPayload, CatalogGarmentUpdatedEvent.class);
        assertEquals(garmentUuid, publicada.garmentId());
        assertEquals("Pantalón Clásico", publicada.name());
        assertEquals("Pantalón", publicada.category());
        assertEquals("https://storage.styleradar.co/garments/pant-1.png", publicada.imageUrl());
        assertTrue(publicada.visible());
        assertEquals(Instant.parse("2026-11-21T18:00:00Z"), publicada.updatedAt());

        // 2. styleradar.catalogo.prenda.retirada
        String retiradaPayload = """
                {
                  "eventoId": "%s",
                  "productor": "ms-item-catalogo",
                  "itemId": "%s",
                  "prendaId": "%s",
                  "tiendaId": "%s",
                  "almacenId": "%s",
                  "motivo": "Retirada por tienda",
                  "fechaEvento": "2026-11-21T18:05:00"
                }
                """.formatted(UUID.randomUUID(), garmentUuid, garmentUuid, storeUuid, storeUuid);

        CatalogGarmentUpdatedEvent retirada = objectMapper.readValue(retiradaPayload, CatalogGarmentUpdatedEvent.class);
        assertEquals(garmentUuid, retirada.garmentId());
        assertEquals(Instant.parse("2026-11-21T18:05:00Z"), retirada.updatedAt());

        // 3. styleradar.catalogo.moderacion.cambiada
        String moderacionPayload = """
                {
                  "eventoId": "%s",
                  "productor": "ms-item-catalogo",
                  "itemId": "%s",
                  "prendaId": "%s",
                  "tiendaId": "%s",
                  "almacenId": "%s",
                  "nombrePrenda": "Pantalón Clásico",
                  "estadoModeracion": "RECHAZADA",
                  "observacion": "Imagen no válida",
                  "fechaModeracion": "2026-11-21T18:10:00"
                }
                """.formatted(UUID.randomUUID(), garmentUuid, garmentUuid, storeUuid, storeUuid);

        CatalogGarmentUpdatedEvent moderacion = objectMapper.readValue(moderacionPayload, CatalogGarmentUpdatedEvent.class);
        assertEquals(garmentUuid, moderacion.garmentId());
        assertEquals("Pantalón Clásico", moderacion.name());
        assertFalse(moderacion.visible(), "estadoModeracion RECHAZADA debe marcar la prenda como no visible");
        assertEquals(Instant.parse("2026-11-21T18:10:00Z"), moderacion.updatedAt());

        // Validaciones adicionales de parseFlexibleInstant y GarmentCategory con tildes y tipos de Catálogo
        assertEquals(Instant.parse("2026-11-21T23:00:00Z"),
                CatalogGarmentUpdatedEvent.parseFlexibleInstant("2026-11-21T18:00:00-05:00"));
        Instant directInstant = Instant.parse("2026-11-21T18:00:00Z");
        assertEquals(directInstant, CatalogGarmentUpdatedEvent.parseFlexibleInstant(directInstant));
        assertNotNull(CatalogGarmentUpdatedEvent.parseFlexibleInstant("   "));
        assertNotNull(CatalogGarmentUpdatedEvent.parseFlexibleInstant(null));

        assertEquals(GarmentCategory.BOTTOM, GarmentCategory.fromRawValue("Pantalón"));
        assertEquals(GarmentCategory.TOP, GarmentCategory.fromRawValue("Saco"));
        assertEquals(GarmentCategory.TOP, GarmentCategory.fromRawValue("Blazer"));
        assertEquals(GarmentCategory.BOTTOM, GarmentCategory.fromRawValue("Jean"));
        assertEquals(GarmentCategory.TOP, GarmentCategory.fromRawValue("Suéter"));
        assertEquals(GarmentCategory.ACCESSORIES, GarmentCategory.fromRawValue("Joyería"));
    }

    @Test
    @DisplayName("Pagos: Deserializa PaymentConfirmedEvent y SubscriptionSuspendedEvent reales con bloque data e ID numérico")
    void shouldDeserializeRealPaymentAndSubscriptionEvents() throws Exception {
        String paymentConfirmedJson = """
                {
                  "eventId": "%s",
                  "eventType": "styleradar.pagos.pago.confirmado",
                  "version": 1,
                  "timestamp": "2026-11-21T19:00:00Z",
                  "producer": "ms-pagos-suscripciones",
                  "correlationId": "corr-123",
                  "data": {
                    "ordenId": 100,
                    "suscripcionId": 55,
                    "suscriptorId": "12",
                    "usuarioId": 12,
                    "tipoSuscriptor": "USER",
                    "planId": 2,
                    "nit": null,
                    "almacenId": null,
                    "estado": "APPROVED"
                  }
                }
                """.formatted(UUID.randomUUID());

        UserSubscriptionPlanEvent confirmed = objectMapper.readValue(paymentConfirmedJson, UserSubscriptionPlanEvent.class);
        assertEquals(UserId.parseToUuid(12L), confirmed.userId());
        assertEquals("USER", confirmed.subscriberType());
        assertEquals(2L, confirmed.planId());
        assertEquals("APPROVED", confirmed.status());
        assertFalse(confirmed.isStoreSubscription());
        assertTrue(confirmed.isApproved());
        assertTrue(confirmed.isRadarPlusSubscription());
        assertEquals(Instant.parse("2026-11-21T19:00:00Z"), confirmed.occurredAt());

        String storePaymentJson = """
                {
                  "eventId": "%s",
                  "eventType": "styleradar.pagos.pago.confirmado",
                  "timestamp": "2026-11-21T19:05:00Z",
                  "data": {
                    "suscriptorId": "store-99",
                    "tipoSuscriptor": "STORE",
                    "planId": 3,
                    "almacenId": "%s",
                    "estado": "APPROVED"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        UserSubscriptionPlanEvent storeEvent = objectMapper.readValue(storePaymentJson, UserSubscriptionPlanEvent.class);
        assertTrue(storeEvent.isStoreSubscription());
        assertFalse(storeEvent.isRadarPlusSubscription());

        String suspendedJson = """
                {
                  "eventId": "%s",
                  "eventType": "styleradar.pagos.suscripcion.suspendida",
                  "version": 1,
                  "timestamp": "2026-11-21T19:10:00Z",
                  "producer": "ms-pagos-suscripciones",
                  "data": {
                    "suscripcionId": 55,
                    "suscriptorId": "12",
                    "tipoSuscriptor": "USER",
                    "planId": 2,
                    "fechaFin": "2026-11-20",
                    "motivo": "Periodo expirado"
                  }
                }
                """.formatted(UUID.randomUUID());

        UserSubscriptionPlanEvent suspended = objectMapper.readValue(suspendedJson, UserSubscriptionPlanEvent.class);
        assertEquals(UserId.parseToUuid("12"), suspended.userId());
        assertFalse(suspended.isStoreSubscription());
        assertTrue(suspended.isRadarPlusSubscription());
        assertFalse(suspended.isApproved(), "Un evento sin estado explícito no debe considerarse pago aprobado");

        // Payloads planos alternos (plan textual, rechazo, otro plan e incompleto)
        UserSubscriptionPlanEvent flatRadarPlus = objectMapper.readValue("""
                {"usuarioId":"12","plan":"Radar+","estado":"APROBADO"}
                """, UserSubscriptionPlanEvent.class);
        assertEquals(2L, flatRadarPlus.planId());
        assertTrue(flatRadarPlus.isApproved());
        assertTrue(flatRadarPlus.isRadarPlusSubscription());
        assertFalse(flatRadarPlus.isStoreSubscription());

        UserSubscriptionPlanEvent otherPlan = objectMapper.readValue("""
                {"usuarioId":"12","tipoSuscriptor":"USER","planId":1,"estado":"APPROVED"}
                """, UserSubscriptionPlanEvent.class);
        assertTrue(otherPlan.isApproved());
        assertFalse(otherPlan.isRadarPlusSubscription());

        UserSubscriptionPlanEvent incompleteEvent = objectMapper.readValue("""
                {"usuarioId":"12"}
                """, UserSubscriptionPlanEvent.class);
        assertFalse(incompleteEvent.isApproved());
        assertFalse(incompleteEvent.isRadarPlusSubscription());

        UserSubscriptionPlanEvent flatStoreByPlanId = objectMapper.readValue("""
                {"suscriptorId":"store-1","planId":3,"estado":"REJECTED"}
                """, UserSubscriptionPlanEvent.class);
        assertTrue(flatStoreByPlanId.isStoreSubscription());
        assertFalse(flatStoreByPlanId.isRadarPlusSubscription());
        assertFalse(flatStoreByPlanId.isApproved());

        UserSubscriptionPlanEvent flatStoreByName = objectMapper.readValue("""
                {"suscriptorId":"store-2","plan":"Store Basic"}
                """, UserSubscriptionPlanEvent.class);
        assertTrue(flatStoreByName.isStoreSubscription());
        assertFalse(flatStoreByName.isRadarPlusSubscription());
        assertNull(flatStoreByName.occurredAt(), "Cuando falta timestamp en JSON debe preservar null para evitar sobreescribir planes vigentes (Point 4 líder)");

        UserSubscriptionPlanEvent updatedAtEvent = objectMapper.readValue("""
                {"usuarioId":"12","planId":2,"estado":"APPROVED","updatedAt":"2026-11-22T10:00:00Z"}
                """, UserSubscriptionPlanEvent.class);
        assertEquals(Instant.parse("2026-11-22T10:00:00Z"), updatedAtEvent.occurredAt());

        UserSubscriptionPlanEvent fechaEvent = objectMapper.readValue("""
                {"usuarioId":"12","planId":2,"estado":"APPROVED","data":{"fecha":"2026-11-22T11:00:00Z"}}
                """, UserSubscriptionPlanEvent.class);
        assertEquals(Instant.parse("2026-11-22T11:00:00Z"), fechaEvent.occurredAt());

        UserSubscriptionPlanEvent eventDateEvent = objectMapper.readValue("""
                {"usuarioId":"12","planId":2,"estado":"APPROVED","eventDate":"2026-11-22T12:00:00Z"}
                """, UserSubscriptionPlanEvent.class);
        assertEquals(Instant.parse("2026-11-22T12:00:00Z"), eventDateEvent.occurredAt());
    }

    @Test
    @DisplayName("RabbitMQ Cuotas: CatalogGarmentUpdatedEvent.parseFlexibleInstantOrNull retorna null ante timestamp vacío, espacios o malformado")
    void shouldReturnNullWhenTimestampIsEmptyOrMalformed() throws Exception {
        assertNull(CatalogGarmentUpdatedEvent.parseFlexibleInstantOrNull(null));
        assertNull(CatalogGarmentUpdatedEvent.parseFlexibleInstantOrNull(""));
        assertNull(CatalogGarmentUpdatedEvent.parseFlexibleInstantOrNull("   "));
        assertNull(CatalogGarmentUpdatedEvent.parseFlexibleInstantOrNull("invalido"));
        assertNull(CatalogGarmentUpdatedEvent.parseFlexibleInstantOrNull("2026-99-99T99:99:99Z"));
        assertNotNull(CatalogGarmentUpdatedEvent.parseFlexibleInstantOrNull("2026-11-25T19:00:00Z"));

        UserSubscriptionPlanEvent emptyTimestamp = objectMapper.readValue("""
                {"usuarioId":"12","planId":2,"estado":"APPROVED","timestamp":""}
                """, UserSubscriptionPlanEvent.class);
        assertNull(emptyTimestamp.occurredAt(), "Timestamp vacío debe ser null para proteger cuota vigente");

        UserSubscriptionPlanEvent blankTimestamp = objectMapper.readValue("""
                {"usuarioId":"12","planId":2,"estado":"APPROVED","timestamp":"   "}
                """, UserSubscriptionPlanEvent.class);
        assertNull(blankTimestamp.occurredAt(), "Timestamp con espacios debe ser null para proteger cuota vigente");

        UserSubscriptionPlanEvent malformedTimestamp = objectMapper.readValue("""
                {"usuarioId":"12","planId":2,"estado":"APPROVED","timestamp":"fecha-invalida"}
                """, UserSubscriptionPlanEvent.class);
        assertNull(malformedTimestamp.occurredAt(), "Timestamp malformado debe ser null para proteger cuota vigente");
    }

    @Test
    @DisplayName("BUG-019: CatalogGarment.sanitizeImageUrl normaliza espacios a %20 y recorta extremos")
    void shouldSanitizeCatalogGarmentImageUrls() {
        assertEquals(
                "https://styleradar.blob.core.windows.net/catalog/camisa%20azul%20claro.png",
                edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment.sanitizeImageUrl(
                        "  https://styleradar.blob.core.windows.net/catalog/camisa azul claro.png  "
                )
        );
        assertNull(edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment.sanitizeImageUrl("   "));
        assertNull(edu.dosw.styleradar.virtualtryon.domain.model.CatalogGarment.sanitizeImageUrl(null));
    }
}
