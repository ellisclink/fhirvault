package dev.fhirvault.search;

import java.util.List;

/**
 * A parsed search: all criteria must match (AND) and any value within a criterion may match (OR), as in
 * FHIR.
 */
public record SearchQuery(String resourceType, List<Criterion> criteria, Sort sort, int offset, int count) {

    public enum Kind { TOKEN, STRING, DATE, REFERENCE, ID, LAST_UPDATED }

    public enum Sort { LAST_UPDATED_DESC, LAST_UPDATED_ASC, ID_ASC, ID_DESC }

    public enum StringMatch { STARTS_WITH, EXACT, CONTAINS }

    /** FHIR date prefixes; "ap" (approximately) is not supported. */
    public enum DatePrefix { EQ, NE, GT, LT, GE, LE, SA, EB }

    public record Criterion(String paramName, Kind kind, List<Value> anyOf) {
    }

    public sealed interface Value permits TokenValue, StringValue, DateValue, ReferenceValue, IdValue {
    }

    /**
     * @param systemGiven true for "system|code" or "|code"; with a null system it means codes
     *                    without a system.
     */
    public record TokenValue(String system, String code, boolean systemGiven) implements Value {
    }

    public record StringValue(String value, StringMatch match) implements Value {
    }

    public record DateValue(DatePrefix prefix, DateRange range) implements Value {
    }

    public record ReferenceValue(String typeAndId) implements Value {
    }

    public record IdValue(String id) implements Value {
    }
}
