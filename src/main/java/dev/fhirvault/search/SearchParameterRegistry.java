package dev.fhirvault.search;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.fhirvault.search.SearchParamDef.date;
import static dev.fhirvault.search.SearchParamDef.reference;
import static dev.fhirvault.search.SearchParamDef.string;
import static dev.fhirvault.search.SearchParamDef.token;

/**
 * The one list of indexed search parameters, covering US Core 6.1's required ones for the six types.
 * Paths are simple FHIRPath without resolve(); reference targets are filtered in code. _id and
 * _lastUpdated come from the resource table.
 */
@Component
public class SearchParameterRegistry {

    private static final List<SearchParamDef> DEFINITIONS = List.of(
            token("Patient", "identifier", "Patient.identifier"),
            string("Patient", "name", "Patient.name"),
            string("Patient", "family", "Patient.name.family"),
            string("Patient", "given", "Patient.name.given"),
            date("Patient", "birthdate", "Patient.birthDate"),
            token("Patient", "gender", "Patient.gender"),

            reference("Observation", "patient", "Observation.subject", "Patient"),
            reference("Observation", "subject", "Observation.subject", "Patient"),
            reference("Observation", "encounter", "Observation.encounter", "Encounter"),
            token("Observation", "code", "Observation.code"),
            token("Observation", "category", "Observation.category"),
            token("Observation", "status", "Observation.status"),
            date("Observation", "date", "Observation.effective"),

            reference("Encounter", "patient", "Encounter.subject", "Patient"),
            reference("Encounter", "subject", "Encounter.subject", "Patient"),
            token("Encounter", "identifier", "Encounter.identifier"),
            token("Encounter", "status", "Encounter.status"),
            token("Encounter", "class", "Encounter.class"),
            token("Encounter", "type", "Encounter.type"),
            date("Encounter", "date", "Encounter.period"),

            reference("Condition", "patient", "Condition.subject", "Patient"),
            reference("Condition", "subject", "Condition.subject", "Patient"),
            reference("Condition", "encounter", "Condition.encounter", "Encounter"),
            token("Condition", "code", "Condition.code"),
            token("Condition", "category", "Condition.category"),
            token("Condition", "clinical-status", "Condition.clinicalStatus"),
            date("Condition", "onset-date", "Condition.onset"),

            reference("MedicationRequest", "patient", "MedicationRequest.subject", "Patient"),
            reference("MedicationRequest", "subject", "MedicationRequest.subject", "Patient"),
            reference("MedicationRequest", "encounter", "MedicationRequest.encounter", "Encounter"),
            token("MedicationRequest", "status", "MedicationRequest.status"),
            token("MedicationRequest", "intent", "MedicationRequest.intent"),
            token("MedicationRequest", "code", "MedicationRequest.medication"),
            date("MedicationRequest", "authoredon", "MedicationRequest.authoredOn"),

            reference("AllergyIntolerance", "patient", "AllergyIntolerance.patient", "Patient"),
            token("AllergyIntolerance", "clinical-status", "AllergyIntolerance.clinicalStatus"),
            token("AllergyIntolerance", "code", "AllergyIntolerance.code"));

    private final Map<String, Map<String, SearchParamDef>> byType = new LinkedHashMap<>();

    public SearchParameterRegistry() {
        for (SearchParamDef def : DEFINITIONS) {
            byType.computeIfAbsent(def.resourceType(), t -> new LinkedHashMap<>()).put(def.name(), def);
        }
    }

    public Collection<SearchParamDef> forType(String resourceType) {
        return byType.getOrDefault(resourceType, Map.of()).values();
    }

    public Optional<SearchParamDef> find(String resourceType, String name) {
        return Optional.ofNullable(byType.getOrDefault(resourceType, Map.of()).get(name));
    }

    public SearchParamDef require(String resourceType, String name) {
        return find(resourceType, name).orElseThrow(() ->
                new IllegalArgumentException("No search parameter " + resourceType + "." + name));
    }
}
