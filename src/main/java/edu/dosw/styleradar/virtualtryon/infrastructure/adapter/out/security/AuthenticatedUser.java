package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.security;

import java.util.Objects;
import java.util.UUID;

/**
 * Principal de seguridad inmutable que representa al usuario autenticado mediante Bearer JWT
 * dentro del microservicio de Probador Virtual (SR-RF-23).
 *
 * @param userId Identificador único del usuario autenticado
 * @param role   Rol principal del usuario (ej. COMPRADOR, USER, ADMIN, ADMIN_STYLERADAR)
 */
public record AuthenticatedUser(UUID userId, String role) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "El userId del usuario autenticado no puede ser nulo");
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("El rol del usuario autenticado no puede ser vacío");
        }
        role = role.trim().toUpperCase();
        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }
    }

    /**
     * Verifica si el usuario autenticado es propietario del recurso biométrico solicitado
     * o cuenta con privilegios administrativos de plataforma.
     */
    public boolean canAccessUser(UUID targetUserId) {
        if (targetUserId == null) {
            return false;
        }
        if ("ADMIN".equals(role) || "ADMIN_STYLERADAR".equals(role)) {
            return true;
        }
        return userId.equals(targetUserId);
    }
}

