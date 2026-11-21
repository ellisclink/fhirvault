package dev.fhirvault.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationOptions;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Validates against base R4 and US Core. Only error and fatal issues reject; warnings, such as codes we
 * can't look up, pass.
 */
@Service
public class ResourceValidator {

    private static final Logger log = LoggerFactory.getLogger(ResourceValidator.class);

    private final FhirContext fhirContext;
    private final FhirValidator validator;
    private final UsCoreProfileSelector profileSelector;

    public ResourceValidator(FhirContext fhirContext, FhirValidator validator, UsCoreProfileSelector profileSelector) {
        this.fhirContext = fhirContext;
        this.validator = validator;
        this.profileSelector = profileSelector;
    }

    public ValidationResult validate(Resource resource) {
        ValidationOptions options = new ValidationOptions();
        profileSelector.defaultProfileFor(resource).ifPresent(options::addProfile);
        return validator.validateWithResult(resource, options);
    }

    /** @throws UnprocessableEntityException (422) carrying the error issues as an OperationOutcome */
    public void requireValid(Resource resource) {
        ValidationResult result = validate(resource);
        List<SingleValidationMessage> errors = result.getMessages().stream()
                .filter(m -> m.getSeverity() == ResultSeverityEnum.ERROR || m.getSeverity() == ResultSeverityEnum.FATAL)
                .toList();
        if (errors.isEmpty()) {
            return;
        }
        // Log the type and count only; messages can echo resource content.
        log.info("Rejected {} with {} validation error(s)", resource.fhirType(), errors.size());
        OperationOutcome outcome = new OperationOutcome();
        for (SingleValidationMessage error : errors) {
            outcome.addIssue()
                    .setSeverity(error.getSeverity() == ResultSeverityEnum.FATAL
                            ? OperationOutcome.IssueSeverity.FATAL
                            : OperationOutcome.IssueSeverity.ERROR)
                    .setCode(OperationOutcome.IssueType.PROCESSING)
                    .setDiagnostics(error.getMessage())
                    .addExpression(error.getLocationString());
        }
        throw new UnprocessableEntityException(fhirContext, outcome);
    }
}
