package br.com.liberdadefamilia.markets.domain;

import br.com.liberdadefamilia.markets.domain.InvalidKeyException.Reason;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A valid NFC-e access key (44 positions). The CNPJ in positions 7 to 20 may be alphanumeric. The key can open the
 * page of the receipt, so {@link #toString()} never shows it.
 */
public record NfceKey(String digits) {

    private static final Pattern SHAPE = Pattern.compile("[0-9]{6}[0-9A-Z]{12}[0-9]{26}");

    /** The IBGE codes of the 27 units of the federation. */
    private static final Map<String, String> UF = Map.ofEntries(
            Map.entry("11", "RO"),
            Map.entry("12", "AC"),
            Map.entry("13", "AM"),
            Map.entry("14", "RR"),
            Map.entry("15", "PA"),
            Map.entry("16", "AP"),
            Map.entry("17", "TO"),
            Map.entry("21", "MA"),
            Map.entry("22", "PI"),
            Map.entry("23", "CE"),
            Map.entry("24", "RN"),
            Map.entry("25", "PB"),
            Map.entry("26", "PE"),
            Map.entry("27", "AL"),
            Map.entry("28", "SE"),
            Map.entry("29", "BA"),
            Map.entry("31", "MG"),
            Map.entry("32", "ES"),
            Map.entry("33", "RJ"),
            Map.entry("35", "SP"),
            Map.entry("41", "PR"),
            Map.entry("42", "SC"),
            Map.entry("43", "RS"),
            Map.entry("50", "MS"),
            Map.entry("51", "MT"),
            Map.entry("52", "GO"),
            Map.entry("53", "DF"));

    /** Takes the key as typed or pasted (spaces and dots allowed) and checks the rules, in order. */
    public static NfceKey parse(String raw, YearMonth current) {
        String key = raw == null ? "" : raw.replace(" ", "").replace(".", "").toUpperCase(Locale.ROOT);
        if (!SHAPE.matcher(key).matches()) {
            throw new InvalidKeyException(Reason.LENGTH);
        }
        if (!UF.containsKey(key.substring(0, 2))) {
            throw new InvalidKeyException(Reason.UF);
        }
        int year = 2000 + Integer.parseInt(key.substring(2, 4));
        int month = Integer.parseInt(key.substring(4, 6));
        if (month < 1 || month > 12 || year < 2010 || YearMonth.of(year, month).isAfter(current)) {
            throw new InvalidKeyException(Reason.MONTH);
        }
        if (!Cnpj.isValid(key.substring(6, 20))) {
            throw new InvalidKeyException(Reason.CNPJ);
        }
        if (!key.startsWith("65", 20)) {
            throw new InvalidKeyException(Reason.MODEL);
        }
        if (checkDigit(key.substring(0, 43)) != key.charAt(43) - '0') {
            throw new InvalidKeyException(Reason.CHECK_DIGIT);
        }
        return new NfceKey(key);
    }

    /** Module 11: weights 2 to 9 from the right, repeating; each position is worth {@code code - 48}. */
    static int checkDigit(String first43) {
        int sum = 0;
        int weight = 2;
        for (int i = first43.length() - 1; i >= 0; i--) {
            sum += (first43.charAt(i) - 48) * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    public String uf() {
        return UF.get(digits.substring(0, 2));
    }

    public YearMonth issuedMonth() {
        return YearMonth.of(2000 + Integer.parseInt(digits.substring(2, 4)), Integer.parseInt(digits.substring(4, 6)));
    }

    public String sellerCnpj() {
        return digits.substring(6, 20);
    }

    @Override
    public String toString() {
        return "NfceKey[***]";
    }
}
