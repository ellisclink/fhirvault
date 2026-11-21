package dev.fhirvault.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The SMART discovery document. Endpoints come from the browser-facing issuer URL, which can differ
 * from the one the server uses internally.
 */
@Component
public class SmartConfiguration {

    public static final List<String> SCOPES_SUPPORTED = List.of(
            "openid", "launch/patient",
            "patient/*.rs", "patient/*.read", "patient/Patient.rs", "patient/Observation.rs", "patient/AuditEvent.rs",
            "user/*.rs", "user/*.cruds", "user/AuditEvent.rs",
            "system/*.rs", "system/*.cruds", "system/AuditEvent.rs");

    private final String issuer;

    public SmartConfiguration(@Value("${fhirvault.smart.issuer:}") String issuer) {
        this.issuer = issuer == null ? "" : issuer.replaceAll("/+$", "");
    }

    public Optional<String> issuer() {
        return issuer.isBlank() ? Optional.empty() : Optional.of(issuer);
    }

    public String authorizeEndpoint() {
        return issuer + "/protocol/openid-connect/auth";
    }

    public String tokenEndpoint() {
        return issuer + "/protocol/openid-connect/token";
    }

    public Map<String, Object> document() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("issuer", issuer);
        doc.put("jwks_uri", issuer + "/protocol/openid-connect/certs");
        doc.put("authorization_endpoint", authorizeEndpoint());
        doc.put("token_endpoint", tokenEndpoint());
        doc.put("grant_types_supported", List.of("authorization_code", "client_credentials"));
        doc.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post"));
        doc.put("scopes_supported", SCOPES_SUPPORTED);
        doc.put("response_types_supported", List.of("code"));
        doc.put("code_challenge_methods_supported", List.of("S256"));
        doc.put("capabilities", List.of(
                "launch-standalone", "client-public", "client-confidential-symmetric",
                "context-standalone-patient", "permission-patient", "permission-user",
                "permission-v1", "permission-v2", "sso-openid-connect"));
        return doc;
    }
}
