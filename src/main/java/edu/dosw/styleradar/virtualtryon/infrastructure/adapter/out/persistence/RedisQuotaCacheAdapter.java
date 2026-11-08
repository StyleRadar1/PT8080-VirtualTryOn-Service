package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.out.persistence;

import edu.dosw.styleradar.virtualtryon.domain.port.out.UserQuotaCachePort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.BillingPeriod;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.OptionalInt;

/**
 * Adaptador de salida hacia Redis (probador-redis) que implementa UserQuotaCachePort
 * usando StringRedisTemplate para lectura, escritura e incremento atómico de cuotas mensuales (SR-RF-24).
 */
@Component
public class RedisQuotaCacheAdapter implements UserQuotaCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisQuotaCacheAdapter.class);
    private static final String QUOTA_KEY_PREFIX = "tryon:quota:";
    private static final Duration QUOTA_TTL = Duration.ofDays(35);

    private final StringRedisTemplate redisTemplate;

    public RedisQuotaCacheAdapter(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    @Override
    public OptionalInt getCachedUsedCount(UserId userId, BillingPeriod billingPeriod) {
        if (redisTemplate == null) {
            return OptionalInt.empty();
        }
        String key = buildKey(userId, billingPeriod);
        try {
            String rawValue = redisTemplate.opsForValue().get(key);
            if (rawValue == null || rawValue.isBlank()) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(Integer.parseInt(rawValue.trim()));
        } catch (RuntimeException ex) {
            log.warn("No fue posible consultar cuota en Redis para la clave {}: {}", key, ex.getMessage());
            return OptionalInt.empty();
        }
    }

    @Override
    public void cacheUsedCount(UserId userId, BillingPeriod billingPeriod, int usedCount) {
        if (redisTemplate == null) {
            return;
        }
        String key = buildKey(userId, billingPeriod);
        try {
            redisTemplate.opsForValue().set(key, Integer.toString(Math.max(0, usedCount)), QUOTA_TTL);
        } catch (RuntimeException ex) {
            log.warn("No fue posible escribir cuota en Redis para la clave {}: {}", key, ex.getMessage());
        }
    }

    @Override
    public int incrementUsedCount(UserId userId, BillingPeriod billingPeriod) {
        if (redisTemplate == null) {
            return 0;
        }
        String key = buildKey(userId, billingPeriod);
        try {
            Long updated = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, QUOTA_TTL);
            return updated == null ? 1 : updated.intValue();
        } catch (RuntimeException ex) {
            log.warn("No fue posible incrementar cuota en Redis para la clave {}: {}", key, ex.getMessage());
            return 0;
        }
    }

    @Override
    public void evictQuota(UserId userId, BillingPeriod billingPeriod) {
        if (redisTemplate == null) {
            return;
        }
        String key = buildKey(userId, billingPeriod);
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("No fue posible eliminar clave de cuota en Redis {}: {}", key, ex.getMessage());
        }
    }

    static String buildKey(UserId userId, BillingPeriod billingPeriod) {
        return QUOTA_KEY_PREFIX + userId.value() + ":" + billingPeriod.getValue();
    }
}

