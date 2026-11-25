package br.com.liberdadefamilia.markets.domain;

import java.text.Normalizer;
import java.util.Locale;

/** Text for comparing: no accents, lowercase, letters and digits only, single spaces. */
public final class TextNorm {

    private TextNorm() {}

    public static String normalize(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
    }
}
