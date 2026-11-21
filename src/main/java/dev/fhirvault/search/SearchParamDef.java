package dev.fhirvault.search;

/**
 * One supported search parameter.
 *
 * @param path       FHIRPath selecting the elements to index
 * @param targetType for references: only this type is indexed, and a bare id ("patient=123")
 *                   means targetType/123. Null otherwise.
 */
public record SearchParamDef(String resourceType, String name, SearchParamType type, String path, String targetType) {

    public static SearchParamDef token(String resourceType, String name, String path) {
        return new SearchParamDef(resourceType, name, SearchParamType.TOKEN, path, null);
    }

    public static SearchParamDef string(String resourceType, String name, String path) {
        return new SearchParamDef(resourceType, name, SearchParamType.STRING, path, null);
    }

    public static SearchParamDef date(String resourceType, String name, String path) {
        return new SearchParamDef(resourceType, name, SearchParamType.DATE, path, null);
    }

    public static SearchParamDef reference(String resourceType, String name, String path, String targetType) {
        return new SearchParamDef(resourceType, name, SearchParamType.REFERENCE, path, targetType);
    }
}
