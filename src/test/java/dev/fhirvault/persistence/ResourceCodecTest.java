package dev.fhirvault.persistence;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.valueset.BundleEntryTransactionMethodEnum;
import dev.fhirvault.TestData;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceCodecTest {

    private static final FhirContext FHIR = FhirContext.forR4Cached();
    private final ResourceCodec codec = new ResourceCodec(FHIR);

    @Test
    void prepareOverwritesClientIdAndMeta() {
        Patient patient = TestData.patient("Lee", "Ann");
        patient.setId("client-id");
        patient.getMeta().setVersionId("99");

        String json = codec.prepareAndEncode(patient, "server-id", 3, Instant.parse("2026-01-02T03:04:05.678Z"));

        Resource stored = codec.parse(json);
        assertThat(stored.getIdElement().getIdPart()).isEqualTo("server-id");
        assertThat(stored.getMeta().getVersionId()).isEqualTo("3");
        assertThat(stored.getMeta().getLastUpdated().toInstant()).isEqualTo(Instant.parse("2026-01-02T03:04:05.678Z"));
        assertThat(json).contains("\"lastUpdated\":\"2026-01-02T03:04:05.678+00:00\"");
    }

    @Test
    void decodeCarriesVersionInId() {
        StoredResource stored = new StoredResource("Patient", "p1", 2, Instant.now(), false,
                FHIR.newJsonParser().encodeResourceToString(TestData.patient("Lee", "Ann")));

        Resource decoded = codec.decode(stored);

        assertThat(decoded.getIdElement().getValue()).isEqualTo("Patient/p1/_history/2");
    }

    @Test
    void historyEntryMarksMethodByVersionAndDelete() {
        String json = FHIR.newJsonParser().encodeResourceToString(TestData.patient("Lee", "Ann"));
        IAnyResource first = codec.toHistoryEntry(new StoredResource("Patient", "p1", 1, Instant.now(), false, json));
        IAnyResource second = codec.toHistoryEntry(new StoredResource("Patient", "p1", 2, Instant.now(), false, json));
        IAnyResource deleted = codec.toHistoryEntry(new StoredResource("Patient", "p1", 3, Instant.now(), true, null));

        assertThat(ResourceMetadataKeyEnum.ENTRY_TRANSACTION_METHOD.get(first)).isEqualTo(BundleEntryTransactionMethodEnum.POST);
        assertThat(ResourceMetadataKeyEnum.ENTRY_TRANSACTION_METHOD.get(second)).isEqualTo(BundleEntryTransactionMethodEnum.PUT);
        assertThat(ResourceMetadataKeyEnum.ENTRY_TRANSACTION_METHOD.get(deleted)).isEqualTo(BundleEntryTransactionMethodEnum.DELETE);
        assertThat(deleted.getIdElement().getVersionIdPart()).isEqualTo("3");
    }
}
