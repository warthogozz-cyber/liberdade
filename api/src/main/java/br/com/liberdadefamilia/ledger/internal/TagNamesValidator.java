package br.com.liberdadefamilia.ledger.internal;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Checks {@link TagNames}. */
final class TagNamesValidator implements ConstraintValidator<TagNames, List<String>> {

    static final int MAX_NAME_LENGTH = 30;
    static final int MAX_NAMES = 5;

    @Override
    public boolean isValid(List<String> names, ConstraintValidatorContext context) {
        if (names == null) {
            return true;
        }
        Set<String> distinct = new HashSet<>();
        for (String name : names) {
            if (name == null || !isValidName(name)) {
                return false;
            }
            distinct.add(key(name));
        }
        return distinct.size() <= MAX_NAMES;
    }

    /** The name as stored: stripped, 1 to 30 characters (as the database counts them) and without control characters. */
    static boolean isValidName(String name) {
        String stripped = name.strip();
        int length = stripped.codePointCount(0, stripped.length());
        return length >= 1 && length <= MAX_NAME_LENGTH && stripped.codePoints().noneMatch(Character::isISOControl);
    }

    /** What makes two names the same tag: the stripped name in lower case. */
    static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
