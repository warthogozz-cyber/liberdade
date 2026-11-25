package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.GeoPoint;
import br.com.liberdadefamilia.markets.domain.MarketKind;
import br.com.liberdadefamilia.markets.domain.SearchBox;
import br.com.liberdadefamilia.markets.internal.MarketViews.FamilyItem;
import br.com.liberdadefamilia.markets.internal.MarketViews.LeftItem;
import br.com.liberdadefamilia.markets.internal.MarketViews.OsmItem;
import br.com.liberdadefamilia.markets.internal.MarketViews.SearchRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.SearchResultView;
import br.com.liberdadefamilia.markets.internal.MarketViews.UnlocatedItem;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The search by area (D5). The family sends a center with at most 2 decimals (about 1 km), so its exact position
 * never reaches the server.
 */
@Service
class MarketSearchService {

    static final int DEFAULT_RADIUS_KM = 3;
    static final int MIN_RADIUS_KM = 1;
    static final int MAX_RADIUS_KM = 10;
    private static final int SEARCH_DECIMALS = 2;

    private final OsmMarketRepository osm;
    private final MarketRepository markets;

    MarketSearchService(OsmMarketRepository osm, MarketRepository markets) {
        this.osm = osm;
        this.markets = markets;
    }

    @Transactional(readOnly = true)
    SearchResultView search(WorkspaceMember member, SearchRequest request) {
        GeoPoint center = point(request.lat(), request.lon()).requireScale(SEARCH_DECIMALS);
        int radiusKm = request.radiusKm() == null ? DEFAULT_RADIUS_KM : request.radiusKm();
        if (radiusKm < MIN_RADIUS_KM || radiusKm > MAX_RADIUS_KM) {
            throw new RadiusInvalidException();
        }
        SearchBox box = SearchBox.around(center, radiusKm);
        List<OsmItem> fromOsm = osm.inBox(member.workspaceId(), box, center.lat(), center.lon()).stream()
                .map(hit -> new OsmItem(
                        hit.ref().asText(),
                        MarketKind.valueOf(hit.kind()),
                        hit.name(),
                        hit.brand(),
                        hit.lat().toPlainString(),
                        hit.lon().toPlainString(),
                        hit.street(),
                        hit.housenumber(),
                        hit.suburb(),
                        hit.city(),
                        hit.familyId()))
                .toList();
        List<FamilyItem> family = markets.familyInBox(member.workspaceId(), box, center.lat(), center.lon()).stream()
                .map(each -> new FamilyItem(
                        each.id().toString(),
                        each.name(),
                        kind(each.kind()),
                        each.lat().toPlainString(),
                        each.lon().toPlainString()))
                .toList();
        List<UnlocatedItem> unlocated = markets.unlocated(member.workspaceId()).stream()
                .map(each -> new UnlocatedItem(each.id().toString(), each.name(), kind(each.kind())))
                .toList();
        List<LeftItem> left = markets.departed(member.workspaceId()).stream()
                .map(each -> new LeftItem(each.id().toString(), each.cnpj()))
                .toList();
        return new SearchResultView(
                fromOsm, family, unlocated, left, osm.lastDatasetDate().orElse(null));
    }

    /** A point from two decimal texts; anything that is not a number on the globe is {@code location_invalid}. */
    static GeoPoint point(String lat, String lon) {
        try {
            return new GeoPoint(new BigDecimal(lat.strip()), new BigDecimal(lon.strip()));
        } catch (IllegalArgumentException e) { // NumberFormatException is one, as is the range check of GeoPoint
            throw new LocationInvalidException();
        }
    }

    private static @Nullable MarketKind kind(@Nullable String kind) {
        return kind == null ? null : MarketKind.valueOf(kind);
    }
}
