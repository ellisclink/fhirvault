package dev.fhirvault.search;

import ca.uhn.fhir.rest.api.SortOrderEnum;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.param.DateAndListParam;
import ca.uhn.fhir.rest.param.DateOrListParam;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.ReferenceOrListParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringAndListParam;
import ca.uhn.fhir.rest.param.StringOrListParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import dev.fhirvault.search.SearchQuery.Criterion;
import dev.fhirvault.search.SearchQuery.DatePrefix;
import dev.fhirvault.search.SearchQuery.DateValue;
import dev.fhirvault.search.SearchQuery.IdValue;
import dev.fhirvault.search.SearchQuery.Kind;
import dev.fhirvault.search.SearchQuery.ReferenceValue;
import dev.fhirvault.search.SearchQuery.Sort;
import dev.fhirvault.search.SearchQuery.StringMatch;
import dev.fhirvault.search.SearchQuery.StringValue;
import dev.fhirvault.search.SearchQuery.TokenValue;
import dev.fhirvault.search.SearchQuery.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Converts HAPI's search parameters into a {@link SearchQuery}. Features this server doesn't implement
 * get 400 instead of being ignored.
 */
public class SearchRequestBuilder {

    public static final int DEFAULT_COUNT = 20;
    public static final int MAX_COUNT = 100;

    private final SearchParameterRegistry registry;
    private final String resourceType;
    private final List<Criterion> criteria = new ArrayList<>();
    private Sort sort = Sort.LAST_UPDATED_DESC;
    private int offset = 0;
    private int count = DEFAULT_COUNT;

    public SearchRequestBuilder(SearchParameterRegistry registry, String resourceType) {
        this.registry = registry;
        this.resourceType = resourceType;
    }

    public SearchRequestBuilder ids(TokenAndListParam param) {
        if (param != null) {
            for (TokenOrListParam or : param.getValuesAsQueryTokens()) {
                add("_id", Kind.ID, or.getValuesAsQueryTokens(), t -> {
                    rejectMissingAndModifier("_id", t.getMissing(), t.getModifier());
                    return new IdValue(t.getValue());
                });
            }
        }
        return this;
    }

    public SearchRequestBuilder lastUpdated(DateAndListParam param) {
        return dates("_lastUpdated", Kind.LAST_UPDATED, param);
    }

    public SearchRequestBuilder date(String name, DateAndListParam param) {
        registry.require(resourceType, name);
        return dates(name, Kind.DATE, param);
    }

    public SearchRequestBuilder token(String name, TokenAndListParam param) {
        registry.require(resourceType, name);
        if (param != null) {
            for (TokenOrListParam or : param.getValuesAsQueryTokens()) {
                add(name, Kind.TOKEN, or.getValuesAsQueryTokens(), t -> toToken(name, t));
            }
        }
        return this;
    }

    public SearchRequestBuilder string(String name, StringAndListParam param) {
        registry.require(resourceType, name);
        if (param != null) {
            for (StringOrListParam or : param.getValuesAsQueryTokens()) {
                add(name, Kind.STRING, or.getValuesAsQueryTokens(), s -> {
                    rejectMissingAndModifier(name, s.getMissing(), null);
                    StringMatch match = s.isExact() ? StringMatch.EXACT
                            : s.isContains() ? StringMatch.CONTAINS : StringMatch.STARTS_WITH;
                    return new StringValue(s.getValue(), match);
                });
            }
        }
        return this;
    }

    public SearchRequestBuilder reference(String name, ReferenceAndListParam param) {
        SearchParamDef def = registry.require(resourceType, name);
        if (param != null) {
            for (ReferenceOrListParam or : param.getValuesAsQueryTokens()) {
                add(name, Kind.REFERENCE, or.getValuesAsQueryTokens(), r -> toReference(def, r));
            }
        }
        return this;
    }

    /** Supports _sort on _lastUpdated and _id only. */
    public SearchRequestBuilder sort(SortSpec spec) {
        if (spec == null) {
            return this;
        }
        if (spec.getChain() != null) {
            throw new InvalidRequestException("Only one _sort key is supported");
        }
        boolean desc = spec.getOrder() == SortOrderEnum.DESC;
        sort = switch (spec.getParamName()) {
            case "_lastUpdated" -> desc ? Sort.LAST_UPDATED_DESC : Sort.LAST_UPDATED_ASC;
            case "_id" -> desc ? Sort.ID_DESC : Sort.ID_ASC;
            default -> throw new InvalidRequestException("Unsupported _sort parameter: " + spec.getParamName()
                    + " (supported: _lastUpdated, _id)");
        };
        return this;
    }

    public SearchRequestBuilder page(Integer requestedOffset, Integer requestedCount) {
        if (requestedOffset != null) {
            if (requestedOffset < 0) {
                throw new InvalidRequestException("_offset must not be negative");
            }
            offset = requestedOffset;
        }
        if (requestedCount != null) {
            if (requestedCount < 0) {
                throw new InvalidRequestException("_count must not be negative");
            }
            count = Math.min(requestedCount, MAX_COUNT);
        }
        return this;
    }

    public SearchQuery build() {
        return new SearchQuery(resourceType, List.copyOf(criteria), sort, offset, count);
    }

    private SearchRequestBuilder dates(String name, Kind kind, DateAndListParam param) {
        if (param != null) {
            for (DateOrListParam or : param.getValuesAsQueryTokens()) {
                add(name, kind, or.getValuesAsQueryTokens(), d -> toDate(name, d));
            }
        }
        return this;
    }

    private <P> void add(String name, Kind kind, List<P> orValues, Function<P, Value> converter) {
        List<Value> values = orValues.stream().map(converter).toList();
        if (!values.isEmpty()) {
            criteria.add(new Criterion(name, kind, values));
        }
    }

    private static TokenValue toToken(String name, TokenParam t) {
        rejectMissingAndModifier(name, t.getMissing(), t.getModifier());
        // HAPI parses "code" as system=null, "|code" as system="", "sys|code" as system="sys".
        boolean systemGiven = t.getSystem() != null;
        String system = systemGiven && !t.getSystem().isEmpty() ? t.getSystem() : null;
        String code = t.getValue() == null || t.getValue().isEmpty() ? null : t.getValue();
        if (code == null && system == null) {
            throw new InvalidRequestException("Empty token value for " + name);
        }
        return new TokenValue(system, code, systemGiven);
    }

    private static ReferenceValue toReference(SearchParamDef def, ReferenceParam r) {
        rejectMissingAndModifier(def.name(), r.getMissing(), null);
        if (r.getChain() != null && !r.getChain().isEmpty()) {
            throw new InvalidRequestException("Chained search is not supported: " + def.name() + "." + r.getChain());
        }
        String type = r.getResourceType() != null ? r.getResourceType() : def.targetType();
        if (def.targetType() != null && !def.targetType().equals(type)) {
            throw new InvalidRequestException(def.name() + " only refers to " + def.targetType() + " resources");
        }
        return new ReferenceValue(type + "/" + r.getIdPart());
    }

    private static DateValue toDate(String name, DateParam d) {
        rejectMissingAndModifier(name, d.getMissing(), null);
        DatePrefix prefix = d.getPrefix() == null ? DatePrefix.EQ : switch (d.getPrefix()) {
            case EQUAL -> DatePrefix.EQ;
            case NOT_EQUAL -> DatePrefix.NE;
            case GREATERTHAN -> DatePrefix.GT;
            case LESSTHAN -> DatePrefix.LT;
            case GREATERTHAN_OR_EQUALS -> DatePrefix.GE;
            case LESSTHAN_OR_EQUALS -> DatePrefix.LE;
            case STARTS_AFTER -> DatePrefix.SA;
            case ENDS_BEFORE -> DatePrefix.EB;
            case APPROXIMATE -> throw new InvalidRequestException("The 'ap' date prefix is not supported");
        };
        try {
            return new DateValue(prefix, DateRange.parse(d.getValueAsString()));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid date for " + name + ": " + d.getValueAsString());
        }
    }

    private static void rejectMissingAndModifier(String name, Boolean missing, Object modifier) {
        if (missing != null) {
            throw new InvalidRequestException("The :missing modifier is not supported (" + name + ")");
        }
        if (modifier != null) {
            throw new InvalidRequestException("Unsupported modifier on " + name + ": " + modifier);
        }
    }
}
