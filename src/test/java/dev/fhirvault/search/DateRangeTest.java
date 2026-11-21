package dev.fhirvault.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    @ParameterizedTest
    @CsvSource({
            "2024,                          2024-01-01T00:00:00Z,     2025-01-01T00:00:00Z",
            "2024-02,                       2024-02-01T00:00:00Z,     2024-03-01T00:00:00Z",
            "2024-02-29,                    2024-02-29T00:00:00Z,     2024-03-01T00:00:00Z",
            "2024-02-29T10:15:30Z,          2024-02-29T10:15:30Z,     2024-02-29T10:15:31Z",
            "2024-02-29T10:15:30.250+02:00, 2024-02-29T08:15:30.250Z, 2024-02-29T08:15:30.251Z",
            "2024-02-29T10:15,              2024-02-29T10:15:00Z,     2024-02-29T10:16:00Z"
    })
    void rangeCoversThePrecisionGiven(String value, String low, String high) {
        DateRange range = DateRange.parse(value);

        assertThat(range.low()).isEqualTo(Instant.parse(low));
        assertThat(range.high()).isEqualTo(Instant.parse(high));
    }

    @Test
    void periodUsesStartOfStartAndEndOfEnd() {
        DateRange range = DateRange.between(DateRange.parse("2024-01-05"), DateRange.parse("2024-01-07"));

        assertThat(range.low()).isEqualTo(Instant.parse("2024-01-05T00:00:00Z"));
        assertThat(range.high()).isEqualTo(Instant.parse("2024-01-08T00:00:00Z"));
    }

    @Test
    void openEndedPeriodHasNullBound() {
        assertThat(DateRange.between(DateRange.parse("2024-01-05"), null).high()).isNull();
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> DateRange.parse("yesterday")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DateRange.parse("2024-13-01")).isInstanceOf(IllegalArgumentException.class);
    }
}
