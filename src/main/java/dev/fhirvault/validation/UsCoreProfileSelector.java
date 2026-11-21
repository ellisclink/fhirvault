package dev.fhirvault.validation;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Picks the US Core profile for a resource that declares none. A declared US Core profile is used as is. */
@Component
public class UsCoreProfileSelector {

    public static final String US_CORE_BASE = "http://hl7.org/fhir/us/core/StructureDefinition/";

    private static final String CONDITION_CATEGORY = "http://terminology.hl7.org/CodeSystem/condition-category";
    private static final String OBSERVATION_CATEGORY = "http://terminology.hl7.org/CodeSystem/observation-category";

    /** Empty when the resource declares a US Core profile or its type has none. */
    public Optional<String> defaultProfileFor(Resource resource) {
        if (declaresUsCoreProfile(resource)) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (resource.fhirType()) {
            case "Patient" -> "us-core-patient";
            case "Encounter" -> "us-core-encounter";
            case "MedicationRequest" -> "us-core-medicationrequest";
            case "AllergyIntolerance" -> "us-core-allergyintolerance";
            case "Condition" -> hasCategory(((Condition) resource).getCategory(), CONDITION_CATEGORY, "encounter-diagnosis")
                    ? "us-core-condition-encounter-diagnosis"
                    : "us-core-condition-problems-health-concerns";
            case "Observation" -> observationProfile((Observation) resource);
            default -> null;
        }).map(name -> US_CORE_BASE + name);
    }

    private static String observationProfile(Observation observation) {
        List<CodeableConcept> categories = observation.getCategory();
        if (hasCategory(categories, OBSERVATION_CATEGORY, "vital-signs")) {
            return "us-core-vital-signs";
        }
        if (hasCategory(categories, OBSERVATION_CATEGORY, "laboratory")) {
            return "us-core-observation-lab";
        }
        return "us-core-observation-clinical-result";
    }

    private static boolean declaresUsCoreProfile(Resource resource) {
        return resource.getMeta().getProfile().stream()
                .anyMatch(p -> p.getValue() != null && p.getValue().startsWith(US_CORE_BASE));
    }

    private static boolean hasCategory(List<CodeableConcept> categories, String system, String code) {
        return categories.stream()
                .flatMap(c -> c.getCoding().stream())
                .anyMatch(c -> system.equals(c.getSystem()) && code.equals(c.getCode()));
    }
}
