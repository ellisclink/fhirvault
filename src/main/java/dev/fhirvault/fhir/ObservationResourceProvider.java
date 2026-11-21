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
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class ObservationResourceProvider extends AbstractResourceProvider<Observation> {

    public ObservationResourceProvider(ResourceService resourceService, SearchService searchService) {
        super(resourceService, searchService, Observation.class, "Observation");
    }

    @Search
    public IBundleProvider search(
            @OptionalParam(name = "_id") TokenAndListParam id,
            @OptionalParam(name = "_lastUpdated") DateAndListParam lastUpdated,
            @OptionalParam(name = Observation.SP_PATIENT, targetTypes = Patient.class) ReferenceAndListParam patient,
            @OptionalParam(name = Observation.SP_SUBJECT, targetTypes = Patient.class) ReferenceAndListParam subject,
            @OptionalParam(name = Observation.SP_ENCOUNTER, targetTypes = Encounter.class) ReferenceAndListParam encounter,
            @OptionalParam(name = Observation.SP_CODE) TokenAndListParam code,
            @OptionalParam(name = Observation.SP_CATEGORY) TokenAndListParam category,
            @OptionalParam(name = Observation.SP_STATUS) TokenAndListParam status,
            @OptionalParam(name = Observation.SP_DATE) DateAndListParam date,
            @Sort SortSpec sort,
            @Offset Integer offset,
            @Count Integer count) {
        return run(search(id, lastUpdated, sort, offset, count)
                .reference(Observation.SP_PATIENT, patient)
                .reference(Observation.SP_SUBJECT, subject)
                .reference(Observation.SP_ENCOUNTER, encounter)
                .token(Observation.SP_CODE, code)
                .token(Observation.SP_CATEGORY, category)
                .token(Observation.SP_STATUS, status)
                .date(Observation.SP_DATE, date));
    }
}
