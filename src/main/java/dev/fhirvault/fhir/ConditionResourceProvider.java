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
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class ConditionResourceProvider extends AbstractResourceProvider<Condition> {

    public ConditionResourceProvider(ResourceService resourceService, SearchService searchService) {
        super(resourceService, searchService, Condition.class, "Condition");
    }

    @Search
    public IBundleProvider search(
            @OptionalParam(name = "_id") TokenAndListParam id,
            @OptionalParam(name = "_lastUpdated") DateAndListParam lastUpdated,
            @OptionalParam(name = Condition.SP_PATIENT, targetTypes = Patient.class) ReferenceAndListParam patient,
            @OptionalParam(name = Condition.SP_SUBJECT, targetTypes = Patient.class) ReferenceAndListParam subject,
            @OptionalParam(name = Condition.SP_ENCOUNTER, targetTypes = Encounter.class) ReferenceAndListParam encounter,
            @OptionalParam(name = Condition.SP_CODE) TokenAndListParam code,
            @OptionalParam(name = Condition.SP_CATEGORY) TokenAndListParam category,
            @OptionalParam(name = Condition.SP_CLINICAL_STATUS) TokenAndListParam clinicalStatus,
            @OptionalParam(name = Condition.SP_ONSET_DATE) DateAndListParam onsetDate,
            @Sort SortSpec sort,
            @Offset Integer offset,
            @Count Integer count) {
        return run(search(id, lastUpdated, sort, offset, count)
                .reference(Condition.SP_PATIENT, patient)
                .reference(Condition.SP_SUBJECT, subject)
                .reference(Condition.SP_ENCOUNTER, encounter)
                .token(Condition.SP_CODE, code)
                .token(Condition.SP_CATEGORY, category)
                .token(Condition.SP_CLINICAL_STATUS, clinicalStatus)
                .date(Condition.SP_ONSET_DATE, onsetDate));
    }
}
