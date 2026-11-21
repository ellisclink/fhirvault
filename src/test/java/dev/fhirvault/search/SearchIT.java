package dev.fhirvault.search;

import dev.fhirvault.AbstractApiIT;
import dev.fhirvault.TestData;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Search over HTTP. The database is shared across test classes, so each test uses unique values. */
class SearchIT extends AbstractApiIT {

    @Test
    void nameSearchIsPrefixCaseAndAccentInsensitive() {
        String family = unique("Peña");
        String id = create(TestData.patient(family, "Ana"));

        assertThat(ids(search("Patient?name=" + family.substring(0, 6).toLowerCase().replace("ñ", "n")))).containsExactly(id);
        assertThat(ids(search("Patient?family=" + family.toUpperCase()))).containsExactly(id);
    }

    @Test
    void exactAndContainsModifiers() {
        String family = unique("Smith");
        String id = create(TestData.patient(family, "Jo"));

        assertThat(ids(search("Patient?family:exact=" + family))).containsExactly(id);
        assertThat(ids(search("Patient?family:exact=" + family.toLowerCase()))).isEmpty();
        assertThat(ids(search("Patient?family:contains=" + family.substring(3)))).containsExactly(id);
    }

    @Test
    void identifierTokenWithAndWithoutSystem() {
        Patient patient = TestData.patient(unique("Id"), "Jo");
        String value = UUID.randomUUID().toString();
        patient.getIdentifierFirstRep().setValue(value);
        String id = create(patient);

        assertThat(ids(search("Patient?identifier=" + TestData.MRN_SYSTEM + "|" + value))).containsExactly(id);
        assertThat(ids(search("Patient?identifier=" + value))).containsExactly(id);
        assertThat(ids(search("Patient?identifier=http://other|" + value))).isEmpty();
    }

    @Test
    void repeatedParametersAreAndedAndCommasAreOred() {
        String family = unique("Combo");
        String female = create(TestData.patient(family, "Ann"));
        Patient malePatient = TestData.patient(family, "Bob");
        malePatient.setGender(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE);
        String male = create(malePatient);

        assertThat(ids(search("Patient?family=" + family + "&gender=male"))).containsExactly(male);
        assertThat(ids(search("Patient?family=" + family + "&gender=male,female"))).containsExactlyInAnyOrder(male, female);
        assertThat(ids(search("Patient?family=" + family + "&given=Ann&given=Bob"))).isEmpty();
    }

    @Test
    void birthdatePrefixes() {
        String family = unique("Born");
        Patient older = TestData.patient(family, "Old");
        older.getBirthDateElement().setValueAsString("1980-06-15");
        Patient younger = TestData.patient(family, "Young");
        younger.getBirthDateElement().setValueAsString("2001-01-01");
        String oldId = create(older);
        String youngId = create(younger);

        assertThat(ids(search("Patient?family=" + family + "&birthdate=1980"))).containsExactly(oldId);
        assertThat(ids(search("Patient?family=" + family + "&birthdate=gt1990"))).containsExactly(youngId);
        assertThat(ids(search("Patient?family=" + family + "&birthdate=le1980-06-15"))).containsExactly(oldId);
        assertThat(ids(search("Patient?family=" + family + "&birthdate=ne1980-06-15"))).containsExactly(youngId);
        assertThat(ids(search("Patient?family=" + family + "&birthdate=ge1980-01-01&birthdate=lt2000"))).containsExactly(oldId);
    }

    @Test
    void observationByPatientCodeCategoryAndDate() {
        String patient = "Patient/" + create(TestData.patient(unique("Obs"), "Jo"));
        String march = create(TestData.labObservation(patient, "2024-03-05T10:00:00Z"));
        String may = create(TestData.labObservation(patient, "2024-05-20T08:30:00Z"));
        String patientId = new IdType(patient).getIdPart();

        assertThat(ids(search("Observation?patient=" + patientId))).containsExactlyInAnyOrder(march, may);
        assertThat(ids(search("Observation?subject=" + patient + "&code=http://loinc.org|2339-0")))
                .containsExactlyInAnyOrder(march, may);
        assertThat(ids(search("Observation?patient=" + patientId + "&category=laboratory&date=2024-03")))
                .containsExactly(march);
        assertThat(ids(search("Observation?patient=" + patientId + "&date=ge2024-04-01"))).containsExactly(may);
        assertThat(ids(search("Observation?patient=" + patientId + "&code=8867-4"))).isEmpty();
    }

    @Test
    void idAndLastUpdated() {
        String id = create(TestData.patient(unique("Upd"), "Jo"));

        assertThat(ids(search("Patient?_id=" + id))).containsExactly(id);
        assertThat(ids(search("Patient?_id=" + id + "&_lastUpdated=gt2000-01-01"))).containsExactly(id);
        assertThat(ids(search("Patient?_id=" + id + "&_lastUpdated=lt2000-01-01"))).isEmpty();
    }

    @Test
    void deletedResourcesDisappearFromSearch() {
        String family = unique("Gone");
        String id = create(TestData.patient(family, "Jo"));
        delete("Patient/" + id);

        assertThat(ids(search("Patient?family=" + family))).isEmpty();
    }

    @Test
    void updatedValuesAreReindexed() {
        String family = unique("Before");
        String id = create(TestData.patient(family, "Jo"));
        String renamed = unique("After");
        Patient updated = TestData.patient(renamed, "Jo");
        updated.setId(id);
        put("Patient/" + id, updated);

        assertThat(ids(search("Patient?family=" + family))).isEmpty();
        assertThat(ids(search("Patient?family=" + renamed))).containsExactly(id);
    }

    @Test
    void pagingReturnsTotalAndOffsetLinks() {
        String family = unique("Page");
        for (int i = 0; i < 5; i++) {
            create(TestData.patient(family, "P" + i));
        }

        Bundle first = parse(Bundle.class, get("Patient?family=" + family + "&_count=2&_sort=_id"));

        assertThat(first.getTotal()).isEqualTo(5);
        assertThat(first.getEntry()).hasSize(2);
        assertThat(first.getLink("next")).isNotNull();
        assertThat(first.getLink("next").getUrl()).contains("_offset=2").contains("_count=2");

        Bundle last = parse(Bundle.class, get("Patient?family=" + family + "&_count=2&_offset=4&_sort=_id"));
        assertThat(last.getEntry()).hasSize(1);
        assertThat(last.getLink("next")).isNull();
        assertThat(last.getLink("previous")).isNotNull();
    }

    @Test
    void countIsCappedAndZeroReturnsOnlyTotal() {
        String family = unique("Cap");
        create(TestData.patient(family, "Jo"));

        Bundle onlyTotal = parse(Bundle.class, get("Patient?family=" + family + "&_count=0"));

        assertThat(onlyTotal.getTotal()).isEqualTo(1);
        assertThat(onlyTotal.getEntry()).isEmpty();
    }

    @Test
    void unsupportedFeaturesAreRejectedWith400() {
        assertThat(get("Patient?unknown=1").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("Patient?gender:not=male").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("Observation?patient.name=Lee").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("Patient?birthdate=ap2020").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("Patient?_sort=birthdate").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("Patient?birthdate=notadate").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void capabilityStatementListsSearchParameters() {
        String body = get("metadata").getBody();

        assertThat(body).contains("\"name\":\"patient\"").contains("\"name\":\"birthdate\"");
    }

    private ResponseEntity<String> search(String query) {
        ResponseEntity<String> response = get(query);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private List<String> ids(ResponseEntity<String> response) {
        return parse(Bundle.class, response).getEntry().stream()
                .map(e -> e.getResource().getIdElement().getIdPart())
                .toList();
    }

    private String create(Resource resource) {
        ResponseEntity<String> response = post(resource.fhirType(), resource);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
        return new IdType(response.getHeaders().getLocation().toString()).getIdPart();
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
