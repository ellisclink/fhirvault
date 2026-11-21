package dev.fhirvault.audit;

import ca.uhn.fhir.rest.param.DateAndListParam;
import ca.uhn.fhir.rest.param.DateOrListParam;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.ReferenceOrListParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import dev.fhirvault.search.DateRange;
import dev.fhirvault.search.SearchRequestBuilder;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Turns AuditEvent search parameters into a repository filter. Repeated date bounds intersect, as
 * FHIR's AND rules require.
 */
public final class AuditQueryBuilder {

    private AuditQueryBuilder() {
    }

    /** Empty when the parameters can never match (e.g. patient=A AND patient=B). */
    public static Optional<AuditRepository.Filter> build(ReferenceAndListParam patient, DateAndListParam date,
                                                         TokenParam action, ReferenceParam entity,
                                                         Integer offset, Integer count) {
        Set<String> patients = new LinkedHashSet<>();
        if (patient != null) {
            for (ReferenceOrListParam or : patient.getValuesAsQueryTokens()) {
                if (or.getValuesAsQueryTokens().size() != 1) {
                    throw new InvalidRequestException("AuditEvent patient does not support OR lists");
                }
                patients.add(or.getValuesAsQueryTokens().get(0).getIdPart());
            }
        }
        if (patients.size() > 1) {
            return Optional.empty();
        }

        Instant from = null;
        Instant to = null;
        if (date != null) {
            for (DateOrListParam or : date.getValuesAsQueryTokens()) {
                if (or.getValuesAsQueryTokens().size() != 1) {
                    throw new InvalidRequestException("AuditEvent date does not support OR lists");
                }
                DateParam d = or.getValuesAsQueryTokens().get(0);
                DateRange range;
                try {
                    range = DateRange.parse(d.getValueAsString());
                } catch (IllegalArgumentException e) {
                    throw new InvalidRequestException("Invalid date: " + d.getValueAsString());
                }
                ParamPrefixEnum prefix = d.getPrefix() == null ? ParamPrefixEnum.EQUAL : d.getPrefix();
                Instant lower = switch (prefix) {
                    case GREATERTHAN_OR_EQUALS, EQUAL -> range.low();
                    case GREATERTHAN -> range.high();
                    case LESSTHAN_OR_EQUALS, LESSTHAN -> null;
                    default -> throw new InvalidRequestException("AuditEvent date supports eq, ge, gt, le and lt");
                };
                Instant upper = switch (prefix) {
                    case LESSTHAN_OR_EQUALS, EQUAL -> range.high();
                    case LESSTHAN -> range.low();
                    default -> null;
                };
                from = later(from, lower);
                to = earlier(to, upper);
            }
        }

        if (entity != null && entity.getResourceType() == null) {
            throw new InvalidRequestException("entity must be a typed reference, e.g. entity=Observation/123");
        }
        if ((offset != null && offset < 0) || (count != null && count < 0)) {
            throw new InvalidRequestException("_offset and _count must not be negative");
        }
        int size = count == null ? SearchRequestBuilder.DEFAULT_COUNT : Math.min(count, SearchRequestBuilder.MAX_COUNT);
        return Optional.of(new AuditRepository.Filter(
                patients.isEmpty() ? null : patients.iterator().next(),
                from, to,
                action == null ? null : action.getValue(),
                entity == null ? null : entity.getResourceType() + "/" + entity.getIdPart(),
                offset == null ? 0 : offset, size));
    }

    private static Instant later(Instant a, Instant b) {
        return a == null ? b : b == null ? a : (a.isAfter(b) ? a : b);
    }

    private static Instant earlier(Instant a, Instant b) {
        return a == null ? b : b == null ? a : (a.isBefore(b) ? a : b);
    }
}
