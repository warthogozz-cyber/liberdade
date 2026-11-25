package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.MarketKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What the Feira's routes ask and answer. Coordinates travel as decimal text ({@code "-10.00012"}), never as floating
 * point numbers.
 */
final class MarketViews {

    private MarketViews() {}

    /** Where the family's market comes from. */
    enum Origin {
        OSM,
        FAMILY
    }

    /** {@code LEFT_OSM}: the object is gone from OpenStreetMap; {@code ARCHIVED}: the family hid it. */
    enum Status {
        ACTIVE,
        LEFT_OSM,
        ARCHIVED
    }

    /** Whose link or date it is. */
    enum Source {
        FAMILY,
        OSM
    }

    /** A search: the center with at most 2 decimals, and a radius of 1 to 10 km (3 when absent). */
    record SearchRequest(
            @NotNull @Schema(example = "-10.00") String lat,
            @NotNull @Schema(example = "-50.00") String lon,

            @Nullable @Schema(nullable = true, minimum = "1", maximum = "10")
            Integer radiusKm) {}

    /** The markets around a point, in separate lists. {@code datasetDate} is the date of the OpenStreetMap data. */
    record SearchResultView(
            List<OsmItem> osm,
            List<FamilyItem> family,
            List<UnlocatedItem> unlocated,
            List<LeftItem> left,
            @Nullable @Schema(nullable = true) LocalDate datasetDate) {}

    /** A market of the OpenStreetMap extract. {@code familyId} is the family's own row, once there is one. */
    record OsmItem(
            String ref,
            MarketKind kind,
            @Nullable @Schema(nullable = true) String name,
            @Nullable @Schema(nullable = true) String brand,
            String lat,
            String lon,
            @Nullable @Schema(nullable = true) String street,
            @Nullable @Schema(nullable = true) String housenumber,
            @Nullable @Schema(nullable = true) String suburb,
            @Nullable @Schema(nullable = true) String city,
            @Nullable @Schema(nullable = true) UUID familyId) {}

    /** A market the family added, with a position. */
    record FamilyItem(
            String ref,
            String name,
            @Nullable @Schema(nullable = true) MarketKind kind,
            String lat,
            String lon) {}

    /** A market the family added without a position. */
    record UnlocatedItem(
            String ref,
            String name,
            @Nullable @Schema(nullable = true) MarketKind kind) {}

    /** A market of the family that is no longer in OpenStreetMap. */
    record LeftItem(
            String ref, @Nullable @Schema(nullable = true) String cnpj) {}

    /** The street address of an OpenStreetMap market. */
    record AddressView(
            @Nullable @Schema(nullable = true) String street,
            @Nullable @Schema(nullable = true) String housenumber,
            @Nullable @Schema(nullable = true) String suburb,
            @Nullable @Schema(nullable = true) String city) {}

    /** A website or Instagram link and whose it is; {@code date} is when the family set it, or the extract's date. */
    record LinkView(
            String value,
            Source source,
            @Nullable @Schema(nullable = true) LocalDate date) {}

    /** The flyer the family linked. */
    record FlyerView(
            String url,
            @Nullable @Schema(nullable = true) String source,
            @Nullable @Schema(nullable = true) LocalDate validUntil,
            @Nullable @Schema(nullable = true) Instant setAt) {}

    /** One market: what OpenStreetMap says about it, with the family's links on top. */
    record MarketCardView(
            String ref,
            @Nullable @Schema(nullable = true) UUID familyId,
            Origin origin,
            Status status,
            @Nullable @Schema(nullable = true) MarketKind kind,
            @Nullable @Schema(nullable = true) String name,
            @Nullable @Schema(nullable = true) String lat,
            @Nullable @Schema(nullable = true) String lon,
            @Nullable @Schema(nullable = true) AddressView address,
            @Nullable @Schema(nullable = true) String openingHours,
            @Nullable @Schema(nullable = true) String cnpj,
            @Nullable @Schema(nullable = true) LocalDate datasetDate,
            @Nullable @Schema(nullable = true) LinkView website,
            @Nullable @Schema(nullable = true) LinkView instagram,
            boolean instagramEmbeddable,
            @Nullable @Schema(nullable = true) FlyerView flyer,
            @Nullable @Schema(nullable = true) Instant updatedAt) {}

    /** The family's address: a point with 3 decimals and a name the person chose. */
    record HomeView(
            String lat,
            String lon,
            @Nullable @Schema(nullable = true) String label) {}

    /** The family's address: both coordinates with at most 3 decimals; the label has up to 40 characters. */
    record HomeRequest(
            @Nullable @Schema(nullable = true, example = "-23.123")
            String lat,

            @Nullable @Schema(nullable = true, example = "-46.123")
            String lon,

            @Nullable @Schema(nullable = true, maxLength = 40, example = "Casa")
            String label) {}

    /** An address to look for, 1 to 120 characters. */
    record GeocodeRequest(
            @NotNull @Schema(maxLength = 120, example = "Rua Exemplo 10")
            String query) {}

    /** A place the address search found, rounded to 3 decimals. */
    record PlaceView(String lat, String lon, String label) {}

    /** The places found, at most 5. */
    record GeocodeResultView(List<PlaceView> places) {}

    /** The switches the app reads to hide what is off. */
    record SettingsView(boolean geocodingEnabled, boolean instagramEnabled) {}
}
