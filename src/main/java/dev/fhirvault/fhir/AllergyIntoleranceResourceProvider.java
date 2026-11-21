package dev.fhirvault.fhir;

import ca.uhn.fhir.rest.annotation.Count;
import ca.uhn.fhir.rest.annotation.Offset;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Sort;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateAndListParam;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import dev.fhirvault.search.SearchService;
import dev.fhirvault.service.ResourceService;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class AllergyIntoleranceResourceProvider extends AbstractResourceProvider<AllergyIntolerance> {

    public AllergyIntoleranceResourceProvider(ResourceService resourceService, SearchService searchService) {
        super(resourceService, searchService, AllergyIntolerance.class, "AllergyIntolerance");
    }

    @Search
    public IBundleProvider search(
            @OptionalParam(name = "_id") TokenAndListParam id,
            @OptionalParam(name = "_lastUpdated") DateAndListParam lastUpdated,
            @OptionalParam(name = AllergyIntolerance.SP_PATIENT, targetTypes = Patient.class)
            ReferenceAndListParam patient,
            @OptionalParam(name = AllergyIntolerance.SP_CLINICAL_STATUS) TokenAndListParam clinicalStatus,
            @OptionalParam(name = AllergyIntolerance.SP_CODE) TokenAndListParam code,
            @Sort SortSpec sort,
            @Offset Integer offset,
            @Count Integer count) {
        return run(search(id, lastUpdated, sort, offset, count)
                .reference(AllergyIntolerance.SP_PATIENT, patient)
                .token(AllergyIntolerance.SP_CLINICAL_STATUS, clinicalStatus)
                .token(AllergyIntolerance.SP_CODE, code));
    }
}
