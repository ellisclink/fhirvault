package dev.fhirvault.security;

/**
 * Supplies the {@link SmartAccess} for the request on this thread. JWT-based by default, unrestricted
 * under the noauth profile.
 */
public interface SmartAccessResolver {

    SmartAccess current();
}
