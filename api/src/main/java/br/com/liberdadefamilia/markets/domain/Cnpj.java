package br.com.liberdadefamilia.markets.domain;

import org.jspecify.annotations.Nullable;

/**
 * The CNPJ of the issuer, numeric or alphanumeric (IN RFB 2.229/2024): 12 positions in {@code [0-9A-Z]}, then two
 * numeric check digits. Each character is worth {@code code - 48}.
 */
public final class Cnpj {

    private static final int[] FIRST = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Cnpj() {}

    public static boolean isValid(@Nullable String cnpj) {
        if (cnpj == null || cnpj.length() != 14) {
            return false;
        }
        for (int i = 0; i < 14; i++) {
            char c = cnpj.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean letter = c >= 'A' && c <= 'Z';
            if (!(digit || (letter && i < 12))) {
                return false;
            }
        }
        if (cnpj.chars().allMatch(c -> c == cnpj.charAt(0))) {
            return false;
        }
        int first = checkDigit(cnpj, 12, FIRST);
        int second = checkDigit(cnpj, 13, SECOND);
        return cnpj.charAt(12) - '0' == first && cnpj.charAt(13) - '0' == second;
    }

    private static int checkDigit(String text, int length, int[] weights) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (text.charAt(i) - 48) * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }
}
