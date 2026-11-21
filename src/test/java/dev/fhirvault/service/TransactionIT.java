package dev.fhirvault.service;

import dev.fhirvault.AbstractApiIT;
import dev.fhirvault.TestData;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionIT extends AbstractApiIT {

    @Test
    void createsPatientAndObservationWithUrnReferenceResolved() {
        String family = unique("Tx");
        String patientUrn = "urn:uuid:" + UUID.randomUUID();
        Bundle bundle = transaction();
        add(bundle, patientUrn, HTTPVerb.POST, "Patient", TestData.patient(family, "Jo"));
        // The observation comes first in the bundle and still resolves the patient's urn.
        Bundle.BundleEntryComponent obsEntry = bundle.getEntry().get(0).copy();
        bundle.getEntry().add(0, obsEntry);
        bundle.getEntry().get(0).setFullUrl("urn:uuid:" + UUID.randomUUID())
                .setResource(TestData.labObservation(patientUrn, "2024-02-02"))
                .getRequest().setMethod(HTTPVerb.POST).setUrl("Observation");

        ResponseEntity<String> response = post("", bundle);

        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        Bundle result = parse(Bundle.class, response);
        assertThat(result.getType()).isEqualTo(Bundle.BundleType.TRANSACTIONRESPONSE);
        assertThat(result.getEntry()).extracting(e -> e.getResponse().getStatus()).containsExactly("201 Created", "201 Created");
        String observationLocation = result.getEntry().get(0).getResponse().getLocation();
        String patientLocation = result.getEntry().get(1).getResponse().getLocation();
        assertThat(observationLocation).startsWith("Observation/").endsWith("/_history/1");

        Observation stored = parse(Observation.class, get(new IdType(observationLocation).toVersionless().getValue()));
        assertThat(stored.getSubject().getReference()).isEqualTo(new IdType(patientLocation).toUnqualifiedVersionless().getValue());
    }

    @Test
    void anyFailingEntryRollsBackTheWholeBundle() {
        String family = unique("Rollback");
        Bundle bundle = transaction();
        add(bundle, "urn:uuid:" + UUID.randomUUID(), HTTPVerb.POST, "Patient", TestData.patient(family, "Ok"));
        Patient invalid = TestData.patient(family, "Bad");
        invalid.setGender(null);
        add(bundle, "urn:uuid:" + UUID.randomUUID(), HTTPVerb.POST, "Patient", invalid);

        ResponseEntity<String> response = post("", bundle);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        OperationOutcome outcome = parse(OperationOutcome.class, response);
        assertThat(outcome.getIssueFirstRep().getDiagnostics()).startsWith("Bundle.entry[1]");
        assertThat(parse(Bundle.class, get("Patient?family=" + family)).getTotal()).isZero();
    }

    @Test
    void putWithClientIdCreatesThenUpdates() {
        String id = "tx-" + UUID.randomUUID();
        Bundle first = transaction();
        add(first, null, HTTPVerb.PUT, "Patient/" + id, withId(TestData.patient(unique("Put"), "Jo"), id));
        Bundle second = transaction();
        add(second, null, HTTPVerb.PUT, "Patient/" + id, withId(TestData.patient(unique("Put"), "Jo"), id));

        Bundle created = parse(Bundle.class, post("", first));
        Bundle updated = parse(Bundle.class, post("", second));

        assertThat(created.getEntryFirstRep().getResponse().getStatus()).isEqualTo("201 Created");
        assertThat(updated.getEntryFirstRep().getResponse().getStatus()).isEqualTo("200 OK");
        assertThat(updated.getEntryFirstRep().getResponse().getEtag()).isEqualTo("W/\"2\"");
    }

    @Test
    void deleteOfPatientAndItsObservationTogetherSucceeds() {
        String patient = create(TestData.patient(unique("Del"), "Jo"));
        String observation = create(TestData.labObservation(patient, "2024-01-01"));
        Bundle bundle = transaction();
        add(bundle, null, HTTPVerb.DELETE, patient, null);
        add(bundle, null, HTTPVerb.DELETE, observation, null);

        ResponseEntity<String> response = post("", bundle);

        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(get(patient).getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    void readEntriesReturnResources() {
        String patient = create(TestData.patient(unique("Read"), "Jo"));
        Bundle bundle = transaction();
        add(bundle, null, HTTPVerb.GET, patient, null);
        add(bundle, null, HTTPVerb.GET, patient + "/_history/1", null);

        Bundle result = parse(Bundle.class, post("", bundle));

        assertThat(result.getEntry()).allSatisfy(e -> {
            assertThat(e.getResponse().getStatus()).isEqualTo("200 OK");
            assertThat(e.getResource()).isInstanceOf(Patient.class);
        });
    }

    @Test
    void staleIfMatchFailsWith412() {
        String patient = create(TestData.patient(unique("Match"), "Jo"));
        String id = new IdType(patient).getIdPart();
        Bundle bundle = transaction();
        add(bundle, null, HTTPVerb.PUT, patient, withId(TestData.patient(unique("Match"), "Jo"), id))
                .getRequest().setIfMatch("W/\"7\"");

        assertThat(post("", bundle).getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
    }

    @Test
    void unsupportedShapesAreRejectedWith400() {
        Bundle batch = new Bundle().setType(Bundle.BundleType.BATCH);
        Bundle conditional = transaction();
        add(conditional, null, HTTPVerb.POST, "Patient", TestData.patient("C", "D")).getRequest().setIfNoneExist("identifier=x|1");
        Bundle duplicate = transaction();
        add(duplicate, null, HTTPVerb.PUT, "Patient/dup", withId(TestData.patient("A", "B"), "dup"));
        add(duplicate, null, HTTPVerb.PUT, "Patient/dup", withId(TestData.patient("A", "B"), "dup"));
        Bundle searchEntry = transaction();
        add(searchEntry, null, HTTPVerb.GET, "Patient?name=x", null);
        Bundle unsupportedType = transaction();
        add(unsupportedType, null, HTTPVerb.DELETE, "Practitioner/1", null);

        for (Bundle bad : new Bundle[] {batch, conditional, duplicate, searchEntry, unsupportedType}) {
            assertThat(post("", bad).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void unresolvedUrnReferenceIsRejected() {
        Bundle bundle = transaction();
        add(bundle, "urn:uuid:" + UUID.randomUUID(), HTTPVerb.POST, "Observation",
                TestData.labObservation("urn:uuid:" + UUID.randomUUID(), "2024-01-01"));

        assertThat(post("", bundle).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static Bundle transaction() {
        return new Bundle().setType(Bundle.BundleType.TRANSACTION);
    }

    private static Bundle.BundleEntryComponent add(Bundle bundle, String fullUrl, HTTPVerb method, String url,
                                                   Resource resource) {
        Bundle.BundleEntryComponent entry = bundle.addEntry().setFullUrl(fullUrl).setResource(resource);
        entry.getRequest().setMethod(method).setUrl(url);
        return entry;
    }

    private static Patient withId(Patient patient, String id) {
        patient.setId(id);
        return patient;
    }

    private String create(Resource resource) {
        ResponseEntity<String> response = post(resource.fhirType(), resource);
        return new IdType(response.getHeaders().getLocation().toString()).toUnqualifiedVersionless().getValue();
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
