package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Principal de seguridad inmutable que representa al usuario autenticado mediante Bearer JWT
 * dentro del microservicio de Probador Virtual (SR-RF-23).
 *
 * @param userId Identificador único del usuario autenticado
 * @param role   Rol principal del usuario (ej. COMPRADOR, USER, ADMIN, ADMIN_STYLERADAR)
 * @param roles  Lista inmutable de roles normalizados extraídos del JWT
 */
public record AuthenticatedUser(UUID userId, String role, List<String> roles) {

    public AuthenticatedUser(UUID userId, String role) {
        this(userId, role, role == null ? List.of() : List.of(role));
    }

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "El userId del usuario autenticado no puede ser nulo");
        List<String> sourceRoles = (roles == null || roles.isEmpty())
                ? (role == null ? List.of() : List.of(role))
                : roles;
        List<String> normalizedRoles = sourceRoles.stream()
                .map(AuthenticatedUser::normalizeRole)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedRoles.isEmpty()) {
            throw new IllegalArgumentException("El rol del usuario autenticado no puede ser vacío");
        }
        String primaryRole = normalizeRole(role);
        role = primaryRole != null ? primaryRole : normalizedRoles.get(0);
        roles = normalizedRoles;
    }

    private static String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return null;
        }
        String cleaned = rawRole.trim().toUpperCase();
        return cleaned.startsWith("ROLE_") ? cleaned.substring(5) : cleaned;
    }

    /**
     * Verifica si el usuario autenticado es propietario del recurso biométrico solicitado
     * o cuenta con privilegios administrativos de plataforma.
     */
    public boolean canAccessUser(UUID targetUserId) {
        if (targetUserId == null) {
            return false;
        }
        if (roles.contains("ADMIN") || roles.contains("ADMIN_STYLERADAR")) {
            return true;
        }
        return userId.equals(targetUserId);
    }
}

