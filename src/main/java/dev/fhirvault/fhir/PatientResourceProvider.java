package dev.fhirvault.fhir;

import ca.uhn.fhir.rest.annotation.Count;
import ca.uhn.fhir.rest.annotation.Offset;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Sort;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateAndListParam;
import ca.uhn.fhir.rest.param.StringAndListParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import dev.fhirvault.search.SearchService;
import dev.fhirvault.service.ResourceService;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class PatientResourceProvider extends AbstractResourceProvider<Patient> {

    public PatientResourceProvider(ResourceService resourceService, SearchService searchService) {
        super(resourceService, searchService, Patient.class, "Patient");
    }

    @Search
    public IBundleProvider search(
            @OptionalParam(name = "_id") TokenAndListParam id,
            @OptionalParam(name = "_lastUpdated") DateAndListParam lastUpdated,
            @OptionalParam(name = Patient.SP_IDENTIFIER) TokenAndListParam identifier,
            @OptionalParam(name = Patient.SP_NAME) StringAndListParam name,
            @OptionalParam(name = Patient.SP_FAMILY) StringAndListParam family,
            @OptionalParam(name = Patient.SP_GIVEN) StringAndListParam given,
            @OptionalParam(name = Patient.SP_BIRTHDATE) DateAndListParam birthdate,
            @OptionalParam(name = Patient.SP_GENDER) TokenAndListParam gender,
            @Sort SortSpec sort,
            @Offset Integer offset,
            @Count Integer count) {
        return run(search(id, lastUpdated, sort, offset, count)
                .token(Patient.SP_IDENTIFIER, identifier)
                .string(Patient.SP_NAME, name)
                .string(Patient.SP_FAMILY, family)
                .string(Patient.SP_GIVEN, given)
                .date(Patient.SP_BIRTHDATE, birthdate)
                .token(Patient.SP_GENDER, gender));
    }
}
