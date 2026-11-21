package dev.fhirvault.validation;

import dev.fhirvault.AbstractApiIT;
import dev.fhirvault.TestData;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceIT extends AbstractApiIT {

    @Test
    void referenceToExistingPatientIsAccepted() {
        String patient = create(TestData.patient("Ray", "Jo"));

        assertThat(post("Observation", TestData.labObservation(patient, "2024-01-01")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void referenceToMissingResourceIsRejected() {
        ResponseEntity<String> response = post("Observation", TestData.labObservation("Patient/nope", "2024-01-01"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        OperationOutcome outcome = parse(OperationOutcome.class, response);
        assertThat(outcome.getIssueFirstRep().getDiagnostics()).contains("does not exist").contains("Patient/nope");
        assertThat(outcome.getIssueFirstRep().getExpression().get(0).getValue()).isEqualTo("Observation.subject");
    }

    @Test
    void referenceToDeletedResourceIsRejected() {
        String patient = create(TestData.patient("Ray", "Jo"));
        delete(patient);

        assertThat(post("Observation", TestData.labObservation(patient, "2024-01-01")).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void referenceToUnsupportedTypeIsRejected() {
        String patient = create(TestData.patient("Ray", "Jo"));
        Observation observation = TestData.labObservation(patient, "2024-01-01");
        observation.addPerformer(new Reference("Practitioner/123"));

        ResponseEntity<String> response = post("Observation", observation);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(parse(OperationOutcome.class, response).getIssueFirstRep().getDiagnostics())
                .contains("unsupported resource type Practitioner");
    }

    @Test
    void absoluteReferenceIsRejected() {
        ResponseEntity<String> response = post("Observation",
                TestData.labObservation("https://elsewhere.example/fhir/Patient/1", "2024-01-01"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void displayOnlyReferenceIsAllowed() {
        String patient = create(TestData.patient("Ray", "Jo"));
        Observation observation = TestData.labObservation(patient, "2024-01-01");
        observation.addPerformer(new Reference().setDisplay("Dr. Example"));

        assertThat(post("Observation", observation).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void deletingAReferencedPatientIsRejectedWith409() {
        String patient = create(TestData.patient("Ray", "Jo"));
        String observation = create(TestData.labObservation(patient, "2024-01-01"));

        ResponseEntity<String> response = delete(patient);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(parse(OperationOutcome.class, response).getIssueFirstRep().getDiagnostics()).contains(observation);
    }

    @Test
    void patientCanBeDeletedOnceReferrersAreGone() {
        String patient = create(TestData.patient("Ray", "Jo"));
        String observation = create(TestData.labObservation(patient, "2024-01-01"));
        delete(observation);

        assertThat(delete(patient).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void updateThatDropsReferenceReleasesTarget() {
        String patientA = create(TestData.patient("Ray", "Jo"));
        String patientB = create(TestData.patient("Ray", "Al"));
        String observation = create(TestData.labObservation(patientA, "2024-01-01"));
        Observation moved = TestData.labObservation(patientB, "2024-01-01");
        moved.setId(new IdType(observation).getIdPart());

        assertThat(put(observation, moved).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(delete(patientA).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(delete(patientB).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void everyClinicalTypeSupportsCrud() {
        String patient = create(TestData.patient("Ray", "Jo"));
        for (Resource resource : new Resource[] {
                TestData.encounter(patient, "2024-01-01T09:00:00Z"),
                TestData.condition(patient),
                TestData.medicationRequest(patient),
                TestData.allergy(patient)}) {
            String ref = create(resource);
            assertThat(get(ref).getStatusCode()).as(ref).isEqualTo(HttpStatus.OK);
            assertThat(delete(ref).getStatusCode().is2xxSuccessful()).as(ref).isTrue();
        }
    }

    private String create(Resource resource) {
        ResponseEntity<String> response = post(resource.fhirType(), resource);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
        return new IdType(response.getHeaders().getLocation().toString()).toUnqualifiedVersionless().getValue();
    }
}
