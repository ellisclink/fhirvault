package dev.fhirvault.security;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import dev.fhirvault.TestData;
import dev.fhirvault.persistence.ResourceCodec;
import dev.fhirvault.persistence.ResourceRepository;
import dev.fhirvault.persistence.StoredResource;
import dev.fhirvault.search.SearchParameterRegistry;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmartAuthorizerTest {

    private static final FhirContext FHIR = FhirContext.forR4Cached();
    private final ResourceRepository repository = mock(ResourceRepository.class);
    private final ResourceCodec codec = new ResourceCodec(FHIR);

    private SmartAuthorizer authorizerFor(String patientId, String... scopes) {
        List<SmartScope> parsed = java.util.Arrays.stream(scopes).map(s -> SmartScope.parse(s).orElseThrow()).toList();
        SmartAccess access = new SmartAccess(parsed, patientId, "sub", "client", false);
        return new SmartAuthorizer(() -> access, new PatientCompartment(FHIR, new SearchParameterRegistry()), codec, repository);
    }

    private StoredResource stored(Observation observation, String id) {
        return new StoredResource("Observation", id, 1, Instant.now(), false, FHIR.newJsonParser().encodeResourceToString(observation));
    }

    @Test
    void recreatingADeletedIdOfAnotherPatientIsForbidden() {
        when(repository.findLatestWithContent("Observation", "o1"))
                .thenReturn(Optional.of(stored(TestData.labObservation("Patient/b", "2024"), "o1")));
        Observation incoming = TestData.labObservation("Patient/a", "2024");
        incoming.setId("Observation/o1");

        assertThatThrownBy(() -> authorizerFor("a", "patient/Observation.cruds").checkWrite(incoming, Optional.empty()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void creatingANewIdInOwnCompartmentIsAllowed() {
        when(repository.findLatestWithContent("Observation", "new")).thenReturn(Optional.empty());
        Observation incoming = TestData.labObservation("Patient/a", "2024");
        incoming.setId("Observation/new");

        assertThatCode(() -> authorizerFor("a", "patient/Observation.cruds").checkWrite(incoming, Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    void instanceHistoryOfAnotherPatientsDeletedResourceIsForbidden() {
        when(repository.findLatestWithContent("Observation", "o2"))
                .thenReturn(Optional.of(stored(TestData.labObservation("Patient/b", "2024"), "o2")));

        assertThatThrownBy(() -> authorizerFor("a", "patient/*.rs").checkInstanceHistory("Observation", "o2"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void patientSeesRedactedAuditEvents() {
        AuditEvent event = new AuditEvent();
        event.setId("AuditEvent/e1");
        event.addAgent().getNetwork().setAddress("10.1.2.3");
        event.addEntity().setWhat(new Reference("Patient/a")).setRole(new Coding().setCode("1"));
        event.addEntity().setQuery("patient=a,b".getBytes());

        authorizerFor("a", "patient/AuditEvent.rs").checkOutgoing(event, SmartScope.Permission.R);

        assertThat(event.getAgentFirstRep().hasNetwork()).isFalse();
        assertThat(event.getEntity()).noneMatch(AuditEvent.AuditEventEntityComponent::hasQuery);
    }
}
