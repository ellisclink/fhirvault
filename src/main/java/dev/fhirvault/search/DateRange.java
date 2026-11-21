package dev.fhirvault.search;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/**
 * A FHIR date, dateTime or instant as the half-open interval it covers: "2024" is all of 2024,
 * "2024-03-05T10:15:00Z" is that second. A null bound is open. Values without a zone are read as UTC.
 */
public record DateRange(Instant low, Instant high) {

    private static final Pattern YEAR = Pattern.compile("\\d{4}");
    private static final Pattern YEAR_MONTH = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern DATE_TIME_NO_ZONE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?");

    public static DateRange parse(String value) {
        try {
            if (YEAR.matcher(value).matches()) {
                Instant low = LocalDate.of(Integer.parseInt(value), 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
                return new DateRange(low, low.atZone(ZoneOffset.UTC).plusYears(1).toInstant());
            }
            if (YEAR_MONTH.matcher(value).matches()) {
                YearMonth month = YearMonth.parse(value);
                return new DateRange(month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                        month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
            }
            if (DATE.matcher(value).matches()) {
                LocalDate day = LocalDate.parse(value);
                return new DateRange(day.atStartOfDay(ZoneOffset.UTC).toInstant(),
                        day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
            }
            String withZone = DATE_TIME_NO_ZONE.matcher(value).matches() ? value + "Z" : value;
            Instant low = OffsetDateTime.parse(withZone).toInstant();
            return new DateRange(low, low.plus(1, precisionOf(value)));
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new IllegalArgumentException("Not a valid FHIR date/dateTime: " + value, e);
        }
    }

    public static DateRange between(DateRange start, DateRange end) {
        return new DateRange(start == null ? null : start.low(), end == null ? null : end.high());
    }

    /** The smallest unit present in the time part: minutes, seconds or milliseconds. */
    private static ChronoUnit precisionOf(String value) {
        String time = value.substring(value.indexOf('T') + 1);
        if (time.length() >= 9 && time.charAt(8) == '.') {
            return ChronoUnit.MILLIS;
        }
        return time.length() >= 6 && time.charAt(5) == ':' ? ChronoUnit.SECONDS : ChronoUnit.MINUTES;
    }
}
