package dev.fhirvault.search;

import dev.fhirvault.persistence.ResourceCodec;
import dev.fhirvault.persistence.StoredResource;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Runs a {@link SearchQuery}: one query for the page, one for the total. */
@Service
public class SearchService {

    private static final RowMapper<StoredResource> ROW_MAPPER = (rs, rowNum) -> new StoredResource(
            rs.getString("resource_type"),
            rs.getString("id"),
            rs.getLong("version_id"),
            rs.getTimestamp("last_updated").toInstant(),
            rs.getBoolean("deleted"),
            rs.getString("content"));

    public record SearchPage(List<Resource> resources, int total, int offset, int count) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final SearchSqlBuilder sqlBuilder;
    private final SearchParameterRegistry registry;
    private final ResourceCodec codec;

    public SearchService(NamedParameterJdbcTemplate jdbc, SearchSqlBuilder sqlBuilder,
                         SearchParameterRegistry registry, ResourceCodec codec) {
        this.jdbc = jdbc;
        this.sqlBuilder = sqlBuilder;
        this.registry = registry;
        this.codec = codec;
    }

    public SearchRequestBuilder builder(String resourceType) {
        return new SearchRequestBuilder(registry, resourceType);
    }

    // One snapshot for both the page and the total.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SearchPage search(SearchQuery query) {
        SearchSqlBuilder.BuiltQuery built = sqlBuilder.build(query);
        Integer total = jdbc.queryForObject(built.countSql(), built.params(), Integer.class);
        List<Resource> resources = query.count() == 0
                ? List.of()
                : jdbc.query(built.selectSql(), built.params(), ROW_MAPPER).stream().map(codec::decode).toList();
        return new SearchPage(resources, total == null ? 0 : total, query.offset(), query.count());
    }
}
