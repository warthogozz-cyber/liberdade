package br.com.liberdadefamilia.markets.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One row of the family's {@code markets}. The OpenStreetMap type and id are set only for an {@code OSM} row. */
public record MarketRow(
        UUID id,
        String origin,
        @Nullable Character osmType,
        @Nullable Long osmId,
        @Nullable String name,
        @Nullable String kind,
        @Nullable BigDecimal lat,
        @Nullable BigDecimal lon,
        @Nullable String cnpj,
        @Nullable String website,
        @Nullable Instant websiteSetAt,
        @Nullable String instagram,
        @Nullable Instant instagramSetAt,
        @Nullable String flyerUrl,
        @Nullable String flyerSource,
        @Nullable LocalDate flyerValidUntil,
        @Nullable Instant flyerSetAt,
        @Nullable Instant archivedAt) {

    public boolean isOsm() {
        return "OSM".equals(origin);
    }
}
