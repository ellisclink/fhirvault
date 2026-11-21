package dev.fhirvault.audit;

import ca.uhn.fhir.rest.annotation.Count;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Offset;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateAndListParam;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Read-only audit trail API: read by id, search by patient, date, action and entity. There are no write
 * methods.
 */
@Component
public class AuditEventResourceProvider implements IResourceProvider {

    private final AuditService auditService;

    public AuditEventResourceProvider(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public Class<AuditEvent> getResourceType() {
        return AuditEvent.class;
    }

    @Read
    public AuditEvent read(@IdParam IdType id) {
        return auditService.read(id.getIdPart());
    }

    @Search
    public IBundleProvider search(
            @OptionalParam(name = AuditEvent.SP_PATIENT, targetTypes = Patient.class) ReferenceAndListParam patient,
            @OptionalParam(name = AuditEvent.SP_DATE) DateAndListParam date,
            @OptionalParam(name = AuditEvent.SP_ACTION) TokenParam action,
            @OptionalParam(name = AuditEvent.SP_ENTITY) ReferenceParam entity,
            @Offset Integer offset,
            @Count Integer count) {
        return AuditQueryBuilder.build(patient, date, action, entity, offset, count)
                .map(auditService::search)
                .map(page -> {
                    SimpleBundleProvider provider = new SimpleBundleProvider(page.events());
                    provider.setSize(page.total());
                    provider.setCurrentPageOffset(page.offset());
                    provider.setCurrentPageSize(page.count());
                    return (IBundleProvider) provider;
                })
                .orElseGet(() -> new SimpleBundleProvider(List.of()));
    }
}
