package dev.fhirvault.search;

import dev.fhirvault.search.SearchQuery.Criterion;
import dev.fhirvault.search.SearchQuery.DateValue;
import dev.fhirvault.search.SearchQuery.IdValue;
import dev.fhirvault.search.SearchQuery.ReferenceValue;
import dev.fhirvault.search.SearchQuery.StringValue;
import dev.fhirvault.search.SearchQuery.TokenValue;
import dev.fhirvault.search.SearchQuery.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds parameterised SQL for a {@link SearchQuery}: one EXISTS subquery on search_index per
 * criterion, with its values OR-ed inside. User input is always bound, never concatenated.
 */
@Component
public class SearchSqlBuilder {

    public record BuiltQuery(String selectSql, String countSql, MapSqlParameterSource params) {
    }

    private static final String SELECT = "SELECT r.resource_type, r.id, r.version_id, r.last_updated, r.deleted,"
            + " r.content::text AS content FROM resource r";

    public BuiltQuery build(SearchQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("type", query.resourceType());
        StringBuilder where = new StringBuilder(" WHERE r.resource_type = :type AND NOT r.deleted");

        int criterionIndex = 0;
        for (Criterion criterion : query.criteria()) {
            String prefix = "c" + criterionIndex++;
            where.append(" AND ").append(criterionSql(criterion, prefix, params));
        }

        String orderBy = switch (query.sort()) {
            case LAST_UPDATED_DESC -> " ORDER BY r.last_updated DESC, r.id";
            case LAST_UPDATED_ASC -> " ORDER BY r.last_updated ASC, r.id";
            case ID_ASC -> " ORDER BY r.id ASC";
            case ID_DESC -> " ORDER BY r.id DESC";
        };
        params.addValue("limit", query.count());
        params.addValue("offset", query.offset());
        String select = SELECT + where + orderBy + " LIMIT :limit OFFSET :offset";
        String count = "SELECT COUNT(*) FROM resource r" + where;
        return new BuiltQuery(select, count, params);
    }

    private static String criterionSql(Criterion criterion, String prefix, MapSqlParameterSource params) {
        List<String> alternatives = new ArrayList<>();
        int i = 0;
        for (Value value : criterion.anyOf()) {
            alternatives.add(valueSql(criterion, value, prefix + "v" + i++, params));
        }
        String anyOf = "(" + String.join(" OR ", alternatives) + ")";
        return switch (criterion.kind()) {
            case ID, LAST_UPDATED -> anyOf;
            default -> {
                String nameParam = prefix + "name";
                params.addValue(nameParam, criterion.paramName());
                yield "EXISTS (SELECT 1 FROM search_index si WHERE si.resource_type = r.resource_type"
                        + " AND si.resource_id = r.id AND si.param_name = :" + nameParam + " AND " + anyOf + ")";
            }
        };
    }

    private static String valueSql(Criterion criterion, Value value, String p, MapSqlParameterSource params) {
        return switch (value) {
            case IdValue id -> {
                params.addValue(p, id.id());
                yield "r.id = :" + p;
            }
            case TokenValue token -> tokenSql(token, p, params);
            case StringValue string -> stringSql(string, p, params);
            case ReferenceValue reference -> {
                params.addValue(p, reference.typeAndId());
                yield "si.value_reference = :" + p;
            }
            case DateValue date -> criterion.kind() == SearchQuery.Kind.LAST_UPDATED
                    // lastUpdated is an instant with millisecond precision: the range [t, t + 1ms).
                    ? dateSql(date, "r.last_updated", "(r.last_updated + INTERVAL '1 millisecond')", p, params)
                    : dateSql(date, "COALESCE(si.value_date_low, '-infinity'::timestamptz)",
                            "COALESCE(si.value_date_high, 'infinity'::timestamptz)", p, params);
        };
    }

    private static String tokenSql(TokenValue token, String p, MapSqlParameterSource params) {
        List<String> parts = new ArrayList<>();
        if (token.code() != null) {
            params.addValue(p + "code", token.code());
            parts.add("si.value_code = :" + p + "code");
        }
        if (token.systemGiven()) {
            if (token.system() == null) {
                parts.add("si.value_system IS NULL");
            } else {
                params.addValue(p + "sys", token.system());
                parts.add("si.value_system = :" + p + "sys");
            }
        }
        return "(" + String.join(" AND ", parts) + ")";
    }

    private static String stringSql(StringValue string, String p, MapSqlParameterSource params) {
        return switch (string.match()) {
            case EXACT -> {
                params.addValue(p, string.value());
                yield "si.value_string_exact = :" + p;
            }
            case STARTS_WITH -> {
                params.addValue(p, escapeLike(StringNormalizer.normalize(string.value())) + "%");
                yield "si.value_string LIKE :" + p + " ESCAPE '\\'";
            }
            case CONTAINS -> {
                params.addValue(p, "%" + escapeLike(StringNormalizer.normalize(string.value())) + "%");
                yield "si.value_string LIKE :" + p + " ESCAPE '\\'";
            }
        };
    }

    /**
     * Compares target range [tl, th) with search range [sl, sh) as FHIR defines each prefix: eq means
     * inside, gt means it extends past, and so on.
     */
    private static String dateSql(DateValue date, String tl, String th, String p, MapSqlParameterSource params) {
        String sl = ":" + p + "lo";
        String sh = ":" + p + "hi";
        params.addValue(p + "lo", timestamp(date.range().low()));
        params.addValue(p + "hi", timestamp(date.range().high()));
        return switch (date.prefix()) {
            case EQ -> "(" + tl + " >= " + sl + " AND " + th + " <= " + sh + ")";
            case NE -> "NOT (" + tl + " >= " + sl + " AND " + th + " <= " + sh + ")";
            case GT -> "(" + th + " > " + sh + ")";
            case LT -> "(" + tl + " < " + sl + ")";
            case GE -> "(" + th + " > " + sl + ")";
            case LE -> "(" + tl + " < " + sh + ")";
            case SA -> "(" + tl + " >= " + sh + ")";
            case EB -> "(" + th + " <= " + sl + ")";
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
