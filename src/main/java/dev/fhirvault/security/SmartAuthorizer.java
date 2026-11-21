package dev.fhirvault.security;

import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import dev.fhirvault.persistence.ResourceCodec;
import dev.fhirvault.persistence.ResourceRepository;
import dev.fhirvault.persistence.StoredResource;
import dev.fhirvault.security.SmartAccess.Decision;
import dev.fhirvault.security.SmartScope.Permission;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * All SMART access decisions, used by the interceptor, the write hook and the transaction service so
 * every path to patient data is checked.
 */
@Service
public class SmartAuthorizer {

    private final SmartAccessResolver resolver;
    private final PatientCompartment compartment;
    private final ResourceCodec codec;
    private final ResourceRepository resources;

    public SmartAuthorizer(SmartAccessResolver resolver, PatientCompartment compartment, ResourceCodec codec,
                           ResourceRepository resources) {
        this.resolver = resolver;
        this.compartment = compartment;
        this.codec = codec;
        this.resources = resources;
    }

    public SmartAccess access() {
        return resolver.current();
    }

    /** @throws ForbiddenOperationException (403) if no scope grants {@code permission} on {@code type} */
    public Decision require(String type, Permission permission) {
        Decision decision = access().decide(type, permission);
        if (decision == Decision.DENY) {
            throw new ForbiddenOperationException("Access token scopes do not permit "
                    + describe(permission) + " on " + type);
        }
        return decision;
    }

    /** For outgoing resources: the type must be granted and, under patient scopes, in the compartment. */
    public void checkOutgoing(Resource resource, Permission permission) {
        if (ResourceMetadataKeyEnum.DELETED_AT.get(resource) != null) {
            // Delete entries carry no content; the instance itself was checked.
            return;
        }
        if (require(resource.fhirType(), permission) == Decision.PATIENT_COMPARTMENT) {
            requireInCompartment(resource);
            if (resource instanceof AuditEvent event) {
                redactForPatient(event);
            }
        }
    }

    /**
     * Patients may see who opened their record, but not the accessor's IP address or the raw query,
     * which can name other patients.
     */
    static void redactForPatient(AuditEvent event) {
        event.getAgent().forEach(agent -> agent.setNetwork(null));
        event.getEntity().removeIf(AuditEvent.AuditEventEntityComponent::hasQuery);
    }

    /**
     * Delete entries in instance history have no content, so under patient scopes check the newest
     * version that has content.
     */
    public void checkInstanceHistory(String type, String id) {
        if (require(type, Permission.R) == Decision.PATIENT_COMPARTMENT) {
            resources.findLatestWithContent(type, id).ifPresent(latest -> requireInCompartment(codec.decode(latest)));
        }
    }

    /**
     * Create when no live version exists, otherwise update. Under patient scopes the new content and
     * the version it replaces must both be in the compartment, so a resource can't move between
     * patients.
     */
    public void checkWrite(Resource incoming, Optional<StoredResource> current) {
        Permission permission = current.isPresent() ? Permission.U : Permission.C;
        if (require(incoming.fhirType(), permission) == Decision.PATIENT_COMPARTMENT) {
            requireInCompartment(incoming);
            // Re-creating a deleted id must not take over another patient's history.
            Optional<StoredResource> previous = current.isPresent() ? current
                    : resources.findLatestWithContent(incoming.fhirType(), incoming.getIdElement().getIdPart());
            previous.ifPresent(p -> requireInCompartment(codec.decode(p)));
        }
    }

    public void checkDelete(StoredResource current) {
        if (require(current.resourceType(), Permission.D) == Decision.PATIENT_COMPARTMENT) {
            requireInCompartment(codec.decode(current));
        }
    }

    private void requireInCompartment(Resource resource) {
        String patientId = access().patientId();
        if (!compartment.belongsTo(resource, patientId)) {
            throw new ForbiddenOperationException(resource.fhirType() + "/" + resource.getIdElement().getIdPart()
                    + " is outside the launch patient's compartment");
        }
    }

    private static String describe(Permission permission) {
        return switch (permission) {
            case C -> "create";
            case R -> "read";
            case U -> "update";
            case D -> "delete";
            case S -> "search";
        };
    }
}
