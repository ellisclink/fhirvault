package dev.fhirvault.loader;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against an unmodified Synthea bundle (synthetic data). */
class SyntheaBundleTransformerTest {

    private static final FhirContext FHIR = FhirContext.forR4Cached();
    private final SyntheaBundleTransformer transformer = new SyntheaBundleTransformer(FHIR);

    @Test
    void keepsOnlySupportedTypesAsIdempotentPuts() {
        SyntheaBundleTransformer.Result result = transformer.transform(sample());

        assertThat(result.bundle().getType()).isEqualTo(Bundle.BundleType.TRANSACTION);
        assertThat(result.bundle().getEntry()).isNotEmpty().allSatisfy(entry -> {
            assertThat(SyntheaBundleTransformer.SUPPORTED).contains(entry.getResource().fhirType());
            assertThat(entry.getRequest().getMethod()).isEqualTo(Bundle.HTTPVerb.PUT);
            assertThat(entry.getRequest().getUrl()).matches(entry.getResource().fhirType() + "/[A-Za-z0-9.-]{1,64}");
            assertThat(entry.getRequest().getUrl()).endsWith("/" + entry.getResource().getIdElement().getIdPart());
        });
        assertThat(result.dropped()).isPositive();
    }

    @Test
    void leavesNoConditionalOrDanglingReferences() {
        SyntheaBundleTransformer.Result result = transformer.transform(sample());
        List<String> fullUrls = result.bundle().getEntry().stream().map(Bundle.BundleEntryComponent::getFullUrl).toList();

        for (Bundle.BundleEntryComponent entry : result.bundle().getEntry()) {
            FHIR.newTerser().getAllResourceReferences(entry.getResource()).forEach(info -> {
                String reference = ((Reference) info.getResourceReference()).getReference();
                if (reference != null) {
                    assertThat(reference).doesNotContain("?");
                    assertThat(fullUrls).as("reference target is in the bundle").contains(reference);
                }
            });
        }
        assertThat(result.referencesFlattened()).isPositive();
    }

    @Test
    void flattenedReferencesKeepADisplay() {
        Bundle bundle = transformer.transform(sample()).bundle();

        bundle.getEntry().stream()
                .flatMap(e -> FHIR.newTerser().getAllResourceReferences(e.getResource()).stream())
                .map(info -> (Reference) info.getResourceReference())
                .filter(r -> !r.hasReference())
                .forEach(r -> assertThat(r.getDisplay()).isNotBlank());
    }

    @Test
    void medicationReferencesAreInlined() {
        SyntheaBundleTransformer.Result result = transformer.transform(sample());

        List<MedicationRequest> requests = result.bundle().getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(MedicationRequest.class::isInstance)
                .map(MedicationRequest.class::cast)
                .toList();
        assertThat(result.medicationsInlined()).isEqualTo(1);
        assertThat(requests).isNotEmpty().allSatisfy(r -> assertThat(r.hasMedicationCodeableConcept()).isTrue());
    }

    private static Bundle sample() {
        try (InputStream in = SyntheaBundleTransformerTest.class.getResourceAsStream("/synthea-sample-patient.json")) {
            return FHIR.newJsonParser().parseResource(Bundle.class, in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
