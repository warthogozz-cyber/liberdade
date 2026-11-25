package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.FamilyRef;
import br.com.liberdadefamilia.markets.domain.InstagramRef;
import br.com.liberdadefamilia.markets.domain.MarketKind;
import br.com.liberdadefamilia.markets.domain.MarketRef;
import br.com.liberdadefamilia.markets.domain.OsmRef;
import br.com.liberdadefamilia.markets.internal.MarketViews.AddressView;
import br.com.liberdadefamilia.markets.internal.MarketViews.FlyerView;
import br.com.liberdadefamilia.markets.internal.MarketViews.LinkView;
import br.com.liberdadefamilia.markets.internal.MarketViews.MarketCardView;
import br.com.liberdadefamilia.markets.internal.MarketViews.Origin;
import br.com.liberdadefamilia.markets.internal.MarketViews.SettingsView;
import br.com.liberdadefamilia.markets.internal.MarketViews.Source;
import br.com.liberdadefamilia.markets.internal.MarketViews.Status;
import br.com.liberdadefamilia.markets.internal.OsmMarketRepository.OsmObject;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The card of one market (D6) and the switches the app reads (D9, D10). */
@Service
class MarketCardService {

    private final MarketRepository markets;
    private final OsmMarketRepository osm;
    private final MarketsProperties properties;

    MarketCardService(MarketRepository markets, OsmMarketRepository osm, MarketsProperties properties) {
        this.markets = markets;
        this.osm = osm;
        this.properties = properties;
    }

    SettingsView settings() {
        return new SettingsView(
                properties.geocoding().enabled(), properties.instagram().enabled());
    }

    /**
     * The card for an {@code osm-…} reference or a row id.
     *
     * @throws MarketNotFoundException when the reference is not one, or names nothing the family can see
     */
    @Transactional(readOnly = true)
    MarketCardView card(WorkspaceMember member, String refText) {
        MarketRef ref = MarketRef.parse(refText).orElseThrow(MarketNotFoundException::new);
        Optional<MarketRow> row = markets.findByRef(member.workspaceId(), ref);
        OsmRef osmRef = switch (ref) {
            case OsmRef each -> each;
            case FamilyRef ignored ->
                row.filter(MarketRow::isOsm)
                        .map(each -> new OsmRef(each.osmType(), each.osmId()))
                        .orElse(null);
        };
        Optional<OsmObject> object = osmRef == null ? Optional.empty() : osm.find(osmRef);
        if (row.isEmpty() && object.isEmpty()) {
            throw new MarketNotFoundException();
        }
        LocalDate datasetDate = osm.lastDatasetDate().orElse(null);
        boolean family = row.isPresent() && !row.get().isOsm();
        Status status = row.isPresent() && row.get().archivedAt() != null
                ? Status.ARCHIVED
                : (osmRef != null && object.isEmpty() ? Status.LEFT_OSM : Status.ACTIVE);
        LinkView instagram = instagram(row.orElse(null), object.orElse(null), datasetDate);
        return new MarketCardView(
                osmRef != null ? osmRef.asText() : row.orElseThrow().id().toString(),
                row.map(MarketRow::id).orElse(null),
                family ? Origin.FAMILY : Origin.OSM,
                status,
                family
                        ? kind(row.get().kind())
                        : object.map(each -> kind(each.kind())).orElse(null),
                family ? row.get().name() : object.map(OsmObject::name).orElse(null),
                text(family ? row.get().lat() : object.map(OsmObject::lat).orElse(null)),
                text(family ? row.get().lon() : object.map(OsmObject::lon).orElse(null)),
                object.map(MarketCardService::address).orElse(null),
                object.map(OsmObject::openingHours).orElse(null),
                row.map(MarketRow::cnpj).orElse(null),
                datasetDate,
                website(row.orElse(null), object.orElse(null), datasetDate),
                instagram,
                embeddable(instagram),
                row.map(MarketCardService::flyer).orElse(null));
    }

    private boolean embeddable(@Nullable LinkView instagram) {
        if (instagram == null || !properties.instagram().enabled()) {
            return false;
        }
        return switch (InstagramRef.parse(instagram.value()).orElse(null)) {
            case InstagramRef.Post ignored -> true;
            case InstagramRef.Profile ignored -> properties.instagram().profileEmbeds();
            case null -> false;
        };
    }

    private static @Nullable LinkView website(
            @Nullable MarketRow row, @Nullable OsmObject object, @Nullable LocalDate datasetDate) {
        if (row != null && row.website() != null) {
            return new LinkView(row.website(), Source.FAMILY, date(row.websiteSetAt()));
        }
        if (object != null && object.website() != null && isWebLink(object.website())) {
            return new LinkView(object.website().strip(), Source.OSM, datasetDate);
        }
        return null;
    }

    /** The family's Instagram wins; the OpenStreetMap one shows only when it reads as a profile or post. */
    private static @Nullable LinkView instagram(
            @Nullable MarketRow row, @Nullable OsmObject object, @Nullable LocalDate datasetDate) {
        if (row != null && row.instagram() != null) {
            Optional<InstagramRef> own = InstagramRef.parse(row.instagram());
            if (own.isPresent()) {
                return new LinkView(own.get().canonicalUrl(), Source.FAMILY, date(row.instagramSetAt()));
            }
        }
        if (object != null && object.instagram() != null) {
            Optional<InstagramRef> theirs = InstagramRef.parse(object.instagram());
            if (theirs.isPresent()) {
                return new LinkView(theirs.get().canonicalUrl(), Source.OSM, datasetDate);
            }
        }
        return null;
    }

    private static @Nullable FlyerView flyer(MarketRow row) {
        return row.flyerUrl() == null
                ? null
                : new FlyerView(row.flyerUrl(), row.flyerSource(), row.flyerValidUntil(), row.flyerSetAt());
    }

    private static @Nullable AddressView address(OsmObject object) {
        if (object.street() == null
                && object.housenumber() == null
                && object.suburb() == null
                && object.city() == null) {
            return null;
        }
        return new AddressView(object.street(), object.housenumber(), object.suburb(), object.city());
    }

    /** OpenStreetMap sites are free text: only an http(s) address with a host becomes a link. */
    private static boolean isWebLink(String text) {
        try {
            URI uri = new URI(text.strip());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static @Nullable LocalDate date(@Nullable Instant instant) {
        return instant == null
                ? null
                : instant.atZone(OsmMarketRepository.SAO_PAULO).toLocalDate();
    }

    private static @Nullable MarketKind kind(@Nullable String kind) {
        return kind == null ? null : MarketKind.valueOf(kind);
    }

    private static @Nullable String text(@Nullable BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
