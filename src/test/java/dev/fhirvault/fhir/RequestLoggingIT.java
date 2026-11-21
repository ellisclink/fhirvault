package dev.fhirvault.fhir;

import dev.fhirvault.AbstractApiIT;
import dev.fhirvault.TestData;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/** Logs may hold resource type, id and request id only, never bodies or patient identifiers. */
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingIT extends AbstractApiIT {

    @Test
    void logsOneSummaryLineWithoutIdentifiersOrQueryValues(CapturedOutput output) {
        Patient patient = TestData.patient("Loggable", "Secretname");
        patient.getIdentifierFirstRep().setValue("MRN-SECRET-123");
        post("Patient", patient);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-ID", "log-test-1");

        exchange(HttpMethod.GET, "Patient?identifier=MRN-SECRET-123&birthdate=notadate", null, headers);

        assertThat(output.getOut()).contains("request=log-test-1 op=search-type target=Patient status=400");
        assertThat(output.getOut()).doesNotContain("MRN-SECRET-123").doesNotContain("Secretname").doesNotContain("notadate");
    }
}
