package dev.fhirvault.validation;

import dev.fhirvault.AbstractApiIT;
import dev.fhirvault.TestData;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationIT extends AbstractApiIT {

    @Test
    void createOfNonConformantPatientReturns422WithOperationOutcome() {
        Patient patient = TestData.patient("Lee", "Ann");
        patient.setGender(null);

        ResponseEntity<String> response = post("Patient", patient);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        OperationOutcome outcome = parse(OperationOutcome.class, response);
        assertThat(outcome.getIssue()).isNotEmpty()
                .allSatisfy(issue -> assertThat(issue.getSeverity()).isIn(
                        OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueSeverity.FATAL))
                .anySatisfy(issue -> assertThat(issue.getDiagnostics()).contains("gender"));
    }

    @Test
    void rejectedUpdateLeavesCurrentVersionUntouched() {
        Patient created = parse(Patient.class, post("Patient", TestData.patient("Lee", "Ann")));
        String id = created.getIdElement().getIdPart();
        Patient invalid = TestData.patient("Lee", "Ann");
        invalid.setId(id);
        invalid.getName().clear();

        ResponseEntity<String> response = put("Patient/" + id, invalid);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(get("Patient/" + id).getHeaders().getETag()).isEqualTo("W/\"1\"");
    }

    @Test
    void malformedJsonReturns400() {
        ResponseEntity<String> response = rest.exchange("/fhir/Patient", HttpMethod.POST,
                new HttpEntity<>("{\"resourceType\":\"Patient\",", jsonHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(FHIR_JSON);
        return headers;
    }
}
