package dev.fhirvault.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import dev.fhirvault.TestData;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Planning needs no database. */
class TransactionPlanTest {

    private final TransactionService service = new TransactionService(null, FhirContext.forR4Cached(), null);

    @Test
    void postGetsServerIdAndReferencesAreRewritten() {
        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry().setFullUrl("urn:uuid:p").setResource(TestData.patient("A", "B"))
                .getRequest().setMethod(HTTPVerb.POST).setUrl("Patient");
        bundle.addEntry().setFullUrl("urn:uuid:o").setResource(TestData.labObservation("urn:uuid:p", "2024"))
                .getRequest().setMethod(HTTPVerb.POST).setUrl("Observation");

        List<TransactionService.PlannedEntry> plan = service.plan(bundle);

        String patientId = plan.get(0).id();
        assertThat(patientId).hasSize(36);
        assertThat(((Observation) plan.get(1).resource()).getSubject().getReference()).isEqualTo("Patient/" + patientId);
    }

    @Test
    void putTakesIdAndIfMatchFromRequest() {
        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry().setResource(TestData.patient("A", "B"))
                .getRequest().setMethod(HTTPVerb.PUT).setUrl("Patient/abc").setIfMatch("W/\"4\"");

        TransactionService.PlannedEntry entry = service.plan(bundle).get(0);

        assertThat(entry.id()).isEqualTo("abc");
        assertThat(entry.expectedVersion()).isEqualTo(4L);
        assertThat(entry.resource().getIdElement().getIdPart()).isEqualTo("abc");
    }

    /** HAPI's parser sets resource.id to the urn fullUrl when the urn ends with the id, as in Synthea. */
    @Test
    void putEntryParsedFromJsonKeepsUrlId() {
        String json = """
                {"resourceType":"Bundle","type":"transaction","entry":[{
                  "fullUrl":"urn:uuid:5dc18972-81fa-ffa9-f4f1-a0159bd334ec",
                  "resource":{"resourceType":"Patient","id":"5dc18972-81fa-ffa9-f4f1-a0159bd334ec"},
                  "request":{"method":"PUT","url":"Patient/5dc18972-81fa-ffa9-f4f1-a0159bd334ec"}}]}""";
        Bundle bundle = FhirContext.forR4Cached().newJsonParser().parseResource(Bundle.class, json);

        TransactionService.PlannedEntry entry = service.plan(bundle).get(0);

        assertThat(entry.resource().getIdElement().getIdPart()).isEqualTo("5dc18972-81fa-ffa9-f4f1-a0159bd334ec");
    }

    @Test
    void absurdVersionNumberIsA400NotA500() {
        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry().getRequest().setMethod(HTTPVerb.GET).setUrl("Patient/x/_history/99999999999999999999");

        assertThatThrownBy(() -> service.plan(bundle)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void resourceTypeMustMatchUrl() {
        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry().setResource(TestData.patient("A", "B")).getRequest().setMethod(HTTPVerb.POST).setUrl("Observation");

        assertThatThrownBy(() -> service.plan(bundle))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Bundle.entry[0]");
    }
}
