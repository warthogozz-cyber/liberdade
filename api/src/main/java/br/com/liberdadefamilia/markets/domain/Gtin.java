package br.com.liberdadefamilia.markets.domain;

import org.jspecify.annotations.Nullable;

/** A GS1 trade item number: 8, 12, 13 or 14 digits, the last one the check digit. */
public final class Gtin {

    private Gtin() {}

    public static boolean isValid(@Nullable String gtin) {
        if (gtin == null) {
            return false;
        }
        int length = gtin.length();
        if (length != 8 && length != 12 && length != 13 && length != 14) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < length; i++) {
            char c = gtin.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            if (i < length - 1) {
                // From the right, skipping the check digit: weight 3, then 1, then 3...
                sum += (c - '0') * ((length - 2 - i) % 2 == 0 ? 3 : 1);
            }
        }
        return (10 - sum % 10) % 10 == gtin.charAt(length - 1) - '0';
    }
}
