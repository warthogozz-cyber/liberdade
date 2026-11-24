package br.com.liberdadefamilia.planning.domain;

import java.util.Arrays;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The fixed set of icons a goal can show, identified by a lowercase key. */
public enum GoalIcon {
    SAVINGS("savings"),
    HOME("home"),
    CAR("car"),
    TRAVEL("travel"),
    EDUCATION("education"),
    HEALTH("health"),
    GIFT("gift"),
    PARTY("party"),
    TECH("tech"),
    OTHER("other");

    private final String key;

    GoalIcon(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The icon with exactly this key (case-sensitive), or empty for a null or unknown key. */
    public static Optional<GoalIcon> fromKey(@Nullable String key) {
        return Arrays.stream(values()).filter(icon -> icon.key.equals(key)).findFirst();
    }
}
