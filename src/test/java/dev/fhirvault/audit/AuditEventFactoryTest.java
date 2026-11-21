package dev.fhirvault.audit;

import org.hl7.fhir.r4.model.AuditEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventFactoryTest {

    private final AuditEventFactory factory = new AuditEventFactory();

    @Test
    void buildsBalpStyleEvent() {
        AuditRecord record = new AuditRecord(Instant.parse("2026-01-01T00:00:00Z"), "R", "0", null, "search-type",
                "p1", List.of("Observation/o1/_history/2"), "patient=p1&code=1234-5", "user-123", "smart-app",
                "10.0.0.5", "req-1");

        AuditEvent event = factory.build("a1", record);

        assertThat(event.getType().getCode()).isEqualTo("rest");
        assertThat(event.getSubtypeFirstRep().getCode()).isEqualTo("search-type");
        assertThat(event.getAction()).isEqualTo(AuditEvent.AuditEventAction.R);
        assertThat(event.getOutcome()).isEqualTo(AuditEvent.AuditEventOutcome._0);
        assertThat(event.getAgent()).hasSize(2);
        assertThat(event.getAgentFirstRep().getRequestor()).isTrue();
        assertThat(event.getAgentFirstRep().getWho().getIdentifier().getValue()).isEqualTo("user-123");
        assertThat(event.getAgentFirstRep().getNetwork().getAddress()).isEqualTo("10.0.0.5");
        assertThat(event.getAgent().get(1).getWho().getIdentifier().getValue()).isEqualTo("smart-app");
        assertThat(event.getEntity()).anySatisfy(e -> {
            assertThat(e.getWhat().getReference()).isEqualTo("Patient/p1");
            assertThat(e.getRole().getCode()).isEqualTo("1");
        });
        assertThat(event.getEntity()).anySatisfy(e -> assertThat(e.getWhat().getReference()).isEqualTo("Observation/o1/_history/2"));
        assertThat(event.getEntity()).filteredOn(e -> e.hasQuery()).singleElement().satisfies(e ->
                assertThat(new String(e.getQuery(), StandardCharsets.UTF_8)).isEqualTo("patient=p1&code=1234-5"));
        assertThat(event.getEntity()).filteredOn(e -> e.getWhat().hasIdentifier()).singleElement()
                .satisfies(e -> assertThat(e.getWhat().getIdentifier().getValue()).isEqualTo("req-1"));
    }

    @Test
    void failureCarriesOutcomeDescriptionAndNoPatientEntity() {
        AuditRecord record = new AuditRecord(Instant.now(), "R", "4", "403 denied", "read", null,
                List.of("Observation/o1"), null, "user-1", null, null, null);

        AuditEvent event = factory.build("a2", record);

        assertThat(event.getOutcome()).isEqualTo(AuditEvent.AuditEventOutcome._4);
        assertThat(event.getOutcomeDesc()).isEqualTo("403 denied");
        assertThat(event.getEntity()).noneSatisfy(e -> assertThat(e.getRole().getCode()).isEqualTo("1"));
        assertThat(event.getAgent()).hasSize(1);
    }
}
