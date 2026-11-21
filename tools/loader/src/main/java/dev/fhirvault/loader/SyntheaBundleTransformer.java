package dev.fhirvault.loader;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.ResourceReferenceInfo;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reshapes a Synthea transaction bundle for fhirvault:
 * <ul>
 *   <li>keeps the six supported types;</li>
 *   <li>turns POSTs into PUTs with Synthea's ids, so reloading updates instead of duplicating;</li>
 *   <li>makes references the server can't check (Practitioner, Organization, Location, dropped
 *       entries) display-only;</li>
 *   <li>inlines Medication.code into the MedicationRequest.</li>
 * </ul>
 */
public class SyntheaBundleTransformer {

    public static final Set<String> SUPPORTED = Set.of(
            "Patient", "Observation", "Encounter", "Condition", "MedicationRequest", "AllergyIntolerance");

    /** Counts for the loader's summary line. */
    public record Result(Bundle bundle, int kept, int dropped, int referencesFlattened, int medicationsInlined) {
    }

    private final FhirContext fhirContext;

    public SyntheaBundleTransformer(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    public Result transform(Bundle source) {
        Map<String, Medication> medications = new HashMap<>();
        Set<String> keptFullUrls = new HashSet<>();
        for (Bundle.BundleEntryComponent entry : source.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof Medication medication) {
                medications.put(entry.getFullUrl(), medication);
            } else if (resource != null && SUPPORTED.contains(resource.fhirType())) {
                keptFullUrls.add(entry.getFullUrl());
            }
        }

        Bundle target = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        int kept = 0;
        int dropped = 0;
        int flattened = 0;
        int inlined = 0;
        for (Bundle.BundleEntryComponent entry : source.getEntry()) {
            Resource resource = entry.getResource();
            if (resource == null || !SUPPORTED.contains(resource.fhirType())) {
                dropped++;
                continue;
            }
            if (resource instanceof MedicationRequest request && inlineMedication(request, medications)) {
                inlined++;
            }
            flattened += flattenUncheckableReferences(resource, keptFullUrls);

            // HAPI's parser sets bundle resource ids to the urn:uuid fullUrl; the bare UUID is
            // Synthea's id.
            String id = bareId(resource.getIdElement().getValue(), entry.getFullUrl());
            resource.setId(resource.fhirType() + "/" + id);
            Bundle.BundleEntryComponent out = target.addEntry().setFullUrl(entry.getFullUrl()).setResource(resource);
            out.getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl(resource.fhirType() + "/" + id);
            kept++;
        }
        return new Result(target, kept, dropped, flattened, inlined);
    }

    private static String bareId(String id, String fullUrl) {
        String candidate = id != null ? id : fullUrl;
        if (candidate.startsWith("urn:uuid:")) {
            return candidate.substring("urn:uuid:".length());
        }
        int slash = candidate.lastIndexOf('/');
        return slash >= 0 ? candidate.substring(slash + 1) : candidate;
    }

    private static boolean inlineMedication(MedicationRequest request, Map<String, Medication> medications) {
        if (!request.hasMedicationReference()) {
            return false;
        }
        Medication medication = medications.get(request.getMedicationReference().getReference());
        if (medication == null || !medication.hasCode()) {
            return false;
        }
        request.setMedication(medication.getCode().copy());
        return true;
    }

    /** Keeps urn:uuid references to entries that stay; the rest become display-only. */
    private int flattenUncheckableReferences(Resource resource, Set<String> keptFullUrls) {
        int count = 0;
        for (ResourceReferenceInfo info : fhirContext.newTerser().getAllResourceReferences(resource)) {
            Reference reference = (Reference) info.getResourceReference();
            String value = reference.getReference();
            if (value == null || value.startsWith("#") || keptFullUrls.contains(value)) {
                continue;
            }
            if (!reference.hasDisplay()) {
                reference.setDisplay(describe(value));
            }
            reference.setReference(null);
            count++;
        }
        return count;
    }

    /** "Practitioner?identifier=..." becomes "Practitioner", which still says what it was. */
    private static String describe(String reference) {
        int cut = reference.indexOf('?');
        if (cut < 0) {
            cut = reference.indexOf('/');
        }
        return cut > 0 ? reference.substring(0, cut) : "external reference";
    }
}
