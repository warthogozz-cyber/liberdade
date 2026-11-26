package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.GeoPoint;
import java.math.BigDecimal;
import java.util.List;

/** The address search of dev, e2e and the tests: one invented place, and no network. */
final class FakeGeocoder implements Geocoder {

    @Override
    public List<GeocodedPlace> search(String query) {
        return List.of(new GeocodedPlace(
                new GeoPoint(new BigDecimal("-10.000"), new BigDecimal("-50.000")), "Bairro Exemplo, Cidade Exemplo"));
    }
}
