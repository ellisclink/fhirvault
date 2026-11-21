package dev.fhirvault.fhir;

import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import dev.fhirvault.search.SearchRequestBuilder;
import dev.fhirvault.service.ResourceService;
import org.hl7.fhir.r4.model.InstantType;

/** Shared by instance, type and system _history. Same paging rules as search. */
final class HistoryPaging {

    private HistoryPaging() {
    }

    static IBundleProvider page(ResourceService service, String type, String id, InstantType since,
                                Integer offset, Integer count) {
        int start = offset == null ? 0 : offset;
        int size = count == null ? SearchRequestBuilder.DEFAULT_COUNT : Math.min(count, SearchRequestBuilder.MAX_COUNT);
        if (start < 0 || size < 0) {
            throw new InvalidRequestException("_offset and _count must not be negative");
        }
        ResourceService.HistoryPage page = service.history(type, id,
                since == null ? null : since.getValue().toInstant(), start, size);
        SimpleBundleProvider provider = new SimpleBundleProvider(page.entries());
        provider.setSize(page.total());
        provider.setCurrentPageOffset(page.offset());
        provider.setCurrentPageSize(page.count());
        return provider;
    }
}
