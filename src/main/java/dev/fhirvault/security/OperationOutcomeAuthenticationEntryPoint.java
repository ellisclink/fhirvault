package dev.fhirvault.security;

import ca.uhn.fhir.context.FhirContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 401s keep Spring's RFC 6750 WWW-Authenticate header and get an OperationOutcome body, like every
 * other FHIR error.
 */
public class OperationOutcomeAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        delegate.commence(request, response, exception);
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.LOGIN)
                .setDiagnostics("A valid bearer token is required");
        response.setContentType("application/fhir+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(FhirContext.forR4Cached().newJsonParser().encodeResourceToString(outcome));
    }
}
