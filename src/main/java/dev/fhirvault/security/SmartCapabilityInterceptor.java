package dev.fhirvault.security;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.UriType;
import org.springframework.stereotype.Component;

/** Adds the SMART security block to the CapabilityStatement, as the SMART App Launch spec asks. */
@Component
@Interceptor
public class SmartCapabilityInterceptor {

    private static final String OAUTH_URIS = "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris";

    private final SmartConfiguration configuration;

    public SmartCapabilityInterceptor(SmartConfiguration configuration) {
        this.configuration = configuration;
    }

    @Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
    public void addSecurity(IBaseConformance conformance) {
        if (configuration.issuer().isEmpty() || !(conformance instanceof CapabilityStatement capability)) {
            return;
        }
        for (CapabilityStatement.CapabilityStatementRestComponent rest : capability.getRest()) {
            CapabilityStatement.CapabilityStatementRestSecurityComponent security = rest.getSecurity();
            security.setCors(true);
            security.addService(new CodeableConcept().addCoding(new Coding(
                    "http://terminology.hl7.org/CodeSystem/restful-security-service", "SMART-on-FHIR", "SMART-on-FHIR")));
            security.setDescription("OAuth2 using SMART-on-FHIR profile (see /.well-known/smart-configuration)");
            Extension oauth = security.addExtension().setUrl(OAUTH_URIS);
            oauth.addExtension("authorize", new UriType(configuration.authorizeEndpoint()));
            oauth.addExtension("token", new UriType(configuration.tokenEndpoint()));
        }
    }
}
