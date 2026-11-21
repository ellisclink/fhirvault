package dev.fhirvault.fhir;

import ca.uhn.fhir.rest.annotation.Count;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.History;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Offset;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Since;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateAndListParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import dev.fhirvault.search.SearchRequestBuilder;
import dev.fhirvault.search.SearchService;
import dev.fhirvault.service.ResourceService;
import dev.fhirvault.service.WriteResult;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Resource;

/**
 * CRUD and history shared by every resource type. Methods only translate HAPI types to {@link
 * ResourceService} calls; subclasses add search.
 */
public abstract class AbstractResourceProvider<T extends Resource> implements IResourceProvider {

    protected final ResourceService resourceService;
    protected final SearchService searchService;
    private final Class<T> resourceType;
    private final String typeName;

    protected AbstractResourceProvider(ResourceService resourceService, SearchService searchService,
                                       Class<T> resourceType, String typeName) {
        this.resourceService = resourceService;
        this.searchService = searchService;
        this.resourceType = resourceType;
        this.typeName = typeName;
    }

    @Override
    public Class<T> getResourceType() {
        return resourceType;
    }

    protected String typeName() {
        return typeName;
    }

    /** Handles both read and vread ({@code /Type/id/_history/n}). */
    @Read(version = true)
    public T read(@IdParam IdType id) {
        Resource resource = id.hasVersionIdPart()
                ? resourceService.vread(typeName, id.getIdPart(), parseVersion(id))
                : resourceService.read(typeName, id.getIdPart());
        return resourceType.cast(resource);
    }

    @Create
    public MethodOutcome create(@ResourceParam T resource) {
        return toOutcome(resourceService.create(resource));
    }

    /** HAPI puts an If-Match ETag into the id's version, so a versioned id means If-Match. */
    @Update
    public MethodOutcome update(@IdParam IdType id, @ResourceParam T resource) {
        Long expectedVersion = id.hasVersionIdPart() ? parseVersion(id) : null;
        return toOutcome(resourceService.update(typeName, id.getIdPart(), resource, expectedVersion));
    }

    @Delete
    public MethodOutcome delete(@IdParam IdType id) {
        resourceService.delete(typeName, id.getIdPart());
        return new MethodOutcome();
    }

    @History
    public IBundleProvider instanceHistory(@IdParam IdType id, @Since InstantType since,
                                           @Offset Integer offset, @Count Integer count) {
        return HistoryPaging.page(resourceService, typeName, id.getIdPart(), since, offset, count);
    }

    @History
    public IBundleProvider typeHistory(@Since InstantType since, @Offset Integer offset, @Count Integer count) {
        return HistoryPaging.page(resourceService, typeName, null, since, offset, count);
    }

    /** Starts a search with the shared parameters: _id, _lastUpdated, _sort and paging. */
    protected SearchRequestBuilder search(TokenAndListParam id, DateAndListParam lastUpdated, SortSpec sort,
                                          Integer offset, Integer count) {
        return searchService.builder(typeName).ids(id).lastUpdated(lastUpdated).sort(sort).page(offset, count);
    }

    /**
     * Wraps one page. Setting offset and size makes HAPI build stateless _offset links instead of
     * caching results in memory.
     */
    protected IBundleProvider run(SearchRequestBuilder request) {
        SearchService.SearchPage page = searchService.search(request.build());
        SimpleBundleProvider provider = new SimpleBundleProvider(page.resources());
        provider.setSize(page.total());
        provider.setCurrentPageOffset(page.offset());
        provider.setCurrentPageSize(page.count());
        return provider;
    }

    private static MethodOutcome toOutcome(WriteResult result) {
        MethodOutcome outcome = new MethodOutcome(result.resource().getIdElement(), result.created());
        outcome.setResource(result.resource());
        return outcome;
    }

    private static long parseVersion(IdType id) {
        try {
            return Long.parseLong(id.getVersionIdPart());
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("Version id must be a number: " + id.getVersionIdPart());
        }
    }
}
