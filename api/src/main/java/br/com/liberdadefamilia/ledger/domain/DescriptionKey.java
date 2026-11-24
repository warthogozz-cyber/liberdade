package br.com.liberdadefamilia.ledger.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The key a transaction description is compared by: no accents, lower case, no installment marks and only letters,
 * digits and single spaces. The web computes the same key with the same rules.
 */
public final class DescriptionKey {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern INSTALLMENT =
            Pattern.compile("\\b(?:parc(?:ela)?\\.?\\s*)?\\d{1,2}\\s*(?:/|de)\\s*\\d{1,2}\\b");
    private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

    private DescriptionKey() {}

    /** The key of the description; empty when there is none. */
    public static String of(@Nullable String description) {
        if (description == null) {
            return "";
        }
        String plain = MARKS.matcher(Normalizer.normalize(description, Normalizer.Form.NFD))
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
        String withoutInstallments = INSTALLMENT.matcher(plain).replaceAll("");
        return SEPARATORS.matcher(withoutInstallments).replaceAll(" ").strip();
    }
}
