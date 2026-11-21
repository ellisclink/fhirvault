package dev.fhirvault.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class ResourceLinkRepository {

    private final JdbcClient jdbc;

    public ResourceLinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Replaces the outgoing links of a resource with {@code targets} ("Type/id" strings). */
    public void replaceLinks(String sourceType, String sourceId, Collection<String> targets) {
        deleteLinksFrom(sourceType, sourceId);
        for (String target : targets) {
            int slash = target.indexOf('/');
            jdbc.sql("INSERT INTO resource_link (source_type, source_id, target_type, target_id)"
                            + " VALUES (:st, :sid, :tt, :tid) ON CONFLICT DO NOTHING")
                    .param("st", sourceType)
                    .param("sid", sourceId)
                    .param("tt", target.substring(0, slash))
                    .param("tid", target.substring(slash + 1))
                    .update();
        }
    }

    public void deleteLinksFrom(String sourceType, String sourceId) {
        jdbc.sql("DELETE FROM resource_link WHERE source_type = :st AND source_id = :sid")
                .param("st", sourceType)
                .param("sid", sourceId)
                .update();
    }

    /** "Type/id" of every other resource that references the target. */
    public List<String> findReferrers(String targetType, String targetId) {
        return jdbc.sql("SELECT source_type || '/' || source_id FROM resource_link"
                        + " WHERE target_type = :tt AND target_id = :tid"
                        + " AND NOT (source_type = :tt AND source_id = :tid) ORDER BY 1")
                .param("tt", targetType)
                .param("tid", targetId)
                .query(String.class)
                .list();
    }
}
