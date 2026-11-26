package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.FamilyRef;
import br.com.liberdadefamilia.markets.domain.MarketRef;
import br.com.liberdadefamilia.markets.domain.OsmRef;
import br.com.liberdadefamilia.markets.internal.MarketViews.PriceView;
import br.com.liberdadefamilia.markets.internal.MarketViews.PricesView;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductView;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductsView;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The prices on the market card and the family's products (D12). */
@Service
class MarketPricesService {

    static final int MAX_PRICES = 200;
    static final int MAX_PRODUCTS = 50;
    private static final int PRICE_SCALE = 4;

    private final MarketRepository markets;
    private final PriceRepository prices;
    private final ProductRepository products;

    MarketPricesService(MarketRepository markets, PriceRepository prices, ProductRepository products) {
        this.markets = markets;
        this.prices = prices;
        this.products = products;
    }

    /**
     * The last price of each product at the market. An {@code osm-…} market the family never used has no row, so no
     * prices.
     *
     * @throws MarketNotFoundException when the reference is not one, or is a family id the family does not have
     */
    @Transactional(readOnly = true)
    PricesView prices(UUID workspaceId, String refText) {
        MarketRef ref = MarketRef.parse(refText).orElseThrow(MarketNotFoundException::new);
        MarketRow row = markets.findByRef(workspaceId, ref).orElse(null);
        if (row == null) {
            return switch (ref) {
                case OsmRef ignored -> new PricesView(List.of());
                case FamilyRef ignored -> throw new MarketNotFoundException();
            };
        }
        return new PricesView(prices.latestByMarket(workspaceId, row.id(), MAX_PRICES).stream()
                .map(each -> new PriceView(
                        each.productId(),
                        each.name(),
                        each.unit(),
                        each.unitPrice().setScale(PRICE_SCALE).toPlainString(),
                        each.observedOn()))
                .toList());
    }

    @Transactional(readOnly = true)
    ProductsView products(UUID workspaceId, String q) {
        return new ProductsView(products.search(workspaceId, q, MAX_PRODUCTS).stream()
                .map(each -> new ProductView(each.id(), each.name(), each.unit(), each.gtin()))
                .toList());
    }
}
