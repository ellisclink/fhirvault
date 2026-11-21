package dev.fhirvault.persistence;

import ca.uhn.fhir.context.FhirContext;
import dev.fhirvault.TestData;
import dev.fhirvault.search.IndexEntry;
import dev.fhirvault.search.SearchParamType;
import dev.fhirvault.search.SearchParameterRegistry;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SearchIndexExtractorTest {

    private final SearchIndexExtractor extractor =
            new SearchIndexExtractor(FhirContext.forR4Cached(), new SearchParameterRegistry());

    @Test
    void patientNamePartsAreNormalisedAndKeptExact() {
        Patient patient = TestData.patient("Peña", "José");
        patient.getNameFirstRep().addPrefix("Dr.");

        List<IndexEntry> names = entries(extractor.extract(patient), "name");

        assertThat(names).extracting(IndexEntry::string, IndexEntry::stringExact)
                .containsExactlyInAnyOrder(tuple("pena", "Peña"), tuple("jose", "José"), tuple("dr.", "Dr."));
    }

    @Test
    void identifierAndGenderBecomeTokens() {
        Patient patient = TestData.patient("Lee", "Ann");
        patient.getIdentifierFirstRep().setValue("MRN-1");

        List<IndexEntry> entries = extractor.extract(patient);

        assertThat(entries(entries, "identifier")).extracting(IndexEntry::system, IndexEntry::code)
                .containsExactly(tuple(TestData.MRN_SYSTEM, "MRN-1"));
        assertThat(entries(entries, "gender")).extracting(IndexEntry::system, IndexEntry::code)
                .containsExactly(tuple(null, "female"));
    }

    @Test
    void observationIndexesEveryCodingAndOnlyPatientSubjects() {
        Observation observation = TestData.labObservation("Patient/p1", "2024-03-05T10:00:00Z");
        observation.getCode().addCoding().setSystem("http://example.org/local").setCode("GLU");
        observation.setEncounter(new Reference("Encounter/e1"));

        List<IndexEntry> entries = extractor.extract(observation);

        assertThat(entries(entries, "code")).extracting(IndexEntry::code).containsExactlyInAnyOrder("2339-0", "GLU");
        assertThat(entries(entries, "patient")).extracting(IndexEntry::reference).containsExactly("Patient/p1");
        assertThat(entries(entries, "encounter")).extracting(IndexEntry::reference).containsExactly("Encounter/e1");
        assertThat(entries(entries, "date")).singleElement().satisfies(d -> {
            assertThat(d.dateLow()).isEqualTo(Instant.parse("2024-03-05T10:00:00Z"));
            assertThat(d.dateHigh()).isEqualTo(Instant.parse("2024-03-05T10:00:01Z"));
        });
    }

    @Test
    void encounterPeriodBecomesOneRange() {
        Encounter encounter = TestData.encounter("Patient/p1", "2024-01-05T09:00:00Z");
        encounter.getPeriod().getEndElement().setValueAsString("2024-01-07T17:00:00Z");

        assertThat(entries(extractor.extract(encounter), "date")).singleElement().satisfies(d -> {
            assertThat(d.dateLow()).isEqualTo(Instant.parse("2024-01-05T09:00:00Z"));
            assertThat(d.dateHigh()).isEqualTo(Instant.parse("2024-01-07T17:00:01Z"));
        });
    }

    @Test
    void nonDateOnsetIsSkipped() {
        Condition condition = TestData.condition("Patient/p1");
        condition.setOnset(new org.hl7.fhir.r4.model.StringType("childhood"));

        assertThat(entries(extractor.extract(condition), "onset-date")).isEmpty();
        assertThat(extractor.extract(condition)).extracting(IndexEntry::type).doesNotContainNull()
                .contains(SearchParamType.TOKEN, SearchParamType.REFERENCE);
    }

    private static List<IndexEntry> entries(List<IndexEntry> all, String param) {
        return all.stream().filter(e -> e.paramName().equals(param)).toList();
    }
}
