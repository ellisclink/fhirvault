package dev.fhirvault.persistence;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import dev.fhirvault.search.DateRange;
import dev.fhirvault.search.IndexEntry;
import dev.fhirvault.search.SearchParamDef;
import dev.fhirvault.search.SearchParameterRegistry;
import dev.fhirvault.validation.ParsedReference;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a resource into search_index rows: evaluates each parameter's FHIRPath and converts the
 * elements to token, string, date or reference values. Elements a parameter can't use, like an Age
 * onset for a date, are skipped.
 */
@Component
public class SearchIndexExtractor {

    private final IFhirPath fhirPath;
    private final SearchParameterRegistry registry;

    public SearchIndexExtractor(FhirContext fhirContext, SearchParameterRegistry registry) {
        this.fhirPath = fhirContext.newFhirPath();
        this.registry = registry;
    }

    public List<IndexEntry> extract(Resource resource) {
        List<IndexEntry> entries = new ArrayList<>();
        for (SearchParamDef def : registry.forType(resource.fhirType())) {
            for (IBase element : fhirPath.evaluate(resource, def.path(), IBase.class)) {
                switch (def.type()) {
                    case TOKEN -> addTokens(def, element, entries);
                    case STRING -> addStrings(def, element, entries);
                    case DATE -> addDate(def, element, entries);
                    case REFERENCE -> addReference(def, element, entries);
                }
            }
        }
        return entries;
    }

    private static void addTokens(SearchParamDef def, IBase element, List<IndexEntry> out) {
        String name = def.name();
        if (element instanceof CodeableConcept concept) {
            concept.getCoding().forEach(c -> addCoding(name, c, out));
        } else if (element instanceof Coding coding) {
            addCoding(name, coding, out);
        } else if (element instanceof Identifier identifier) {
            if (identifier.hasValue()) {
                out.add(IndexEntry.token(name, identifier.getSystem(), identifier.getValue()));
            }
        } else if (element instanceof ContactPoint contactPoint) {
            if (contactPoint.hasValue()) {
                out.add(IndexEntry.token(name, null, contactPoint.getValue()));
            }
        } else if (element instanceof BooleanType bool) {
            if (bool.hasValue()) {
                out.add(IndexEntry.token(name, null, bool.getValueAsString()));
            }
        } else if (element instanceof PrimitiveType<?> code && code.hasValue()) {
            // Plain codes (status, gender): the binding implies the system.
            out.add(IndexEntry.token(name, null, code.getValueAsString()));
        }
    }

    private static void addCoding(String name, Coding coding, List<IndexEntry> out) {
        if (coding.hasCode()) {
            out.add(IndexEntry.token(name, coding.getSystem(), coding.getCode()));
        }
    }

    private static void addStrings(SearchParamDef def, IBase element, List<IndexEntry> out) {
        List<String> values = new ArrayList<>();
        if (element instanceof HumanName name) {
            if (name.hasFamily()) {
                values.add(name.getFamily());
            }
            name.getGiven().forEach(g -> values.add(g.getValue()));
            name.getPrefix().forEach(p -> values.add(p.getValue()));
            name.getSuffix().forEach(s -> values.add(s.getValue()));
            if (name.hasText()) {
                values.add(name.getText());
            }
        } else if (element instanceof Address address) {
            address.getLine().forEach(l -> values.add(l.getValue()));
            values.add(address.getCity());
            values.add(address.getState());
            values.add(address.getPostalCode());
            values.add(address.getCountry());
            values.add(address.getText());
        } else if (element instanceof StringType string) {
            values.add(string.getValue());
        }
        values.stream()
                .filter(v -> v != null && !v.isBlank())
                .forEach(v -> out.add(IndexEntry.string(def.name(), v)));
    }

    private static void addDate(SearchParamDef def, IBase element, List<IndexEntry> out) {
        if (element instanceof BaseDateTimeType date && date.hasValue()) {
            out.add(IndexEntry.date(def.name(), DateRange.parse(date.getValueAsString())));
        } else if (element instanceof Period period && (period.hasStart() || period.hasEnd())) {
            DateRange start = period.hasStart() ? DateRange.parse(period.getStartElement().getValueAsString()) : null;
            DateRange end = period.hasEnd() ? DateRange.parse(period.getEndElement().getValueAsString()) : null;
            out.add(IndexEntry.date(def.name(), DateRange.between(start, end)));
        }
    }

    private static void addReference(SearchParamDef def, IBase element, List<IndexEntry> out) {
        if (element instanceof Reference reference && reference.hasReference()) {
            ParsedReference parsed = ParsedReference.parse(reference.getReference());
            boolean typeMatches = def.targetType() == null || def.targetType().equals(parsed.type());
            if (parsed.kind() == ParsedReference.Kind.RELATIVE && typeMatches) {
                out.add(IndexEntry.reference(def.name(), parsed.typeAndId()));
            }
        }
    }
}
