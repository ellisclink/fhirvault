package dev.fhirvault.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.ResourceVersionConflictException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.util.ResourceReferenceInfo;
import dev.fhirvault.fhir.SupportedResourceTypes;
import dev.fhirvault.persistence.ResourceLinkRepository;
import dev.fhirvault.persistence.ResourceRepository;
import dev.fhirvault.persistence.StoredResource;
import dev.fhirvault.service.ResourceWriteHook;
import dev.fhirvault.service.WriteContext;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Referential integrity. On write, every literal reference must point at a live resource of a supported
 * type, or one the same bundle creates. On delete, nothing else may still reference it. Outgoing
 * references are kept in resource_link.
 */
@Component
@Order(20)
public class ReferenceWriteHook implements ResourceWriteHook {

    private static final int MAX_REFERRERS_REPORTED = 5;

    private final FhirContext fhirContext;
    private final ResourceRepository resources;
    private final ResourceLinkRepository links;

    public ReferenceWriteHook(FhirContext fhirContext, ResourceRepository resources, ResourceLinkRepository links) {
        this.fhirContext = fhirContext;
        this.resources = resources;
        this.links = links;
    }

    @Override
    public void beforeWrite(Resource resource, Optional<StoredResource> current, WriteContext context) {
        String self = resource.fhirType() + "/" + resource.getIdElement().getIdPart();
        OperationOutcome outcome = new OperationOutcome();
        for (ResourceReferenceInfo info : references(resource)) {
            String value = info.getResourceReference().getReferenceElement().getValue();
            String problem = check(ParsedReference.parse(value), self, context);
            if (problem != null) {
                outcome.addIssue()
                        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                        .setCode(OperationOutcome.IssueType.PROCESSING)
                        .setDiagnostics(problem + ": " + value)
                        .addExpression(resource.fhirType() + "." + info.getName());
            }
        }
        if (outcome.hasIssue()) {
            throw new UnprocessableEntityException(fhirContext, outcome);
        }
    }

    @Override
    public void afterWrite(Resource resource, StoredResource stored) {
        Set<String> targets = new LinkedHashSet<>();
        for (ResourceReferenceInfo info : references(resource)) {
            ParsedReference parsed = ParsedReference.parse(info.getResourceReference().getReferenceElement().getValue());
            if (parsed.kind() == ParsedReference.Kind.RELATIVE) {
                targets.add(parsed.typeAndId());
            }
        }
        links.replaceLinks(stored.resourceType(), stored.id(), targets);
    }

    @Override
    public void beforeDelete(StoredResource current, WriteContext context) {
        List<String> referrers = links.findReferrers(current.resourceType(), current.id()).stream()
                .filter(r -> !context.pendingDeletes().contains(r))
                .toList();
        if (!referrers.isEmpty()) {
            throw new ResourceVersionConflictException("Cannot delete " + current.typeAndId()
                    + " because it is referenced by " + referrers.size() + " resource(s), e.g. "
                    + String.join(", ", referrers.subList(0, Math.min(MAX_REFERRERS_REPORTED, referrers.size()))));
        }
    }

    @Override
    public void afterDelete(StoredResource tombstone) {
        links.deleteLinksFrom(tombstone.resourceType(), tombstone.id());
    }

    private List<ResourceReferenceInfo> references(Resource resource) {
        return fhirContext.newTerser().getAllResourceReferences(resource).stream()
                .filter(info -> info.getResourceReference().getReferenceElement().getValue() != null)
                .toList();
    }

    /** Returns a problem description, or null when the reference is acceptable. */
    private String check(ParsedReference ref, String self, WriteContext context) {
        return switch (ref.kind()) {
            case CONTAINED -> null;
            case URN -> "Unresolved urn reference (only allowed between entries of a transaction bundle)";
            case CONDITIONAL -> "Conditional references are not supported";
            case ABSOLUTE -> "Absolute references are not supported; use a relative Type/id reference";
            case INVALID -> "Malformed reference";
            case RELATIVE -> {
                if (!SupportedResourceTypes.isClinical(ref.type())) {
                    yield "Reference to unsupported resource type " + ref.type();
                }
                String target = ref.typeAndId();
                boolean exists = target.equals(self)
                        || context.pendingCreates().contains(target)
                        || resources.existsCurrent(ref.type(), ref.id());
                yield exists ? null : "Referenced resource does not exist";
            }
        };
    }
}
