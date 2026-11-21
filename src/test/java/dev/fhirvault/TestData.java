package dev.fhirvault;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;

import java.util.UUID;

/** Hand-made synthetic resources that satisfy US Core 6.1. Never real patient data. */
public final class TestData {

    public static final String MRN_SYSTEM = "http://hospital.example.org/mrn";
    public static final String LOINC = "http://loinc.org";
    public static final String SNOMED = "http://snomed.info/sct";
    public static final String RXNORM = "http://www.nlm.nih.gov/research/umls/rxnorm";

    private TestData() {
    }

    public static Patient patient(String family, String given) {
        Patient patient = new Patient();
        patient.addIdentifier().setSystem(MRN_SYSTEM).setValue("MRN-" + UUID.randomUUID());
        patient.addName().setFamily(family).addGiven(given);
        patient.setGender(AdministrativeGender.FEMALE);
        return patient;
    }

    /** A glucose lab result (US Core Observation Lab). */
    public static Observation labObservation(String patientRef, String effective) {
        Observation observation = new Observation();
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.addCategory(concept("http://terminology.hl7.org/CodeSystem/observation-category", "laboratory", "Laboratory"));
        observation.setCode(concept(LOINC, "2339-0", "Glucose [Mass/volume] in Blood"));
        observation.setSubject(new Reference(patientRef));
        observation.setEffective(new DateTimeType(effective));
        observation.setValue(new Quantity().setValue(95).setUnit("mg/dL")
                .setSystem("http://unitsofmeasure.org").setCode("mg/dL"));
        return observation;
    }

    public static Encounter encounter(String patientRef, String start) {
        Encounter encounter = new Encounter();
        encounter.addIdentifier().setSystem("http://hospital.example.org/encounter").setValue(UUID.randomUUID().toString());
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.setClass_(new Coding("http://terminology.hl7.org/CodeSystem/v3-ActCode", "AMB", "ambulatory"));
        encounter.addType(concept(SNOMED, "185349003", "Encounter for check up"));
        encounter.setSubject(new Reference(patientRef));
        encounter.getPeriod().setStartElement(new DateTimeType(start)).setEndElement(new DateTimeType(start));
        return encounter;
    }

    public static Condition condition(String patientRef) {
        Condition condition = new Condition();
        condition.setClinicalStatus(concept("http://terminology.hl7.org/CodeSystem/condition-clinical", "active", "Active"));
        condition.setVerificationStatus(concept("http://terminology.hl7.org/CodeSystem/condition-ver-status", "confirmed", "Confirmed"));
        condition.addCategory(concept("http://terminology.hl7.org/CodeSystem/condition-category", "problem-list-item", "Problem List Item"));
        condition.setCode(concept(SNOMED, "44054006", "Diabetes mellitus type 2"));
        condition.setSubject(new Reference(patientRef));
        condition.setOnset(new DateTimeType("2020-04-01"));
        return condition;
    }

    public static MedicationRequest medicationRequest(String patientRef) {
        MedicationRequest request = new MedicationRequest();
        request.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
        request.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        request.setMedication(concept(RXNORM, "860975", "metformin hydrochloride 500 MG Oral Tablet"));
        request.setSubject(new Reference(patientRef));
        request.setAuthoredOnElement(new DateTimeType("2020-04-01"));
        request.setRequester(new Reference(patientRef));
        return request;
    }

    public static AllergyIntolerance allergy(String patientRef) {
        AllergyIntolerance allergy = new AllergyIntolerance();
        allergy.setClinicalStatus(concept("http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", "active", "Active"));
        allergy.setVerificationStatus(concept("http://terminology.hl7.org/CodeSystem/allergyintolerance-verification", "confirmed", "Confirmed"));
        allergy.setCode(concept(SNOMED, "91936005", "Allergy to penicillin"));
        allergy.setPatient(new Reference(patientRef));
        return allergy;
    }

    public static CodeableConcept concept(String system, String code, String display) {
        return new CodeableConcept().addCoding(new Coding(system, code, display)).setText(display);
    }
}
