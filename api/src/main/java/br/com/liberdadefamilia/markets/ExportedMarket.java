package br.com.liberdadefamilia.markets;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A market row as the data export carries it. {@code origin} is {@code FAMILY} or {@code OSM}; {@code kind} is the
 * enum name. An {@code OSM} row has only {@code osmRef} ({@code osm-n-123}): its name, kind and position belong to
 * OpenStreetMap and are not exported.
 */
public record ExportedMarket(
        UUID id,
        String origin,
        @Nullable String osmRef,
        @Nullable String name,
        @Nullable String kind,
        @Nullable BigDecimal lat,
        @Nullable BigDecimal lon,
        @Nullable String cnpj,
        @Nullable String website,
        @Nullable String instagram,
        @Nullable String flyerUrl,
        @Nullable String flyerSource,
        @Nullable LocalDate flyerValidUntil,
        @Nullable Instant archivedAt) {}
