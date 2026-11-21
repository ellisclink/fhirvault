package dev.fhirvault.fhir;

import java.util.Set;

/** The clinical resource types this server stores. References may only point at these. */
public final class SupportedResourceTypes {

    public static final Set<String> CLINICAL = Set.of(
            "Patient", "Observation", "Encounter", "Condition", "MedicationRequest", "AllergyIntolerance");

    private SupportedResourceTypes() {
    }

    public static boolean isClinical(String type) {
        return CLINICAL.contains(type);
    }
}
