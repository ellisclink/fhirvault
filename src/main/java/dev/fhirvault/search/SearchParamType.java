package dev.fhirvault.search;

/** Indexed search parameter types. Codes match search_index.param_type. */
public enum SearchParamType {
    TOKEN("token"),
    STRING("string"),
    DATE("date"),
    REFERENCE("reference");

    private final String code;

    SearchParamType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
