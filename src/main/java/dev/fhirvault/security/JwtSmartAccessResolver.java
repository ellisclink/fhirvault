package dev.fhirvault.security;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Reads scopes and launch context from the JWT. Spring Security has already checked signature, issuer,
 * audience and expiry.
 */
@Component
@Profile("!noauth")
public class JwtSmartAccessResolver implements SmartAccessResolver {

    @Override
    public SmartAccess current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AuthenticationException("A bearer token is required");
        }
        return fromJwt(token.getToken());
    }

    static SmartAccess fromJwt(Jwt jwt) {
        String scopeClaim = Optional.ofNullable(jwt.getClaimAsString("scope")).orElse("");
        List<SmartScope> scopes = Arrays.stream(scopeClaim.split(" "))
                .map(SmartScope::parse)
                .flatMap(Optional::stream)
                .toList();
        String patient = jwt.getClaimAsString("patient");
        String client = Optional.ofNullable(jwt.getClaimAsString("azp")).orElse(jwt.getClaimAsString("client_id"));
        return new SmartAccess(scopes, patient == null || patient.isBlank() ? null : patient,
                jwt.getSubject(), client, false);
    }
}
