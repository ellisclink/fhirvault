package dev.fhirvault.fhir;

import dev.fhirvault.AbstractApiIT;
import dev.fhirvault.TestData;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryIT extends AbstractApiIT {

    @Test
    void instanceHistoryListsEveryVersionNewestFirstIncludingDelete() {
        String id = createPatient("Hist");
        Patient v2 = TestData.patient("Hist", "Two");
        v2.setId(id);
        put("Patient/" + id, v2);
        delete("Patient/" + id);

        Bundle history = history("Patient/" + id + "/_history");

        assertThat(history.getType()).isEqualTo(Bundle.BundleType.HISTORY);
        assertThat(history.getTotal()).isEqualTo(3);
        assertThat(history.getEntry()).extracting(e -> e.getRequest().getMethod())
                .containsExactly(Bundle.HTTPVerb.DELETE, Bundle.HTTPVerb.PUT, Bundle.HTTPVerb.POST);
        Bundle.BundleEntryComponent deleted = history.getEntry().get(0);
        assertThat(deleted.hasResource()).isFalse();
        assertThat(deleted.getRequest().getUrl()).isEqualTo("Patient/" + id + "/_history/3");
        Patient second = (Patient) history.getEntry().get(1).getResource();
        assertThat(second.getMeta().getVersionId()).isEqualTo("2");
        assertThat(second.getNameFirstRep().getGivenAsSingleString()).isEqualTo("Two");
    }

    @Test
    void instanceHistoryOfUnknownResourceIs404() {
        assertThat(get("Patient/never/_history").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void sinceFiltersOutOlderVersions() throws InterruptedException {
        String id = createPatient("Since");
        Thread.sleep(5);
        Instant between = Instant.now();
        Thread.sleep(5);
        Patient v2 = TestData.patient("Since", "Two");
        v2.setId(id);
        put("Patient/" + id, v2);

        Bundle history = history("Patient/" + id + "/_history?_since=" + between);

        assertThat(history.getEntry()).hasSize(1);
        assertThat(history.getEntryFirstRep().getResource().getMeta().getVersionId()).isEqualTo("2");
    }

    @Test
    void historyIsPaged() {
        String id = createPatient("Paged");
        for (int i = 0; i < 4; i++) {
            Patient next = TestData.patient("Paged", "V" + i);
            next.setId(id);
            put("Patient/" + id, next);
        }

        Bundle first = history("Patient/" + id + "/_history?_count=2");

        assertThat(first.getTotal()).isEqualTo(5);
        assertThat(first.getEntry()).hasSize(2);
        assertThat(first.getLink("next").getUrl()).contains("_offset=2");
        Bundle third = history("Patient/" + id + "/_history?_count=2&_offset=4");
        assertThat(third.getEntry()).hasSize(1);
        assertThat(third.getEntryFirstRep().getResource().getMeta().getVersionId()).isEqualTo("1");
    }

    @Test
    void typeAndSystemHistoryIncludeNewVersions() {
        Instant start = Instant.now().minusMillis(1);
        String id = createPatient("Type");

        Bundle type = history("Patient/_history?_since=" + start);
        Bundle system = history("_history?_since=" + start);

        assertThat(type.getEntry()).extracting(e -> e.getResource().getIdElement().getIdPart()).contains(id);
        assertThat(system.getEntry()).extracting(e -> e.getResource().getIdElement().getIdPart()).contains(id);
    }

    @Test
    void vreadOfEachVersionMatchesHistory() {
        String id = createPatient("Vread");
        Patient v2 = TestData.patient("Vread", "Two");
        v2.setId(id);
        put("Patient/" + id, v2);

        ResponseEntity<String> v1 = get("Patient/" + id + "/_history/1");

        assertThat(v1.getHeaders().getETag()).isEqualTo("W/\"1\"");
        assertThat(get("Patient/" + id + "/_history/9").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("Patient/" + id + "/_history/abc").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Bundle history(String path) {
        ResponseEntity<String> response = get(path);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return parse(Bundle.class, response);
    }

    private String createPatient(String family) {
        ResponseEntity<String> response = post("Patient", TestData.patient(family, "One"));
        return new IdType(response.getHeaders().getLocation().toString()).getIdPart();
    }
}
