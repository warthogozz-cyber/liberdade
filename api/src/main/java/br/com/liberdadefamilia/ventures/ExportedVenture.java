package br.com.liberdadefamilia.ventures;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A project as the data export carries it. {@code type} and {@code status} are the enum names ({@code FOOD},
 * {@code ACTIVE}, ...); the lists are in the order the family sees them.
 */
public record ExportedVenture(
        UUID id,
        String name,
        String type,
        String status,
        @Nullable LocalDate startedOn,
        @Nullable LocalDate closedOn,
        String currency,
        @Nullable String templateKey,
        List<ExportedVentureItem> items,
        List<ExportedVentureProduct> products,
        List<ExportedVentureStep> steps) {}
