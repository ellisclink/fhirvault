package dev.fhirvault.persistence;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.model.valueset.BundleEntryTransactionMethodEnum;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.TimeZone;

/**
 * Converts between HAPI objects and stored JSON. The server owns {@code id} and {@code
 * meta.versionId/lastUpdated} and overwrites whatever the client sent.
 */
@Component
public class ResourceCodec {

    private final FhirContext fhirContext;

    public ResourceCodec(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    /** Stamps server-managed fields onto the resource and returns the JSON to store. */
    public String prepareAndEncode(Resource resource, String id, long versionId, Instant lastUpdated) {
        resource.setId(new IdType(resource.fhirType(), id, Long.toString(versionId)));
        resource.getMeta().setVersionId(Long.toString(versionId));
        resource.getMeta().setLastUpdatedElement(utc(lastUpdated));
        return encode(resource);
    }

    public String encode(Resource resource) {
        return parser().encodeResourceToString(resource);
    }

    public Resource decode(StoredResource stored) {
        if (stored.deleted()) {
            throw new IllegalArgumentException("Deleted version has no content: " + stored.typeAndId());
        }
        Resource resource = (Resource) parser().parseResource(stored.json());
        // The versioned id lets HAPI emit ETag and versioned Location headers.
        resource.setId(new IdType(stored.resourceType(), stored.id(), Long.toString(stored.versionId())));
        return resource;
    }

    public Resource parse(String json) {
        return (Resource) parser().parseResource(json);
    }

    /**
     * Builds a history entry. A deleted version becomes an empty resource marked DELETE, which HAPI
     * renders as a request with no resource.
     */
    public IAnyResource toHistoryEntry(StoredResource stored) {
        IAnyResource resource;
        BundleEntryTransactionMethodEnum method;
        if (stored.deleted()) {
            resource = (IAnyResource) fhirContext.getResourceDefinition(stored.resourceType()).newInstance();
            resource.setId(new IdType(stored.resourceType(), stored.id(), Long.toString(stored.versionId())));
            ResourceMetadataKeyEnum.DELETED_AT.put(resource, utc(stored.lastUpdated()));
            method = BundleEntryTransactionMethodEnum.DELETE;
        } else {
            resource = decode(stored);
            method = stored.versionId() == 1 ? BundleEntryTransactionMethodEnum.POST : BundleEntryTransactionMethodEnum.PUT;
        }
        ResourceMetadataKeyEnum.ENTRY_TRANSACTION_METHOD.put(resource, method);
        return resource;
    }

    // Always UTC, so stored JSON doesn't depend on the server's time zone.
    private static InstantType utc(Instant instant) {
        return new InstantType(Date.from(instant), TemporalPrecisionEnum.MILLI, TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    private IParser parser() {
        // Parsers are cheap and not thread-safe; FhirContext is.
        return fhirContext.newJsonParser();
    }
}
