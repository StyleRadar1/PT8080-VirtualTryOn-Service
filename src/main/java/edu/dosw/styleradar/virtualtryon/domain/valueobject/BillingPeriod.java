package edu.dosw.styleradar.virtualtryon.domain.valueobject;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object inmutable que representa el período mensual de facturación y cuotas en formato YYYY-MM (SR-RF-24).
 */
public record BillingPeriod(String yearMonth) {

    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    public BillingPeriod {
        Objects.requireNonNull(yearMonth, "El período de facturación (BillingPeriod) no puede ser nulo");
        if (!YEAR_MONTH_PATTERN.matcher(yearMonth).matches()) {
            throw new IllegalArgumentException(
                    "El período de facturación debe tener el formato YYYY-MM: " + yearMonth
            );
        }
    }

    public static BillingPeriod of(String yearMonth) {
        return new BillingPeriod(yearMonth);
    }

    public static BillingPeriod current() {
        return new BillingPeriod(YearMonth.now(ZoneOffset.UTC).toString());
    }

    /**
     * Calcula el período mensual UTC correspondiente a un instante histórico dado (por ejemplo,
     * la fecha de creación de un trabajo al reembolsar cuota mensual tras fallo definitivo).
     */
    public static BillingPeriod fromInstant(java.time.Instant instant) {
        java.util.Objects.requireNonNull(instant, "El instante para calcular el período de facturación no puede ser nulo");
        return new BillingPeriod(YearMonth.from(instant.atOffset(ZoneOffset.UTC)).toString());
    }

    public String getValue() {
        return yearMonth;
    }
}

