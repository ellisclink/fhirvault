package dev.fhirvault.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.UnknownCodeSystemWarningValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class ValidationConfig {

    /** Pinned so validation results don't drift. */
    public static final String US_CORE_PACKAGE = "classpath:fhir-packages/hl7.fhir.us.core-6.1.0.tgz";
    static final String TERMINOLOGY_FILES = "classpath:fhir-terminology/*.json";

    /**
     * Order matters: the chain asks each module in turn. There is no terminology server, so SNOMED CT,
     * LOINC and RxNorm codes produce warnings instead of rejecting valid data.
     */
    @Bean
    public IValidationSupport validationSupport(FhirContext fhirContext) throws IOException {
        NpmPackageValidationSupport usCore = new NpmPackageValidationSupport(fhirContext);
        usCore.loadPackageFromClasspath(US_CORE_PACKAGE);

        UnknownCodeSystemWarningValidationSupport unknownCodeSystems =
                new UnknownCodeSystemWarningValidationSupport(fhirContext);
        unknownCodeSystems.setNonExistentCodeSystemSeverity(IValidationSupport.IssueSeverity.WARNING);

        // The chain caches lookups and generated snapshots itself (HAPI 8+).
        return new ValidationSupportChain(
                usCore,
                usCoreDependencies(fhirContext),
                new DefaultProfileValidationSupport(fhirContext),
                new CommonCodeSystemsTerminologyService(fhirContext),
                new InMemoryTerminologyServerValidationSupport(fhirContext),
                new SnapshotGeneratingValidationSupport(fhirContext),
                unknownCodeSystems);
    }

    /**
     * VSAC value sets that US Core imports (OMB race and ethnicity, among others). They list their
     * codes, so no code system is needed. The CDC Race & Ethnicity CodeSystem is left out on purpose:
     * its published form has no concepts and blocks expansion.
     */
    static IValidationSupport usCoreDependencies(FhirContext fhirContext) throws IOException {
        PrePopulatedValidationSupport support = new PrePopulatedValidationSupport(fhirContext);
        Resource[] files = new PathMatchingResourcePatternResolver().getResources(TERMINOLOGY_FILES);
        for (Resource file : files) {
            try (InputStream in = file.getInputStream()) {
                support.addResource(fhirContext.newJsonParser().parseResource(in));
            }
        }
        return support;
    }

    @Bean
    public FhirValidator fhirValidator(FhirContext fhirContext, IValidationSupport validationSupport) {
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupport);
        // An unknown meta.profile must not quietly fall back to base R4.
        instanceValidator.setErrorForUnknownProfiles(true);
        FhirValidator validator = fhirContext.newValidator();
        validator.registerValidatorModule(instanceValidator);
        return validator;
    }
}
