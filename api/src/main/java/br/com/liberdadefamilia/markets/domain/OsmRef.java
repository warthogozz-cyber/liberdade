package br.com.liberdadefamilia.markets.domain;

/** An OpenStreetMap object: {@code n} (node), {@code w} (way) or {@code r} (relation) and its id. */
public record OsmRef(char type, long id) implements MarketRef {

    public OsmRef {
        if (type != 'n' && type != 'w' && type != 'r') {
            throw new IllegalArgumentException("unknown OpenStreetMap type");
        }
        if (id < 0) {
            throw new IllegalArgumentException("negative OpenStreetMap id");
        }
    }

    /** The form in paths and JSON, such as {@code osm-w-45}. */
    public String asText() {
        return "osm-" + type + "-" + id;
    }
}
