package dev.fhirvault.search;

import java.time.Instant;

/** One row of search_index. Only the fields for {@code type} are set. */
public record IndexEntry(
        String paramName,
        SearchParamType type,
        String system,
        String code,
        String string,
        String stringExact,
        Instant dateLow,
        Instant dateHigh,
        String reference) {

    public static IndexEntry token(String param, String system, String code) {
        return new IndexEntry(param, SearchParamType.TOKEN, system, code, null, null, null, null, null);
    }

    public static IndexEntry string(String param, String value) {
        return new IndexEntry(param, SearchParamType.STRING, null, null,
                StringNormalizer.normalize(value), value, null, null, null);
    }

    public static IndexEntry date(String param, DateRange range) {
        return new IndexEntry(param, SearchParamType.DATE, null, null, null, null, range.low(), range.high(), null);
    }

    public static IndexEntry reference(String param, String typeAndId) {
        return new IndexEntry(param, SearchParamType.REFERENCE, null, null, null, null, null, null, typeAndId);
    }
}
