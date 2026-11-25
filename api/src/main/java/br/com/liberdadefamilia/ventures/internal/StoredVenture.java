package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.ventures.domain.VentureStatus;
import br.com.liberdadefamilia.ventures.domain.VentureType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A project as it is stored, with its lines in position order. Amounts are as the database holds them. */
record StoredVenture(
        UUID id,
        String name,
        VentureType type,
        @Nullable String templateKey,
        VentureStatus status,
        @Nullable LocalDate startedOn,
        @Nullable LocalDate closedOn,
        String currency,
        long version,
        List<Item> initialItems,
        List<Item> fixedItems,
        List<Product> products,
        List<Step> steps) {

    StoredVenture {
        initialItems = List.copyOf(initialItems);
        fixedItems = List.copyOf(fixedItems);
        products = List.copyOf(products);
        steps = List.copyOf(steps);
    }

    record Item(UUID id, String name, BigDecimal amount, boolean isExample) {}

    record Product(
            UUID id,
            String name,
            BigDecimal unitVariableCost,
            BigDecimal unitPrice,
            int plannedUnitsMonth,
            boolean isExample) {}

    record Step(
            UUID id, String title, int position, @Nullable Instant doneAt) {}

    boolean hasExamples() {
        return initialItems.stream().anyMatch(Item::isExample)
                || fixedItems.stream().anyMatch(Item::isExample)
                || products.stream().anyMatch(Product::isExample);
    }
}
