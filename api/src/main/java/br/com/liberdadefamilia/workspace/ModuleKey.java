package br.com.liberdadefamilia.workspace;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Optional;

/** The optional modules a family can switch on. Off by default; lowercase in JSON, in paths and in the database. */
public enum ModuleKey {
    PLANNING,
    INDICATORS,
    GAMIFICATION,
    VENTURES;

    /** The lowercase name, as it travels in JSON and paths and is stored. */
    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The key with this lowercase name, or empty if there is none. */
    public static Optional<ModuleKey> fromWire(String value) {
        for (ModuleKey key : values()) {
            if (key.wire().equals(value)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }
}
