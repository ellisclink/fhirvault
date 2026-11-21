package dev.fhirvault.security;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fhirvault.KeycloakTestSupport;
import dev.fhirvault.TestData;
import dev.fhirvault.TestcontainersConfiguration;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end SMART checks with real Keycloak tokens. patient-demo is linked to Patient/example-patient
 * through the "patient" claim.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmartSecurityIT {

    private static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");
    private static final String DEMO_PATIENT = "example-patient";

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakTestSupport::issuer);
        registry.add("fhirvault.smart.issuer", KeycloakTestSupport::issuer);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private FhirContext fhirContext;

    private String systemToken;
    private String otherPatient;
    private String demoObservation;
    private String otherObservation;

    @BeforeAll
    void seed() {
        systemToken = KeycloakTestSupport.systemToken();
        Patient demo = TestData.patient("Demo", "Patient");
        demo.setId(DEMO_PATIENT);
        ResponseEntity<String> put = call(HttpMethod.PUT, "Patient/" + DEMO_PATIENT, demo, systemToken);
        assertThat(put.getStatusCode().is2xxSuccessful()).as(put.getBody()).isTrue();
        otherPatient = location(call(HttpMethod.POST, "Patient", TestData.patient("Other", "Person"), systemToken));
        demoObservation = location(call(HttpMethod.POST, "Observation",
                TestData.labObservation("Patient/" + DEMO_PATIENT, "2024-01-01"), systemToken));
        otherObservation = location(call(HttpMethod.POST, "Observation",
                TestData.labObservation(otherPatient, "2024-01-01"), systemToken));
    }

    @Test
    void requestWithoutTokenIs401WithOperationOutcome() {
        ResponseEntity<String> response = call(HttpMethod.GET, "Patient/" + DEMO_PATIENT, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).startsWith("Bearer");
        assertThat(response.getBody()).contains("OperationOutcome");
    }

    @Test
    void forgedTokenIs401() {
        assertThat(call(HttpMethod.GET, "Patient", null, "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0.").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void discoveryIsPublic() throws Exception {
        CapabilityStatement capability = parse(CapabilityStatement.class, call(HttpMethod.GET, "metadata", null, null));
        assertThat(capability.getRestFirstRep().getSecurity().getServiceFirstRep().getCodingFirstRep().getCode())
                .isEqualTo("SMART-on-FHIR");

        ResponseEntity<String> smart = call(HttpMethod.GET, ".well-known/smart-configuration", null, null);
        JsonNode config = new ObjectMapper().readTree(smart.getBody());
        assertThat(config.get("token_endpoint").asText()).isEqualTo(KeycloakTestSupport.issuer() + "/protocol/openid-connect/token");
        assertThat(config.get("capabilities").toString()).contains("permission-v2");
    }

    @Test
    void patientScopesReachOnlyTheLaunchPatient() {
        String token = KeycloakTestSupport.userToken("patient-demo", "openid launch/patient patient/*.rs");

        assertThat(call(HttpMethod.GET, "Patient/" + DEMO_PATIENT, null, token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, otherPatient, null, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, demoObservation, null, token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, otherObservation, null, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void patientScopedSearchIsNarrowedToTheLaunchPatient() {
        String token = KeycloakTestSupport.userToken("patient-demo", "openid launch/patient patient/*.rs");

        assertThat(ids(call(HttpMethod.GET, "Observation", null, token))).containsExactly(new IdType(demoObservation).getIdPart());
        assertThat(ids(call(HttpMethod.GET, "Observation?patient=" + otherPatient, null, token))).isEmpty();
        assertThat(ids(call(HttpMethod.GET, "Patient", null, token))).containsExactly(DEMO_PATIENT);
        assertThat(call(HttpMethod.GET, "Observation/_history", null, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void readOnlyPatientScopeCannotWrite() {
        String token = KeycloakTestSupport.userToken("patient-demo", "openid launch/patient patient/*.rs");

        ResponseEntity<String> response = call(HttpMethod.POST, "Observation",
                TestData.labObservation("Patient/" + DEMO_PATIENT, "2024-02-02"), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void typeSpecificScopeLimitsTypes() {
        String token = KeycloakTestSupport.userToken("patient-demo", "openid launch/patient patient/Observation.rs");

        assertThat(call(HttpMethod.GET, demoObservation, null, token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "Patient/" + DEMO_PATIENT, null, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void patientScopeWithoutLaunchContextGrantsNothing() {
        String token = KeycloakTestSupport.userToken("patient-demo", "openid patient/*.rs");

        assertThat(call(HttpMethod.GET, "Patient/" + DEMO_PATIENT, null, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void userScopesReachAllPatientsWithTheirPermissions() {
        String readOnly = KeycloakTestSupport.userToken("clinician-demo", "openid user/*.rs");
        String readWrite = KeycloakTestSupport.userToken("clinician-demo", "openid user/*.cruds");

        assertThat(call(HttpMethod.GET, otherPatient, null, readOnly).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.POST, "Patient", TestData.patient("New", "One"), readOnly).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "Patient", TestData.patient("New", "One"), readWrite).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void transactionEntriesAreAuthorisedOneByOne() {
        String token = KeycloakTestSupport.userToken("patient-demo", "openid launch/patient patient/*.rs");
        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry().getRequest().setMethod(Bundle.HTTPVerb.GET).setUrl("Patient/" + DEMO_PATIENT);
        bundle.addEntry().getRequest().setMethod(Bundle.HTTPVerb.GET).setUrl(new IdType(otherPatient).toUnqualifiedVersionless().getValue());

        assertThat(call(HttpMethod.POST, "", bundle, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void systemHistoryNeedsUnrestrictedAccess() {
        String patientToken = KeycloakTestSupport.userToken("patient-demo", "openid launch/patient patient/*.rs");

        assertThat(call(HttpMethod.GET, "_history", null, patientToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "_history?_count=1", null, systemToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Keycloak grants user/* scopes only to users with the clinician role. */
    @Test
    void patientUserCannotObtainUserScopes() {
        String token = KeycloakTestSupport.userToken("patient-demo", "openid user/*.cruds user/AuditEvent.rs");

        assertThat(call(HttpMethod.GET, otherPatient, null, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "AuditEvent", null, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void historyOfAnotherPatientsResourceIsForbidden() {
        String token = KeycloakTestSupport.userToken("patient-demo", "openid launch/patient patient/*.rs");

        assertThat(call(HttpMethod.GET, otherObservation + "/_history", null, token).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, demoObservation + "/_history", null, token).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void auditLogNeedsAnExplicitScopeAndPatientsSeeOnlyTheirOwn() {
        String patientToken = KeycloakTestSupport.userToken("patient-demo",
                "openid launch/patient patient/*.rs patient/AuditEvent.rs");
        call(HttpMethod.GET, "Patient/" + DEMO_PATIENT, null, patientToken);
        String clinicianAll = KeycloakTestSupport.userToken("clinician-demo", "openid user/*.rs");
        String auditor = KeycloakTestSupport.userToken("clinician-demo", "openid user/AuditEvent.rs");

        assertThat(call(HttpMethod.GET, "AuditEvent", null, clinicianAll).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Bundle own = parse(Bundle.class, call(HttpMethod.GET, "AuditEvent?_count=100", null, patientToken));
        assertThat(own.getEntry()).isNotEmpty().allSatisfy(e -> assertThat(((org.hl7.fhir.r4.model.AuditEvent) e.getResource())
                .getEntity()).anySatisfy(entity -> assertThat(entity.getWhat().getReference()).isEqualTo("Patient/" + DEMO_PATIENT)));

        Bundle forOther = parse(Bundle.class, call(HttpMethod.GET,
                "AuditEvent?patient=" + new IdType(otherPatient).getIdPart(), null, auditor));
        assertThat(forOther.getTotal()).isPositive();
        assertThat(forOther.getEntryFirstRep().getResource()).isInstanceOf(org.hl7.fhir.r4.model.AuditEvent.class);
    }

    private ResponseEntity<String> call(HttpMethod method, String path, IBaseResource body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(FHIR_JSON, MediaType.APPLICATION_JSON));
        if (token != null) {
            headers.setBearerAuth(token);
        }
        String json = null;
        if (body != null) {
            headers.setContentType(FHIR_JSON);
            json = fhirContext.newJsonParser().encodeResourceToString(body);
        }
        return rest.exchange("/fhir/" + path, method, new HttpEntity<>(json, headers), String.class);
    }

    private <T extends IBaseResource> T parse(Class<T> type, ResponseEntity<String> response) {
        return fhirContext.newJsonParser().parseResource(type, response.getBody());
    }

    private List<String> ids(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return parse(Bundle.class, response).getEntry().stream()
                .map(e -> e.getResource().getIdElement().getIdPart()).toList();
    }

    private static String location(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
        return new IdType(response.getHeaders().getLocation().toString()).toUnqualifiedVersionless().getValue();
    }
}
