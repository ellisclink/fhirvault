package dev.fhirvault.search;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * FHIR string search ignores case and accents, so "pena" finds "Peña". Indexed values and search terms
 * are normalized the same way.
 */
public final class StringNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private StringNormalizer() {
    }

    public static String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }
}
