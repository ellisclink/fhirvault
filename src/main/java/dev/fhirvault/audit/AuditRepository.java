package dev.fhirvault.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AuditRepository {

    /** Filters for AuditEvent search; null fields are not applied. */
    public record Filter(String patientId, Instant from, Instant to, String action, String entity,
                         int offset, int count) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final NamedParameterJdbcTemplate jdbc;

    public AuditRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String id, AuditRecord record, String json) {
        jdbc.update("INSERT INTO audit_event (id, recorded, action, outcome, interaction, agent_subject, agent_client,"
                        + " patient_id, request_id, content) VALUES (:id, :recorded, :action, :outcome, :interaction,"
                        + " :subject, :client, :patient, :requestId, CAST(:content AS jsonb))",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("recorded", Timestamp.from(record.recorded()))
                        .addValue("action", record.action())
                        .addValue("outcome", record.outcome())
                        .addValue("interaction", record.interaction())
                        .addValue("subject", truncate(record.agentSubject(), 255))
                        .addValue("client", truncate(record.agentClient(), 255))
                        .addValue("patient", record.patientId())
                        .addValue("requestId", truncate(record.requestId(), 64))
                        .addValue("content", json));
    }

    public Optional<String> findJson(String id) {
        return jdbc.queryForList("SELECT content::text FROM audit_event WHERE id = :id",
                new MapSqlParameterSource("id", id), String.class).stream().findFirst();
    }

    public List<String> search(Filter filter) {
        MapSqlParameterSource params = params(filter)
                .addValue("count", filter.count())
                .addValue("offset", filter.offset());
        return jdbc.queryForList("SELECT content::text FROM audit_event" + where(filter)
                + " ORDER BY recorded DESC, id LIMIT :count OFFSET :offset", params, String.class);
    }

    public int count(Filter filter) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_event" + where(filter), params(filter), Integer.class);
        return total == null ? 0 : total;
    }

    private static String where(Filter filter) {
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        if (filter.patientId() != null) {
            where.append(" AND patient_id = :patient");
        }
        if (filter.from() != null) {
            where.append(" AND recorded >= :from");
        }
        if (filter.to() != null) {
            where.append(" AND recorded < :to");
        }
        if (filter.action() != null) {
            where.append(" AND action = :action");
        }
        if (filter.entity() != null) {
            // Entity references live inside the JSON; match on entity.what.
            where.append(" AND content @> CAST(:entityJson AS jsonb)");
        }
        return where.toString();
    }

    private static MapSqlParameterSource params(Filter filter) {
        return new MapSqlParameterSource()
                .addValue("patient", filter.patientId())
                .addValue("from", filter.from() == null ? null : Timestamp.from(filter.from()))
                .addValue("to", filter.to() == null ? null : Timestamp.from(filter.to()))
                .addValue("action", filter.action())
                .addValue("entityJson", filter.entity() == null ? null : entityContainment(filter.entity()));
    }

    /** {"entity":[{"what":{"reference":"Type/id"}}]}, built with Jackson so input can't change its shape. */
    private static String entityContainment(String reference) {
        ObjectNode root = JSON.createObjectNode();
        root.putArray("entity").addObject().putObject("what").put("reference", reference);
        return root.toString();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
