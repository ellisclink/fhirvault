package dev.fhirvault.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.ValidationResult;
import dev.fhirvault.TestData;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses the real US Core package, built once because loading takes seconds. */
class ResourceValidatorTest {

    private static ResourceValidator validator;

    @BeforeAll
    static void setUp() throws Exception {
        FhirContext fhir = FhirContext.forR4Cached();
        ValidationConfig config = new ValidationConfig();
        validator = new ResourceValidator(fhir,
                config.fhirValidator(fhir, config.validationSupport(fhir)),
                new UsCoreProfileSelector());
    }

    @Test
    void usCorePatientIsAccepted() {
        assertThatCode(() -> validator.requireValid(TestData.patient("Lee", "Ann"))).doesNotThrowAnyException();
    }

    @Test
    void builtInTestResourcesOfEveryTypeAreUsCoreValid() {
        String patient = "Patient/p1";
        List<Resource> resources = List.of(
                TestData.labObservation(patient, "2024-05-01T10:00:00Z"),
                TestData.encounter(patient, "2024-05-01T10:00:00Z"),
                TestData.condition(patient),
                TestData.medicationRequest(patient),
                TestData.allergy(patient));

        for (Resource resource : resources) {
            List<String> errors = validator.validate(resource).getMessages().stream()
                    .filter(m -> m.getSeverity().ordinal() >= ResultSeverityEnum.ERROR.ordinal())
                    .map(m -> m.getLocationString() + ": " + m.getMessage())
                    .toList();
            assertThat(errors).as(resource.fhirType()).isEmpty();
        }
    }

    /** The OMB race binding imports VSAC value sets; Synthea patients use it. */
    @Test
    void ombRaceAndEthnicityExtensionsValidate() {
        Patient patient = TestData.patient("Lee", "Ann");
        patient.addExtension(ombExtension("http://hl7.org/fhir/us/core/StructureDefinition/us-core-race", "2106-3", "White"));
        patient.addExtension(ombExtension("http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity",
                "2186-5", "Not Hispanic or Latino"));

        assertThatCode(() -> validator.requireValid(patient)).doesNotThrowAnyException();
    }

    @Test
    void unknownRaceCodeIsRejected() {
        Patient patient = TestData.patient("Lee", "Ann");
        patient.addExtension(ombExtension("http://hl7.org/fhir/us/core/StructureDefinition/us-core-race", "9999-9", "Made up"));

        assertThatThrownBy(() -> validator.requireValid(patient)).isInstanceOf(UnprocessableEntityException.class);
    }

    private static org.hl7.fhir.r4.model.Extension ombExtension(String url, String code, String display) {
        org.hl7.fhir.r4.model.Extension extension = new org.hl7.fhir.r4.model.Extension(url);
        extension.addExtension("ombCategory", new org.hl7.fhir.r4.model.Coding("urn:oid:2.16.840.1.113883.6.238", code, display));
        extension.addExtension("text", new org.hl7.fhir.r4.model.StringType(display));
        return extension;
    }

    @Test
    void madeUpUsCoreProfileIsRejectedNotSilentlyIgnored() {
        Patient patient = TestData.patient("Lee", "Ann");
        patient.getMeta().addProfile("http://hl7.org/fhir/us/core/StructureDefinition/bogus");

        assertThatThrownBy(() -> validator.requireValid(patient)).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void labObservationWithoutSubjectViolatesUsCore() {
        Observation observation = TestData.labObservation("Patient/p1", "2024-05-01");
        observation.setSubject(null);

        assertThatThrownBy(() -> validator.requireValid(observation)).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void patientWithoutGenderViolatesUsCore() {
        Patient patient = TestData.patient("Lee", "Ann");
        patient.setGender(null);

        assertThatThrownBy(() -> validator.requireValid(patient))
                .isInstanceOf(UnprocessableEntityException.class)
                .satisfies(e -> assertThat(diagnostics((UnprocessableEntityException) e)).contains("gender"));
    }

    @Test
    void patientWithoutIdentifierViolatesUsCore() {
        Patient patient = TestData.patient("Lee", "Ann");
        patient.getIdentifier().clear();

        ValidationResult result = validator.validate(patient);

        assertThat(result.getMessages())
                .anySatisfy(m -> {
                    assertThat(m.getSeverity()).isEqualTo(ResultSeverityEnum.ERROR);
                    assertThat(m.getLocationString()).contains("Patient");
                    assertThat(m.getMessage()).contains("identifier");
                });
    }

    @Test
    void baseSpecInvariantViolationIsRejected() {
        Patient patient = TestData.patient("Lee", "Ann");
        // Invariant pat-1: a contact needs details or an organization.
        patient.addContact().addRelationship().setText("friend");

        assertThatThrownBy(() -> validator.requireValid(patient)).isInstanceOf(UnprocessableEntityException.class);
    }

    private static String diagnostics(UnprocessableEntityException e) {
        OperationOutcome outcome = (OperationOutcome) e.getOperationOutcome();
        return outcome.getIssue().stream().map(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
                .reduce("", String::concat);
    }
}
