package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.ProductsChanged;
import br.com.liberdadefamilia.markets.domain.ProductUnit;
import br.com.liberdadefamilia.markets.internal.MarketViews.EditProductRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductOverview;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductPriceView;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductsOverview;
import br.com.liberdadefamilia.markets.internal.PriceRepository.LatestPrice;
import br.com.liberdadefamilia.markets.internal.ProductRepository.ProductRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The family's products with their last prices, and renaming a product or changing its unit (D2, D3). */
@Service
class ProductsService {

    static final int MAX_PRODUCTS = 500;
    static final int NAME_MAX = 80;
    private static final int PRICE_SCALE = 4;

    private final ProductRepository products;
    private final PriceRepository prices;
    private final ApplicationEventPublisher events;

    ProductsService(ProductRepository products, PriceRepository prices, ApplicationEventPublisher events) {
        this.products = products;
        this.prices = prices;
        this.events = events;
    }

    /** Every product by name, up to {@value #MAX_PRODUCTS}, each with its last price at each market. */
    @Transactional(readOnly = true)
    ProductsOverview overview(UUID workspaceId) {
        List<ProductRow> rows = products.search(workspaceId, "", MAX_PRODUCTS + 1);
        boolean truncated = rows.size() > MAX_PRODUCTS;
        Map<UUID, List<ProductPriceView>> pricesByProduct = pricesByProduct(workspaceId);
        return new ProductsOverview(
                rows.stream()
                        .limit(MAX_PRODUCTS)
                        .map(row -> overviewOf(row, pricesByProduct.getOrDefault(row.id(), List.of())))
                        .toList(),
                truncated);
    }

    /** One product in the shape of the overview. */
    @Transactional(readOnly = true)
    ProductOverview one(UUID workspaceId, UUID productId) {
        ProductRow row = products.find(workspaceId, productId).orElseThrow(ProductNotFoundException::new);
        return overviewOf(row, pricesByProduct(workspaceId).getOrDefault(productId, List.of()));
    }

    /**
     * Renames the product and/or changes its unit. The prices already recorded keep the unit they were made in.
     *
     * @throws ProductNotFoundException when the family has no such product
     * @throws ProductRuleException when the name is empty or longer than {@value #NAME_MAX} after stripping
     */
    @Transactional
    ProductOverview edit(UUID workspaceId, UUID productId, EditProductRequest request) {
        if (!products.exists(workspaceId, productId)) {
            throw new ProductNotFoundException();
        }
        String name = request.name() == null ? null : request.name().strip();
        if (name != null && (name.isEmpty() || name.codePointCount(0, name.length()) > NAME_MAX)) {
            throw ProductRuleException.nameInvalid();
        }
        if (name != null || request.unit() != null) {
            products.update(workspaceId, productId, name, request.unit());
            events.publishEvent(new ProductsChanged(workspaceId));
        }
        return one(workspaceId, productId);
    }

    /** The last price of each product at each market, by product; a product with no price has no entry. */
    Map<UUID, List<ProductPriceView>> pricesByProduct(UUID workspaceId) {
        Map<UUID, List<ProductPriceView>> byProduct = new HashMap<>();
        for (LatestPrice price : prices.latestPerProductAndMarket(workspaceId)) {
            byProduct
                    .computeIfAbsent(price.productId(), id -> new ArrayList<>())
                    .add(new ProductPriceView(
                            price.marketRef(),
                            price.marketName(),
                            price.unitPrice().setScale(PRICE_SCALE).toPlainString(),
                            ProductUnit.valueOf(price.unit()),
                            price.observedOn()));
        }
        return byProduct;
    }

    private static ProductOverview overviewOf(ProductRow row, List<ProductPriceView> prices) {
        return new ProductOverview(row.id(), row.name(), ProductUnit.valueOf(row.unit()), row.gtin(), prices);
    }
}
