package br.com.liberdadefamilia.markets.domain;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** How a route names a market: an OpenStreetMap object ({@code osm-w-45}) or the id of a family row. */
public sealed interface MarketRef permits OsmRef, FamilyRef {

    Pattern OSM = Pattern.compile("osm-([nwr])-(\\d{1,18})");
    Pattern UUID_FORM = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** The reference in this text, or empty for any other form. */
    static Optional<MarketRef> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher osm = OSM.matcher(text);
        if (osm.matches()) {
            return Optional.of(new OsmRef(osm.group(1).charAt(0), Long.parseLong(osm.group(2))));
        }
        if (UUID_FORM.matcher(text).matches()) {
            return Optional.of(new FamilyRef(UUID.fromString(text)));
        }
        return Optional.empty();
    }
}
