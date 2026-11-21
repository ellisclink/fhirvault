package dev.fhirvault.security;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One SMART resource scope, e.g. {@code patient/Observation.rs} (v2) or {@code user/*.read} (v1).
 * Non-resource scopes and v2 scopes with query restrictions don't parse and grant nothing.
 */
public record SmartScope(Context context, String resourceType, Set<Permission> permissions) {

    public enum Context { PATIENT, USER, SYSTEM }

    /** SMART v2 permission letters: create, read, update, delete, search. */
    public enum Permission { C, R, U, D, S }

    public static final String WILDCARD = "*";

    private static final Pattern SCOPE = Pattern.compile("^(patient|user|system)/(\\*|[A-Z][A-Za-z]+)\\.([a-z*]+)$");
    private static final Pattern V2_PERMISSIONS = Pattern.compile("^c?r?u?d?s?$");

    public static Optional<SmartScope> parse(String scope) {
        Matcher m = SCOPE.matcher(scope);
        if (!m.matches()) {
            return Optional.empty();
        }
        Context context = Context.valueOf(m.group(1).toUpperCase());
        Set<Permission> permissions = permissions(m.group(3));
        if (permissions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SmartScope(context, m.group(2), permissions));
    }

    /** v1 read, write and * map to v2 letters; v2 letters must be in cruds order. */
    private static Set<Permission> permissions(String text) {
        switch (text) {
            case "read":
                return EnumSet.of(Permission.R, Permission.S);
            case "write":
                return EnumSet.of(Permission.C, Permission.U, Permission.D);
            case "*":
                return EnumSet.allOf(Permission.class);
            default:
                if (!V2_PERMISSIONS.matcher(text).matches()) {
                    return EnumSet.noneOf(Permission.class);
                }
                EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
                for (char c : text.toCharArray()) {
                    result.add(Permission.valueOf(String.valueOf(c).toUpperCase()));
                }
                return result;
        }
    }

    /**
     * Whether this scope covers {@code type}. The wildcard covers clinical types but not AuditEvent,
     * which needs a scope that names it.
     */
    public boolean covers(String type, Permission permission) {
        boolean typeMatches = resourceType.equals(type)
                || (WILDCARD.equals(resourceType) && !"AuditEvent".equals(type));
        return typeMatches && permissions.contains(permission);
    }
}
