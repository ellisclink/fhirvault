package dev.fhirvault.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.ETagSupportEnum;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import dev.fhirvault.audit.AuditInterceptor;
import dev.fhirvault.security.SmartAuthorizationInterceptor;
import dev.fhirvault.security.SmartCapabilityInterceptor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * HAPI entry point for /fhir. Providers and interceptors arrive as Spring beans. Audit runs at hook
 * order 100, after authorization.
 */
@Component
public class FhirRestfulServer extends RestfulServer {

    public FhirRestfulServer(FhirContext fhirContext, List<IResourceProvider> resourceProviders,
                             SystemProvider systemProvider,
                             SmartAuthorizationInterceptor authorizationInterceptor,
                             SmartCapabilityInterceptor capabilityInterceptor,
                             AuditInterceptor auditInterceptor,
                             RequestLoggingInterceptor loggingInterceptor) {
        super(fhirContext);
        setServerName("fhirvault");
        setImplementationDescription("fhirvault FHIR R4 server");
        setDefaultResponseEncoding(EncodingEnum.JSON);
        setETagSupport(ETagSupportEnum.ENABLED);
        setResourceProviders(resourceProviders);
        registerProvider(systemProvider);
        registerInterceptor(authorizationInterceptor);
        registerInterceptor(capabilityInterceptor);
        registerInterceptor(auditInterceptor);
        registerInterceptor(loggingInterceptor);
    }
}
