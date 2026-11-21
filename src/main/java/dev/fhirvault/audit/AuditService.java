package dev.fhirvault.audit;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Writes and reads the audit trail. Rows are immutable (see migration V5). */
@Service
public class AuditService {

    public record AuditPage(List<AuditEvent> events, int total, int offset, int count) {
    }

    private final AuditRepository repository;
    private final AuditEventFactory factory;
    private final FhirContext fhirContext;

    public AuditService(AuditRepository repository, AuditEventFactory factory, FhirContext fhirContext) {
        this.repository = repository;
        this.factory = factory;
        this.fhirContext = fhirContext;
    }

    /** Own transaction, so the record survives a rolled-back request. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String record(AuditRecord record) {
        String id = UUID.randomUUID().toString();
        AuditEvent event = factory.build(id, record);
        repository.insert(id, record, fhirContext.newJsonParser().encodeResourceToString(event));
        return id;
    }

    @Transactional(readOnly = true)
    public AuditEvent read(String id) {
        String json = repository.findJson(id)
                .orElseThrow(() -> new ResourceNotFoundException(new IdType("AuditEvent", id)));
        return parse(json);
    }

    @Transactional(readOnly = true)
    public AuditPage search(AuditRepository.Filter filter) {
        int total = repository.count(filter);
        List<AuditEvent> events = filter.count() == 0 ? List.of()
                : repository.search(filter).stream().map(this::parse).toList();
        return new AuditPage(events, total, filter.offset(), filter.count());
    }

    private AuditEvent parse(String json) {
        return fhirContext.newJsonParser().parseResource(AuditEvent.class, json);
    }
}
