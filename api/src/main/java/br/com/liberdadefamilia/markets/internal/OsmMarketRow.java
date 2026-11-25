package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.MarketKind;
import java.math.BigDecimal;

/** One line of the extract, with the columns of {@code osm_markets}. Absent text is {@code null}. */
public record OsmMarketRow(
        char type,
        long id,
        MarketKind kind,
        String name,
        String brand,
        BigDecimal lat,
        BigDecimal lon,
        String website,
        String instagram,
        String openingHours,
        String street,
        String housenumber,
        String suburb,
        String city) {}
