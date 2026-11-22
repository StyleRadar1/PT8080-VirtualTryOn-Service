package edu.dosw.styleradar.virtualtryon.application.dto.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * DTO de evento asíncrono recibido desde RabbitMQ (routing keys:
 * {@code styleradar.pagos.pago.confirmado} y {@code styleradar.pagos.suscripcion.suspendida})
 * emitido por ms-pagos-suscripciones ante activación o suspensión de planes (SR-RF-24 / SR-RF-108).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserSubscriptionPlanEvent(
        UUID userId,
        String subscriberType,
        Long planId,
        String status,
        Instant occurredAt
) {

    @JsonCreator
    public static UserSubscriptionPlanEvent fromJson(
            @JsonProperty("data") Map<String, Object> data,
            @JsonProperty("userId") @JsonAlias({"usuarioId", "suscriptorId", "subscriberId"}) Object rawUserId,
            @JsonProperty("subscriberType") @JsonAlias({"tipoSuscriptor"}) String subscriberType,
            @JsonProperty("planId") @JsonAlias({"plan"}) Object rawPlanId,
            @JsonProperty("status") @JsonAlias({"estado"}) String status,
            @JsonProperty("timestamp") @JsonAlias({"occurredAt", "fechaEvento"}) Object rawTimestamp
    ) {
        Object resolvedUserId = firstNonNull(
                rawUserId,
                data != null ? firstPresent(data, "usuarioId", "userId", "suscriptorId", "subscriberId") : null
        );
        String resolvedSubscriberType = firstNonBlankString(
                subscriberType,
                data != null ? stringOrNull(firstPresent(data, "tipoSuscriptor", "subscriberType")) : null
        );
        Object resolvedPlan = firstNonNull(
                rawPlanId,
                data != null ? firstPresent(data, "planId", "plan") : null
        );
        String resolvedStatus = firstNonBlankString(
                status,
                data != null ? stringOrNull(firstPresent(data, "estado", "status")) : null
        );

        UUID parsedUserId = UserId.parseToUuid(resolvedUserId);
        Long parsedPlanId = parsePlanId(resolvedPlan);
        Instant parsedTimestamp = CatalogGarmentUpdatedEvent.parseFlexibleInstant(rawTimestamp);

        return new UserSubscriptionPlanEvent(
                parsedUserId,
                resolvedSubscriberType,
                parsedPlanId,
                resolvedStatus,
                parsedTimestamp
        );
    }

    public boolean isStoreSubscription() {
        if (subscriberType != null && !subscriberType.isBlank()) {
            String normalized = subscriberType.trim().toUpperCase(Locale.ROOT);
            if ("STORE".equals(normalized) || "ALMACEN".equals(normalized)) {
                return true;
            }
        }
        return planId != null && planId == 3L;
    }

    public boolean isApproved() {
        if (status == null || status.isBlank()) {
            return true;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "APPROVED".equals(normalized)
                || "APROBADO".equals(normalized)
                || "ACTIVE".equals(normalized)
                || "ACTIVA".equals(normalized);
    }

    private static Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !(value instanceof String s && s.isBlank())) {
                return value;
            }
        }
        return null;
    }

    private static Object firstNonNull(Object primary, Object fallback) {
        return primary != null ? primary : fallback;
    }

    private static String firstNonBlankString(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return (fallback != null && !fallback.isBlank()) ? fallback : null;
    }

    private static String stringOrNull(Object value) {
        return value != null ? value.toString() : null;
    }

    private static Long parsePlanId(Object rawPlan) {
        if (rawPlan == null) {
            return null;
        }
        if (rawPlan instanceof Number number) {
            return number.longValue();
        }
        String text = rawPlan.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ignored) {
            String normalized = text.toUpperCase(Locale.ROOT);
            if ("RADAR+".equals(normalized) || "RADAR_PLUS".equals(normalized)) {
                return 2L;
            }
            if ("STORE".equals(normalized) || "STORE BASIC".equals(normalized) || "ALMACEN".equals(normalized)) {
                return 3L;
            }
            return null;
        }
    }
}
