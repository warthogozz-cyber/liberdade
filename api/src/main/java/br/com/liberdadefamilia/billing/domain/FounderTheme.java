package br.com.liberdadefamilia.billing.domain;

import org.jspecify.annotations.Nullable;

/** The color themes a founder family may pick. */
public enum FounderTheme {
    TEAL,
    OCEAN,
    FOREST,
    PLUM;

    /** The stored value, or TEAL when there is none or it is not a theme. */
    public static FounderTheme orDefault(@Nullable String value) {
        if (value != null) {
            for (FounderTheme theme : values()) {
                if (theme.name().equals(value)) {
                    return theme;
                }
            }
        }
        return TEAL;
    }
}
