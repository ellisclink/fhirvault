package dev.fhirvault.persistence;

import dev.fhirvault.search.IndexEntry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class SearchIndexRepository {

    private static final String INSERT = "INSERT INTO search_index (resource_type, resource_id, param_name, param_type,"
            + " value_system, value_code, value_string, value_string_exact, value_date_low, value_date_high, value_reference)"
            + " VALUES (:type, :id, :param, :paramType, :system, :code, :string, :stringExact, :low, :high, :reference)";

    private final NamedParameterJdbcTemplate jdbc;

    public SearchIndexRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Replaces a resource's index rows in one batch. */
    public void replace(String resourceType, String resourceId, List<IndexEntry> entries) {
        delete(resourceType, resourceId);
        SqlParameterSource[] batch = entries.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("type", resourceType)
                        .addValue("id", resourceId)
                        .addValue("param", e.paramName())
                        .addValue("paramType", e.type().code())
                        .addValue("system", e.system())
                        .addValue("code", e.code())
                        .addValue("string", e.string())
                        .addValue("stringExact", e.stringExact())
                        .addValue("low", timestamp(e.dateLow()))
                        .addValue("high", timestamp(e.dateHigh()))
                        .addValue("reference", e.reference()))
                .toArray(SqlParameterSource[]::new);
        if (batch.length > 0) {
            jdbc.batchUpdate(INSERT, batch);
        }
    }

    public void delete(String resourceType, String resourceId) {
        jdbc.update("DELETE FROM search_index WHERE resource_type = :type AND resource_id = :id",
                new MapSqlParameterSource().addValue("type", resourceType).addValue("id", resourceId));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
