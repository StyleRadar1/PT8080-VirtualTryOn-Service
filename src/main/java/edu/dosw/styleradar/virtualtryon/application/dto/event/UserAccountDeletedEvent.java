package edu.dosw.styleradar.virtualtryon.application.dto.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio entrante publicado cuando un usuario elimina su cuenta de StyleRadar
 * (styleradar.usuarios.cuenta.eliminada), disparando la purga definitiva de datos biométricos (Ley 1581 · SR-RF-27).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserAccountDeletedEvent(
        UUID userId,
        Instant deletedAt
) {

    @JsonCreator
    public static UserAccountDeletedEvent fromJson(
            @JsonProperty("userId") @JsonAlias({"usuarioId", "id", "userId"}) Object rawUserId,
            @JsonProperty("deletedAt") @JsonAlias({"fechaEliminacion", "fechaEvento", "timestamp"}) Instant deletedAt
    ) {
        UUID parsedUserId = UserId.parseToUuid(rawUserId);
        Instant parsedDeletedAt = deletedAt != null ? deletedAt : Instant.now();
        return new UserAccountDeletedEvent(parsedUserId, parsedDeletedAt);
    }
}

