package dev.fhirvault.validation;

import dev.fhirvault.persistence.StoredResource;
import dev.fhirvault.service.ResourceWriteHook;
import dev.fhirvault.service.WriteContext;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** First step of the write pipeline: nothing invalid reaches the database. */
@Component
@Order(10)
public class ValidationWriteHook implements ResourceWriteHook {

    private final ResourceValidator validator;

    public ValidationWriteHook(ResourceValidator validator) {
        this.validator = validator;
    }

    @Override
    public void beforeWrite(Resource resource, Optional<StoredResource> current, WriteContext context) {
        validator.requireValid(resource);
    }
}
