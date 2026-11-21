package dev.fhirvault.validation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies a {@code Reference.reference} string. Only {@link Kind#RELATIVE} references (Type/id,
 * optionally versioned) point at something this server can check.
 */
public record ParsedReference(Kind kind, String type, String id) {

    public enum Kind { RELATIVE, CONTAINED, ABSOLUTE, URN, CONDITIONAL, INVALID }

    private static final Pattern LITERAL = Pattern.compile(
            "^(https?://\\S+/)?([A-Z][A-Za-z]+)/([A-Za-z0-9\\-.]{1,64})(/_history/[A-Za-z0-9\\-.]{1,64})?$");

    public static ParsedReference parse(String reference) {
        if (reference.startsWith("#")) {
            return new ParsedReference(Kind.CONTAINED, null, null);
        }
        if (reference.startsWith("urn:")) {
            return new ParsedReference(Kind.URN, null, null);
        }
        if (reference.contains("?")) {
            return new ParsedReference(Kind.CONDITIONAL, null, null);
        }
        Matcher m = LITERAL.matcher(reference);
        if (!m.matches()) {
            return new ParsedReference(Kind.INVALID, null, null);
        }
        Kind kind = m.group(1) == null ? Kind.RELATIVE : Kind.ABSOLUTE;
        return new ParsedReference(kind, m.group(2), m.group(3));
    }

    public String typeAndId() {
        return type + "/" + id;
    }
}
