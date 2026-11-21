package dev.fhirvault.security;

import dev.fhirvault.persistence.StoredResource;
import dev.fhirvault.service.ResourceWriteHook;
import dev.fhirvault.service.WriteContext;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** First hook: refuse unauthorized writes before any validation work. */
@Component
@Order(5)
public class AuthorizationWriteHook implements ResourceWriteHook {

    private final SmartAuthorizer authorizer;

    public AuthorizationWriteHook(SmartAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    @Override
    public void beforeWrite(Resource resource, Optional<StoredResource> current, WriteContext context) {
        authorizer.checkWrite(resource, current);
    }

    @Override
    public void beforeDelete(StoredResource current, WriteContext context) {
        authorizer.checkDelete(current);
    }
}
