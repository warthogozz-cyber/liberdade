package br.com.liberdadefamilia.live.internal;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What a notice says: which kind of thing changed and, when it helps, which one. Never a value, a name or a note. */
public record LiveChange(String entity, @Nullable UUID id) {

    /** The data of the {@code changed} event, such as {@code {"entity":"budget"}}. */
    public String toJson() {
        return id == null ? "{\"entity\":\"" + entity + "\"}" : "{\"entity\":\"" + entity + "\",\"id\":\"" + id + "\"}";
    }
}
