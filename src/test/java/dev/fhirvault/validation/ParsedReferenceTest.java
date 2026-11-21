package dev.fhirvault.validation;

import dev.fhirvault.validation.ParsedReference.Kind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ParsedReferenceTest {

    @ParameterizedTest
    @CsvSource({
            "Patient/123,                          RELATIVE,    Patient, 123",
            "Patient/a-b.c/_history/2,             RELATIVE,    Patient, a-b.c",
            "https://other.example/fhir/Patient/9, ABSOLUTE,    Patient, 9",
            "#med1,                                CONTAINED,   ,        ",
            "urn:uuid:5d3c2b2a-0000-4000-8000-000000000001, URN, ,      ",
            "Patient?identifier=x|1,               CONDITIONAL, ,        ",
            "patient/123,                          INVALID,     ,        ",
            "Patient/,                             INVALID,     ,        "
    })
    void classifiesReferenceStrings(String input, Kind kind, String type, String id) {
        ParsedReference parsed = ParsedReference.parse(input);

        assertThat(parsed.kind()).isEqualTo(kind);
        assertThat(parsed.type()).isEqualTo(type);
        assertThat(parsed.id()).isEqualTo(id);
    }
}
