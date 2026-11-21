package dev.fhirvault.audit;

import dev.fhirvault.AbstractApiIT;
import dev.fhirvault.TestData;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditIT extends AbstractApiIT {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void readIsAuditedAgainstThePatient() {
        String patient = create(TestData.patient("Audit", "Read"));
        String patientId = new IdType(patient).getIdPart();
        HttpHeaders headers = new HttpHeaders();
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        headers.set("X-Request-ID", requestId);

        exchange(HttpMethod.GET, patient, null, headers);

        List<AuditEvent> events = auditFor(patientId, "read");
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getAction()).isEqualTo(AuditEvent.AuditEventAction.R);
        assertThat(event.getOutcome()).isEqualTo(AuditEvent.AuditEventOutcome._0);
        assertThat(event.getAgentFirstRep().getWho().getIdentifier().getValue()).isEqualTo("anonymous");
        assertThat(event.getEntity()).filteredOn(e -> e.getWhat().hasIdentifier()).singleElement()
                .satisfies(e -> assertThat(e.getWhat().getIdentifier().getValue()).isEqualTo(requestId));
    }

    @Test
    void createAndSearchAreAuditedPerPatient() {
        String patientA = create(TestData.patient("Audit", "A"));
        String patientB = create(TestData.patient("Audit", "B"));
        String obsA = create(TestData.labObservation(patientA, "2024-01-01"));
        create(TestData.labObservation(patientB, "2024-01-01"));

        get("Observation?patient=" + new IdType(patientA).getIdPart() + "," + new IdType(patientB).getIdPart());

        assertThat(auditFor(new IdType(patientA).getIdPart(), "create")).hasSize(2); // the patient and its observation
        // Reading the audit log is audited too, so keep only the Observation search.
        List<AuditEvent> searchA = observationSearches(auditFor(new IdType(patientA).getIdPart(), "search-type"));
        assertThat(searchA).hasSize(1);
        assertThat(searchA.get(0).getEntity()).anySatisfy(e ->
                assertThat(e.getWhat().getReference()).startsWith(obsA));
        assertThat(observationSearches(auditFor(new IdType(patientB).getIdPart(), "search-type"))).hasSize(1);
    }

    @Test
    void deleteIsAuditedAgainstThePreviousVersionsPatient() {
        String patient = create(TestData.patient("Audit", "Del"));
        String observation = create(TestData.labObservation(patient, "2024-01-01"));

        delete(observation);

        List<AuditEvent> deletes = auditFor(new IdType(patient).getIdPart(), "delete");
        assertThat(deletes).hasSize(1);
        assertThat(deletes.get(0).getAction()).isEqualTo(AuditEvent.AuditEventAction.D);
    }

    @Test
    void failedRequestIsAuditedAsFailure() {
        String missing = "missing-" + UUID.randomUUID().toString().substring(0, 8);

        get("Patient/" + missing);

        List<AuditEvent> events = auditFor(missing, "read");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditEvent.AuditEventOutcome._4);
        assertThat(events.get(0).getOutcomeDesc()).startsWith("404");
    }

    @Test
    void failedRequestOnAPatientsResourceIsLinkedToThatPatient() {
        String patient = create(TestData.patient("Audit", "Fail"));
        String observation = create(TestData.labObservation(patient, "2024-01-01"));

        delete(patient); // 409: the observation still references it

        List<AuditEvent> failures = auditFor(new IdType(patient).getIdPart(), "delete");
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).getOutcomeDesc()).startsWith("409");
        assertThat(get(observation + "/_history/99").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(auditFor(new IdType(patient).getIdPart(), "vread"))
                .singleElement().satisfies(e -> assertThat(e.getOutcome()).isEqualTo(AuditEvent.AuditEventOutcome._4));
    }

    @Test
    void overlongIdInUrlIsStillAudited() {
        String id = "x".repeat(64);

        get("Patient/" + id + "y");

        assertThat(get("AuditEvent?patient=" + id).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parse(Bundle.class, get("AuditEvent?patient=" + id)).getTotal()).isPositive();
    }

    @Test
    void transactionIsAuditedAsExecute() {
        String family = "Tx" + UUID.randomUUID().toString().substring(0, 8);
        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry().setFullUrl("urn:uuid:" + UUID.randomUUID()).setResource(TestData.patient(family, "Jo"))
                .getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Patient");

        Bundle response = parse(Bundle.class, post("", bundle));

        String patientId = new IdType(response.getEntryFirstRep().getResponse().getLocation()).getIdPart();
        List<AuditEvent> events = auditFor(patientId, "transaction");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAction()).isEqualTo(AuditEvent.AuditEventAction.E);
    }

    @Test
    void auditEventsAreReadableButNotWritableAndImmutableInTheDatabase() {
        String patient = create(TestData.patient("Audit", "Imm"));
        AuditEvent event = auditFor(new IdType(patient).getIdPart(), "create").get(0);

        assertThat(get("AuditEvent/" + event.getIdElement().getIdPart()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("AuditEvent", new AuditEvent()).getStatusCode().is4xxClientError()).isTrue();
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_event WHERE id = :id")
                .param("id", event.getIdElement().getIdPart()).update())
                .hasMessageContaining("append-only");
    }

    private static List<AuditEvent> observationSearches(List<AuditEvent> events) {
        return events.stream()
                .filter(e -> e.getEntity().stream().anyMatch(en -> en.getWhat().hasReference()
                        && en.getWhat().getReference().startsWith("Observation/")))
                .toList();
    }

    private List<AuditEvent> auditFor(String patientId, String interaction) {
        ResponseEntity<String> response = get("AuditEvent?patient=" + patientId + "&_count=100");
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return parse(Bundle.class, response).getEntry().stream()
                .map(e -> (AuditEvent) e.getResource())
                .filter(e -> interaction.equals(e.getSubtypeFirstRep().getCode()))
                .toList();
    }

    private String create(Resource resource) {
        ResponseEntity<String> response = post(resource.fhirType(), resource);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
        return new IdType(response.getHeaders().getLocation().toString()).toUnqualifiedVersionless().getValue();
    }
}
