package br.com.liberdadefamilia.markets.domain;

/** Invented NFC-e access keys with computed check digits. The CNPJ base is invented, never a real company. */
public final class NfceKeyFixtures {

    /** Invented issuer: root {@code 12345678}, branch {@code 0001}. */
    public static final String CNPJ_BASE = "123456780001";

    private static final int[] FIRST = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private NfceKeyFixtures() {}

    /** A valid key for the invented issuer, series 1, number 1, normal emission. */
    public static String make(String uf, String aamm) {
        return make(uf, aamm, CNPJ_BASE, 1, 1, 1);
    }

    /** {@code cnpjBase} has 12 positions ({@code [0-9A-Z]}); the CNPJ and key check digits are computed. */
    public static String make(String uf, String aamm, String cnpjBase, int serie, int number, int tpEmis) {
        String body = uf + aamm + cnpj(cnpjBase) + "65" + String.format("%03d", serie) + String.format("%09d", number)
                + tpEmis + "12345678";
        return body + NfceKey.checkDigit(body);
    }

    /** The 14-position CNPJ for a 12-position base. */
    public static String cnpj(String base12) {
        String partial = base12 + digit(base12, FIRST);
        return partial + digit(partial, SECOND);
    }

    /** The same key with the last digit changed. */
    public static String withWrongCheckDigit(String key) {
        char last = key.charAt(key.length() - 1);
        return key.substring(0, key.length() - 1) + (last == '0' ? '1' : '0');
    }

    private static int digit(String text, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (text.charAt(i) - 48) * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }
}
