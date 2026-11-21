package dev.fhirvault.fhir;

import dev.fhirvault.AbstractApiIT;
import dev.fhirvault.TestData;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PatientCrudIT extends AbstractApiIT {

    @Test
    void createReturns201WithLocationEtagAndBody() {
        ResponseEntity<String> response = post("Patient", TestData.patient("Chalmers", "Peter"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).matches(".*/fhir/Patient/[0-9a-f-]{36}/_history/1");
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"1\"");
        Patient created = parse(Patient.class, response);
        assertThat(created.getMeta().getVersionId()).isEqualTo("1");
        assertThat(created.getMeta().getLastUpdated()).isNotNull();
    }

    @Test
    void createIgnoresClientSuppliedId() {
        Patient patient = TestData.patient("Chalmers", "Peter");
        patient.setId("client-chosen");

        Patient created = parse(Patient.class, post("Patient", patient));

        assertThat(created.getIdElement().getIdPart()).isNotEqualTo("client-chosen");
    }

    @Test
    void readReturnsCurrentVersionWithHeaders() {
        String id = createPatient();

        ResponseEntity<String> response = get("Patient/" + id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"1\"");
        assertThat(response.getHeaders().getLastModified()).isPositive();
        assertThat(parse(Patient.class, response).getNameFirstRep().getFamily()).isEqualTo("Chalmers");
    }

    @Test
    void readUnknownIdReturns404WithOperationOutcome() {
        ResponseEntity<String> response = get("Patient/does-not-exist");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(parse(OperationOutcome.class, response).getIssueFirstRep().getDiagnostics()).contains("does-not-exist");
    }

    @Test
    void updateCreatesNewVersion() {
        String id = createPatient();
        Patient changed = TestData.patient("Chalmers", "Pete");
        changed.setId(id);

        ResponseEntity<String> response = put("Patient/" + id, changed);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"2\"");
        assertThat(parse(Patient.class, get("Patient/" + id)).getNameFirstRep().getGivenAsSingleString()).isEqualTo("Pete");
    }

    @Test
    void updateWithMatchingIfMatchSucceeds() {
        String id = createPatient();
        Patient changed = TestData.patient("Chalmers", "Pete");
        changed.setId(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setIfMatch("W/\"1\"");

        ResponseEntity<String> response = exchange(HttpMethod.PUT, "Patient/" + id, changed, headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateWithStaleIfMatchReturns412() {
        String id = createPatient();
        Patient changed = TestData.patient("Chalmers", "Pete");
        changed.setId(id);
        put("Patient/" + id, changed);
        HttpHeaders headers = new HttpHeaders();
        headers.setIfMatch("W/\"1\"");

        ResponseEntity<String> response = exchange(HttpMethod.PUT, "Patient/" + id, changed, headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
    }

    @Test
    void updateWithIdMismatchReturns400() {
        String id = createPatient();
        Patient changed = TestData.patient("Chalmers", "Pete");
        changed.setId("other-id");

        assertThat(put("Patient/" + id, changed).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateOfUnknownIdCreatesIt() {
        Patient patient = TestData.patient("Chalmers", "Peter");
        patient.setId("client-id-1");

        ResponseEntity<String> response = put("Patient/client-id-1", patient);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(get("Patient/client-id-1").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteMakesReadReturn410ButOldVersionStaysReadable() {
        String id = createPatient();

        ResponseEntity<String> deleted = delete("Patient/" + id);

        assertThat(deleted.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(get("Patient/" + id).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(get("Patient/" + id + "/_history/1").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("Patient/" + id + "/_history/2").getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    void deleteIsIdempotent() {
        String id = createPatient();
        delete("Patient/" + id);

        assertThat(delete("Patient/" + id).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void deleteUnknownReturns404() {
        assertThat(delete("Patient/never-existed").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateAfterDeleteRecreatesWithNextVersion() {
        String id = createPatient();
        delete("Patient/" + id);
        Patient again = TestData.patient("Chalmers", "Peter");
        again.setId(id);

        ResponseEntity<String> response = put("Patient/" + id, again);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"3\"");
    }

    @Test
    void vreadReturnsRequestedVersion() {
        String id = createPatient();
        Patient changed = TestData.patient("Chalmers", "Pete");
        changed.setId(id);
        put("Patient/" + id, changed);

        Patient v1 = parse(Patient.class, get("Patient/" + id + "/_history/1"));

        assertThat(v1.getNameFirstRep().getGivenAsSingleString()).isEqualTo("Peter");
        assertThat(v1.getMeta().getVersionId()).isEqualTo("1");
    }

    private String createPatient() {
        ResponseEntity<String> response = post("Patient", TestData.patient("Chalmers", "Peter"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new IdType(response.getHeaders().getLocation().toString()).getIdPart();
    }
}
