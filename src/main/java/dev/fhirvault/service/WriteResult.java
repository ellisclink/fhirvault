package dev.fhirvault.service;

import dev.fhirvault.persistence.StoredResource;
import org.hl7.fhir.r4.model.Resource;

/** Result of a create or update. {@code created} means no live version existed (HTTP 201). */
public record WriteResult(Resource resource, StoredResource stored, boolean created) {
}
