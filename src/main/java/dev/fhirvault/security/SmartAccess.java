package dev.fhirvault.security;

import dev.fhirvault.security.SmartScope.Context;
import dev.fhirvault.security.SmartScope.Permission;

import java.util.List;

/**
 * What the current caller may do, from the access token.
 *
 * @param patientId the launch patient ("patient" claim); patient/ scopes grant nothing without it
 * @param subject   the token subject, recorded in the audit trail
 * @param clientId  the OAuth client that obtained the token
 */
public record SmartAccess(List<SmartScope> scopes, String patientId, String subject, String clientId,
                          boolean unrestricted) {

    /** The result of an access decision for one resource type and permission. */
    public enum Decision { DENY, PATIENT_COMPARTMENT, ALLOW }

    /** Used only by the noauth profile, where there is no token. */
    public static SmartAccess unrestricted(String subject) {
        return new SmartAccess(List.of(), null, subject, "noauth", true);
    }

    /**
     * User and system scopes reach all patients; patient scopes reach only the launch patient. The
     * broadest matching scope wins.
     */
    public Decision decide(String type, Permission permission) {
        if (unrestricted) {
            return Decision.ALLOW;
        }
        Decision decision = Decision.DENY;
        for (SmartScope scope : scopes) {
            if (!scope.covers(type, permission)) {
                continue;
            }
            if (scope.context() == Context.USER || scope.context() == Context.SYSTEM) {
                return Decision.ALLOW;
            }
            if (patientId != null) {
                decision = Decision.PATIENT_COMPARTMENT;
            }
        }
        return decision;
    }
}
