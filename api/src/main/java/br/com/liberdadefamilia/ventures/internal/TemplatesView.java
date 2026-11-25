package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.ventures.domain.VentureType;
import java.util.List;

/** The tracks and the ready-made models, with the figures of each example so the screen can show it before create. */
record TemplatesView(List<TrackView> tracks) {

    record TrackView(String key, String name, String description, boolean educational, List<TemplateView> templates) {}

    record TemplateView(
            String key,
            String name,
            VentureType type,
            List<TemplateItemView> initialItems,
            List<TemplateItemView> fixedItems,
            List<TemplateProductView> products,
            List<String> steps,
            FiguresView figures) {}

    record TemplateItemView(String name, Money amount) {}

    record TemplateProductView(String name, Money unitVariableCost, Money unitPrice, int plannedUnitsMonth) {}
}
