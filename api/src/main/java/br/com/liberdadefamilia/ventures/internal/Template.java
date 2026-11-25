package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.ventures.domain.VentureType;
import java.util.List;

/** A ready-made project model with example values and steps. */
public record Template(
        String key,
        String name,
        VentureType type,
        List<TemplateItem> initial,
        List<TemplateItem> fixed,
        List<TemplateProduct> products,
        List<String> steps) {

    public Template {
        initial = List.copyOf(initial);
        fixed = List.copyOf(fixed);
        products = List.copyOf(products);
        steps = List.copyOf(steps);
    }
}
