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
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class EncounterResourceProvider extends AbstractResourceProvider<Encounter> {

    public EncounterResourceProvider(ResourceService resourceService, SearchService searchService) {
        super(resourceService, searchService, Encounter.class, "Encounter");
    }

    @Search
    public IBundleProvider search(
            @OptionalParam(name = "_id") TokenAndListParam id,
            @OptionalParam(name = "_lastUpdated") DateAndListParam lastUpdated,
            @OptionalParam(name = Encounter.SP_PATIENT, targetTypes = Patient.class) ReferenceAndListParam patient,
            @OptionalParam(name = Encounter.SP_SUBJECT, targetTypes = Patient.class) ReferenceAndListParam subject,
            @OptionalParam(name = Encounter.SP_IDENTIFIER) TokenAndListParam identifier,
            @OptionalParam(name = Encounter.SP_STATUS) TokenAndListParam status,
            @OptionalParam(name = Encounter.SP_CLASS) TokenAndListParam encounterClass,
            @OptionalParam(name = Encounter.SP_TYPE) TokenAndListParam type,
            @OptionalParam(name = Encounter.SP_DATE) DateAndListParam date,
            @Sort SortSpec sort,
            @Offset Integer offset,
            @Count Integer count) {
        return run(search(id, lastUpdated, sort, offset, count)
                .reference(Encounter.SP_PATIENT, patient)
                .reference(Encounter.SP_SUBJECT, subject)
                .token(Encounter.SP_IDENTIFIER, identifier)
                .token(Encounter.SP_STATUS, status)
                .token(Encounter.SP_CLASS, encounterClass)
                .token(Encounter.SP_TYPE, type)
                .date(Encounter.SP_DATE, date));
    }
}
