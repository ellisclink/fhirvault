package dev.fhirvault.persistence;

import dev.fhirvault.service.ResourceWriteHook;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Keeps search_index in step with the current version, in the write's transaction. */
@Component
@Order(30)
public class SearchIndexWriteHook implements ResourceWriteHook {

    private final SearchIndexExtractor extractor;
    private final SearchIndexRepository repository;

    public SearchIndexWriteHook(SearchIndexExtractor extractor, SearchIndexRepository repository) {
        this.extractor = extractor;
        this.repository = repository;
    }

    @Override
    public void afterWrite(Resource resource, StoredResource stored) {
        repository.replace(stored.resourceType(), stored.id(), extractor.extract(resource));
    }

    @Override
    public void afterDelete(StoredResource tombstone) {
        repository.delete(tombstone.resourceType(), tombstone.id());
    }
}
