package dev.fhirvault.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Plain SQL for resource and resource_history. Callers own the transaction. */
@Repository
public class ResourceRepository {

    private static final String COLUMNS = "resource_type, id, version_id, last_updated, deleted, content::text AS content";

    private static final RowMapper<StoredResource> ROW_MAPPER = (rs, rowNum) -> new StoredResource(
            rs.getString("resource_type"),
            rs.getString("id"),
            rs.getLong("version_id"),
            rs.getTimestamp("last_updated").toInstant(),
            rs.getBoolean("deleted"),
            rs.getString("content"));

    private final JdbcClient jdbc;

    public ResourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredResource> findCurrent(String type, String id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM resource WHERE resource_type = :type AND id = :id")
                .param("type", type)
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    /** Locks the row so concurrent updates queue instead of losing a version. */
    public Optional<StoredResource> findCurrentForUpdate(String type, String id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM resource WHERE resource_type = :type AND id = :id FOR UPDATE")
                .param("type", type)
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    public Optional<StoredResource> findVersion(String type, String id, long versionId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM resource_history"
                        + " WHERE resource_type = :type AND id = :id AND version_id = :version")
                .param("type", type)
                .param("id", id)
                .param("version", versionId)
                .query(ROW_MAPPER)
                .optional();
    }

    /**
     * Checks for a live resource and takes a KEY SHARE lock. A concurrent delete holds FOR UPDATE on
     * the same row, so one waits for the other and sees its result.
     */
    public boolean existsCurrent(String type, String id) {
        return !jdbc.sql("SELECT 1 FROM resource WHERE resource_type = :type AND id = :id AND NOT deleted FOR KEY SHARE")
                .param("type", type)
                .param("id", id)
                .query(Integer.class)
                .list()
                .isEmpty();
    }

    /** The newest version that has content, even if the resource is now deleted. */
    public Optional<StoredResource> findLatestWithContent(String type, String id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM resource_history WHERE resource_type = :type AND id = :id"
                        + " AND NOT deleted ORDER BY version_id DESC LIMIT 1")
                .param("type", type)
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    /** Inserts the first version of a resource. Fails with a duplicate key if the id is taken. */
    public void insertNew(StoredResource resource) {
        jdbc.sql("INSERT INTO resource (resource_type, id, version_id, last_updated, deleted, content)"
                        + " VALUES (:type, :id, :version, :lastUpdated, :deleted, CAST(:content AS jsonb))")
                .paramSource(params(resource))
                .update();
        insertHistory(resource);
    }

    /** Replaces the current row with a new version and appends it to history. */
    public void updateCurrent(StoredResource resource) {
        jdbc.sql("UPDATE resource SET version_id = :version, last_updated = :lastUpdated, deleted = :deleted,"
                        + " content = CAST(:content AS jsonb) WHERE resource_type = :type AND id = :id")
                .paramSource(params(resource))
                .update();
        insertHistory(resource);
    }

    private void insertHistory(StoredResource resource) {
        jdbc.sql("INSERT INTO resource_history (resource_type, id, version_id, last_updated, deleted, content)"
                        + " VALUES (:type, :id, :version, :lastUpdated, :deleted, CAST(:content AS jsonb))")
                .paramSource(params(resource))
                .update();
    }

    /**
     * Versions, newest first. A null {@code type} means all types and a null {@code id} every resource
     * of the type, so one query serves system, type and instance history.
     */
    public List<StoredResource> history(String type, String id, Instant since, int offset, int count) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM resource_history" + historyWhere(type, id, since)
                        + " ORDER BY last_updated DESC, resource_type, id, version_id DESC LIMIT :count OFFSET :offset")
                .paramSource(historyParams(type, id, since).addValue("count", count).addValue("offset", offset))
                .query(ROW_MAPPER)
                .list();
    }

    public int countHistory(String type, String id, Instant since) {
        return jdbc.sql("SELECT COUNT(*) FROM resource_history" + historyWhere(type, id, since))
                .paramSource(historyParams(type, id, since))
                .query(Integer.class)
                .single();
    }

    private static String historyWhere(String type, String id, Instant since) {
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        if (type != null) {
            where.append(" AND resource_type = :type");
        }
        if (id != null) {
            where.append(" AND id = :id");
        }
        if (since != null) {
            where.append(" AND last_updated >= :since");
        }
        return where.toString();
    }

    private static MapSqlParameterSource historyParams(
            String type, String id, Instant since) {
        return new MapSqlParameterSource()
                .addValue("type", type)
                .addValue("id", id)
                .addValue("since", since == null ? null : Timestamp.from(since));
    }

    private static MapSqlParameterSource params(StoredResource r) {
        return new MapSqlParameterSource()
                .addValue("type", r.resourceType())
                .addValue("id", r.id())
                .addValue("version", r.versionId())
                .addValue("lastUpdated", Timestamp.from(r.lastUpdated()))
                .addValue("deleted", r.deleted())
                .addValue("content", r.json());
    }
}
