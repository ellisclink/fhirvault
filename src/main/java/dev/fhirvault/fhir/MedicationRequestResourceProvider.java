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
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class MedicationRequestResourceProvider extends AbstractResourceProvider<MedicationRequest> {

    public MedicationRequestResourceProvider(ResourceService resourceService, SearchService searchService) {
        super(resourceService, searchService, MedicationRequest.class, "MedicationRequest");
    }

    @Search
    public IBundleProvider search(
            @OptionalParam(name = "_id") TokenAndListParam id,
            @OptionalParam(name = "_lastUpdated") DateAndListParam lastUpdated,
            @OptionalParam(name = MedicationRequest.SP_PATIENT, targetTypes = Patient.class) ReferenceAndListParam patient,
            @OptionalParam(name = MedicationRequest.SP_SUBJECT, targetTypes = Patient.class) ReferenceAndListParam subject,
            @OptionalParam(name = MedicationRequest.SP_ENCOUNTER, targetTypes = Encounter.class)
            ReferenceAndListParam encounter,
            @OptionalParam(name = MedicationRequest.SP_STATUS) TokenAndListParam status,
            @OptionalParam(name = MedicationRequest.SP_INTENT) TokenAndListParam intent,
            @OptionalParam(name = MedicationRequest.SP_CODE) TokenAndListParam code,
            @OptionalParam(name = MedicationRequest.SP_AUTHOREDON) DateAndListParam authoredOn,
            @Sort SortSpec sort,
            @Offset Integer offset,
            @Count Integer count) {
        return run(search(id, lastUpdated, sort, offset, count)
                .reference(MedicationRequest.SP_PATIENT, patient)
                .reference(MedicationRequest.SP_SUBJECT, subject)
                .reference(MedicationRequest.SP_ENCOUNTER, encounter)
                .token(MedicationRequest.SP_STATUS, status)
                .token(MedicationRequest.SP_INTENT, intent)
                .token(MedicationRequest.SP_CODE, code)
                .date(MedicationRequest.SP_AUTHOREDON, authoredOn));
    }
}
