package dev.fhirvault.fhir;

import ca.uhn.fhir.rest.annotation.Count;
import ca.uhn.fhir.rest.annotation.History;
import ca.uhn.fhir.rest.annotation.Offset;
import ca.uhn.fhir.rest.annotation.Since;
import ca.uhn.fhir.rest.annotation.Transaction;
import ca.uhn.fhir.rest.annotation.TransactionParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import dev.fhirvault.service.ResourceService;
import dev.fhirvault.service.TransactionService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.InstantType;
import org.springframework.stereotype.Component;

/** Server-level operations that are not tied to one resource type. */
@Component
public class SystemProvider {

    /** User-data key for the transaction's touched resources, read by the audit interceptor. */
    public static final String TOUCHED_RESOURCES = "fhirvault.transaction.touched";

    private final ResourceService resourceService;
    private final TransactionService transactionService;

    public SystemProvider(ResourceService resourceService, TransactionService transactionService) {
        this.resourceService = resourceService;
        this.transactionService = transactionService;
    }

    @History
    public IBundleProvider systemHistory(@Since InstantType since, @Offset Integer offset, @Count Integer count) {
        return HistoryPaging.page(resourceService, null, null, since, offset, count);
    }

    @Transaction
    public Bundle transaction(@TransactionParam Bundle bundle, RequestDetails requestDetails) {
        TransactionService.TransactionResult result = transactionService.execute(bundle);
        requestDetails.getUserData().put(TOUCHED_RESOURCES, result.touched());
        return result.response();
    }
}
