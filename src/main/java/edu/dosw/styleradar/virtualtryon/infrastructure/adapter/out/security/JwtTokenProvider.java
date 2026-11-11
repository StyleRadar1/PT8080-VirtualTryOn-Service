package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Proveedor de tokens JWT (HS256) encargado de firmar, validar y extraer la identidad
 * y rol del usuario autenticado para proteger los endpoints biométricos (SR-RF-23).
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration duration;
    private final Clock clock;
    private final JwtParser parser;

    @Autowired
    public JwtTokenProvider(
            @Value("${styleradar.security.jwt.secret-base64}") String secretBase64,
            @Value("${styleradar.security.jwt.duration:PT60M}") Duration duration
    ) {
        this(secretBase64, duration, Clock.systemUTC());
    }

    public JwtTokenProvider(String secretBase64, Duration duration, Clock clock) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalStateException("JWT_SECRET_BASE64 es requerida y debe configurarse mediante variables de entorno");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secretBase64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SECRET_BASE64 debe contener una clave Base64 válida", ex);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET_BASE64 es requerida y debe contener al menos 256 bits");
        }
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalStateException("La duración JWT debe ser positiva");
        }
        this.key = new SecretKeySpec(decoded, "HmacSHA256");
        this.duration = duration;
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(this.key)
                .clock(() -> Date.from(this.clock.instant()))
                .build();
    }

    /**
     * Genera un token JWT firmado con HS256 conteniendo el UUID del usuario y su rol.
     */
    public String generateToken(UUID userId, String role) {
        if (userId == null) {
            throw new IllegalArgumentException("El identificador de usuario no puede ser nulo");
        }
        String resolvedRole = (role == null || role.isBlank()) ? "COMPRADOR" : role.trim().toUpperCase();
        var now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", resolvedRole)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(duration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida la firma y vigencia del token JWT y extrae el principal AuthenticatedUser.
     */
    public AuthenticatedUser validateAndExtractUser(String token) {
        Jws<Claims> jws = parser.parseSignedClaims(token);
        if (!Jwts.SIG.HS256.getId().equals(jws.getHeader().getAlgorithm())) {
            throw new JwtException("Algoritmo de firma JWT no permitido: se requiere HS256");
        }
        Claims claims = jws.getPayload();
        Date now = Date.from(clock.instant());
        if (claims.getExpiration() == null || claims.getIssuedAt() == null
                || !claims.getExpiration().after(now)
                || claims.getIssuedAt().after(now)) {
            throw new JwtException("Fechas de vigencia del token JWT inválidas");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtException("Subject del token JWT ausente");
        }

        UUID userId = parseUserId(subject.trim());
        String roleClaim = claims.get("role", String.class);
        String resolvedRole = (roleClaim == null || roleClaim.isBlank()) ? "COMPRADOR" : roleClaim;
        return new AuthenticatedUser(userId, resolvedRole);
    }

    private static UUID parseUserId(String rawSubject) {
        try {
            return UUID.fromString(rawSubject);
        } catch (IllegalArgumentException ex) {
            if (rawSubject.matches("[1-9][0-9]*")) {
                return UUID.nameUUIDFromBytes(("styleradar-user-" + rawSubject).getBytes(StandardCharsets.UTF_8));
            }
            throw new JwtException("Identificador de usuario en subject JWT inválido", ex);
        }
    }
}

