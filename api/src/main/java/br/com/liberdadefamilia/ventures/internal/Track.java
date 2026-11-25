package br.com.liberdadefamilia.ventures.internal;

import java.util.List;

/** A group of models; an educational track has no models and creates no project. */
public record Track(String key, String name, String description, boolean educational, List<Template> templates) {

    public Track {
        templates = List.copyOf(templates);
    }
}
