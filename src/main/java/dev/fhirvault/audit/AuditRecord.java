package dev.fhirvault.audit;

import java.time.Instant;
import java.util.List;

/**
 * What happened, independent of the FHIR format.
 *
 * @param action      AuditEvent.action: C, R, U, D, or E for a transaction
 * @param outcome     AuditEvent.outcome: 0 success, 4 minor failure, 8 serious failure
 * @param interaction restful-interaction code, e.g. "read" or "search-type"
 * @param patientId   the patient whose data was touched; null if none or unknown
 * @param entities    "Type/id[/_history/n]" of the resources touched for that patient
 * @param query       the query string of a search, stored as entity.query
 */
public record AuditRecord(
        Instant recorded,
        String action,
        String outcome,
        String outcomeDescription,
        String interaction,
        String patientId,
        List<String> entities,
        String query,
        String agentSubject,
        String agentClient,
        String clientAddress,
        String requestId) {
}
