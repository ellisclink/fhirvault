package dev.fhirvault.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Development and test profile without Keycloak: every request has full access. */
@Component
@Profile("noauth")
public class NoAuthSmartAccessResolver implements SmartAccessResolver {

    @Override
    public SmartAccess current() {
        return SmartAccess.unrestricted("anonymous");
    }
}
