package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.TextNorm;
import br.com.liberdadefamilia.markets.internal.MarketViews.GeocodeResultView;
import br.com.liberdadefamilia.markets.internal.MarketViews.PlaceView;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The polite address search (D9): switch, query check, cache, the family's daily limit, the pace, then the address
 * service. The query is never logged and never stored; the cache keeps it in memory for a day.
 */
@Service
class GeocodeService {

    private static final int QUERY_MAX = 120;

    private final MarketsProperties properties;
    private final Geocoder geocoder;
    private final GeocodingThrottle throttle;
    private final GeocodingCache cache;
    private final GeocodingLimits limits;

    GeocodeService(
            MarketsProperties properties,
            Geocoder geocoder,
            GeocodingThrottle throttle,
            GeocodingCache cache,
            GeocodingLimits limits) {
        this.properties = properties;
        this.geocoder = geocoder;
        this.throttle = throttle;
        this.cache = cache;
        this.limits = limits;
    }

    GeocodeResultView search(WorkspaceMember member, String query) {
        if (!properties.geocoding().enabled()) {
            throw MarketRuleException.geocodingOff();
        }
        String stripped = query.strip();
        if (stripped.isEmpty() || stripped.length() > QUERY_MAX) {
            throw MarketRuleException.queryInvalid();
        }
        String key = TextNorm.normalize(stripped);
        if (key.isEmpty()) {
            throw MarketRuleException.queryInvalid();
        }
        Optional<List<GeocodedPlace>> cached = cache.get(key);
        if (cached.isPresent()) {
            return view(cached.get());
        }
        if (!limits.tryConsume(member.workspaceId())) {
            throw MarketRuleException.geocodingLimit();
        }
        List<GeocodedPlace> places;
        try {
            throttle.acquire();
            places = geocoder.search(stripped);
        } catch (GeocodingBusyException e) {
            limits.refund(member.workspaceId()); // the family got no answer: the lookup is not spent
            throw e;
        } catch (GeocodingException e) {
            limits.refund(member.workspaceId());
            throw MarketRuleException.geocodingFailed();
        }
        cache.put(key, places);
        return view(places);
    }

    private static GeocodeResultView view(List<GeocodedPlace> places) {
        return new GeocodeResultView(places.stream()
                .map(place -> new PlaceView(
                        place.point().lat().toPlainString(), place.point().lon().toPlainString(), place.label()))
                .toList());
    }
}
