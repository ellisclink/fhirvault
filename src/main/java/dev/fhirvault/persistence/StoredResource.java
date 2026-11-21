package dev.fhirvault.persistence;

import java.time.Instant;

/** One stored version of a resource. {@code json} is null for a delete. */
public record StoredResource(
        String resourceType,
        String id,
        long versionId,
        Instant lastUpdated,
        boolean deleted,
        String json) {

    public String typeAndId() {
        return resourceType + "/" + id;
    }
}
