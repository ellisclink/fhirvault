package dev.fhirvault.security;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import dev.fhirvault.search.SearchParameterRegistry;
import dev.fhirvault.validation.ParsedReference;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which patients a resource belongs to. A Patient belongs to itself; other resources belong to the
 * patient in their "patient" search parameter.
 */
@Component
public class PatientCompartment {

    private final IFhirPath fhirPath;
    private final SearchParameterRegistry registry;

    public PatientCompartment(FhirContext fhirContext, SearchParameterRegistry registry) {
        this.fhirPath = fhirContext.newFhirPath();
        this.registry = registry;
    }

    public Set<String> patientIds(Resource resource) {
        Set<String> ids = new LinkedHashSet<>();
        if ("Patient".equals(resource.fhirType())) {
            if (resource.getIdElement().hasIdPart()) {
                ids.add(resource.getIdElement().getIdPart());
            }
            return ids;
        }
        if (resource instanceof AuditEvent event) {
            // An AuditEvent belongs to its role-1 (Patient) entity.
            for (AuditEvent.AuditEventEntityComponent entity : event.getEntity()) {
                if ("1".equals(entity.getRole().getCode()) && entity.getWhat().hasReference()) {
                    ParsedReference parsed = ParsedReference.parse(entity.getWhat().getReference());
                    if (parsed.kind() == ParsedReference.Kind.RELATIVE && "Patient".equals(parsed.type())) {
                        ids.add(parsed.id());
                    }
                }
            }
            return ids;
        }
        registry.find(resource.fhirType(), "patient").ifPresent(def -> {
            for (Reference reference : fhirPath.evaluate(resource, def.path(), Reference.class)) {
                if (reference.hasReference()) {
                    ParsedReference parsed = ParsedReference.parse(reference.getReference());
                    if (parsed.kind() == ParsedReference.Kind.RELATIVE && "Patient".equals(parsed.type())) {
                        ids.add(parsed.id());
                    }
                }
            }
        });
        return ids;
    }

    public boolean belongsTo(Resource resource, String patientId) {
        return patientIds(resource).contains(patientId);
    }
}
