package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.ShoppingListChanged;
import br.com.liberdadefamilia.markets.domain.ProductUnit;
import br.com.liberdadefamilia.markets.domain.TextNorm;
import br.com.liberdadefamilia.markets.internal.MarketViews.AddListItemRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.ClearedView;
import br.com.liberdadefamilia.markets.internal.MarketViews.ListItemView;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductPriceView;
import br.com.liberdadefamilia.markets.internal.MarketViews.ShoppingListView;
import br.com.liberdadefamilia.markets.internal.MarketViews.UpdateListItemRequest;
import br.com.liberdadefamilia.markets.internal.ShoppingListRepository.Added;
import br.com.liberdadefamilia.markets.internal.ShoppingListRepository.ItemRow;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The family's one shopping list (D7). It keeps no log and no audit: what is on the list is everyday business. */
@Service
class ShoppingListService {

    static final int MAX_ITEMS = 300;
    private static final Pattern DECIMAL = Pattern.compile("\\d{1,15}(\\.\\d{1,4})?");
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final ShoppingListRepository list;
    private final ProductRepository products;
    private final ProductsService productsService;
    private final ApplicationEventPublisher events;

    ShoppingListService(
            ShoppingListRepository list,
            ProductRepository products,
            ProductsService productsService,
            ApplicationEventPublisher events) {
        this.list = list;
        this.products = products;
        this.productsService = productsService;
        this.events = events;
    }

    /** What an add answered: the item, and whether it is new on the list (201) or a sum into one already there (200). */
    record AddResult(ListItemView item, boolean created) {}

    /** The items in the family's order, each with the last price at each market. */
    @Transactional(readOnly = true)
    ShoppingListView list(UUID workspaceId) {
        Map<UUID, List<ProductPriceView>> prices = productsService.pricesByProduct(workspaceId);
        return new ShoppingListView(list.items(workspaceId).stream()
                .map(row -> view(row, prices.getOrDefault(row.productId(), List.of())))
                .toList());
    }

    /**
     * Adds a product at the end of the list. A product already on the list gets the quantity added and is unticked.
     * With a new name, the family's product with the same normalized name is used, or a product in {@code UN} with no
     * price is made.
     *
     * @throws ShoppingListException when the quantity is invalid, the request names no product or two, or the list
     *     has {@value #MAX_ITEMS} items
     * @throws ProductNotFoundException when the family has no such product
     * @throws ProductRuleException when the new name is empty or longer than 80 after stripping
     */
    @Transactional
    AddResult add(UUID workspaceId, UUID userId, AddListItemRequest request) {
        if ((request.productId() == null) == (request.newProductName() == null)) {
            throw ShoppingListException.productRequired();
        }
        BigDecimal quantity = request.quantity() == null ? ONE : quantity(request.quantity());
        list.lockAdds(workspaceId);
        Optional<UUID> known;
        String name = "";
        if (request.productId() != null) {
            if (!products.exists(workspaceId, request.productId())) {
                throw new ProductNotFoundException();
            }
            known = Optional.of(request.productId());
        } else {
            name = request.newProductName().strip();
            if (name.isEmpty() || name.codePointCount(0, name.length()) > ProductsService.NAME_MAX) {
                throw ProductRuleException.nameInvalid();
            }
            known = products.findByNormalizedName(workspaceId, TextNorm.normalize(name));
        }
        if (known.isEmpty() || !list.contains(workspaceId, known.get())) {
            if (list.count(workspaceId) >= MAX_ITEMS) {
                throw ShoppingListException.full();
            }
        }
        String newName = name;
        UUID productId = known.orElseGet(() -> products.create(workspaceId, newName, ProductUnit.UN));
        Added added = list.add(workspaceId, productId, quantity, userId);
        events.publishEvent(new ShoppingListChanged(workspaceId));
        return new AddResult(one(workspaceId, added.id()), added.created());
    }

    /**
     * Changes the quantity and/or ticks or unticks the item.
     *
     * @throws ShoppingListException when the quantity is invalid
     * @throws ShoppingListException (404) when the family has no such item
     */
    @Transactional
    ListItemView update(UUID workspaceId, UUID id, UpdateListItemRequest request) {
        if (list.find(workspaceId, id).isEmpty()) {
            throw ShoppingListException.itemNotFound();
        }
        BigDecimal quantity = request.quantity() == null ? null : quantity(request.quantity());
        if (quantity != null) {
            list.setQuantity(workspaceId, id, quantity);
        }
        if (request.checked() != null) {
            list.setChecked(workspaceId, id, request.checked());
        }
        if (quantity != null || request.checked() != null) {
            events.publishEvent(new ShoppingListChanged(workspaceId));
        }
        return one(workspaceId, id);
    }

    /**
     * Sets the order. {@code ids} must be exactly the items on the list now, each once; otherwise another device
     * changed it, and the caller reloads and tries again.
     *
     * @throws ShoppingListException (409) when the ids are not the list's
     */
    @Transactional
    ShoppingListView reorder(UUID workspaceId, List<UUID> ids) {
        List<UUID> current = list.lockIds(workspaceId);
        if (ids.size() != current.size() || !new HashSet<>(ids).equals(new HashSet<>(current))) {
            throw ShoppingListException.orderStale();
        }
        for (int position = 0; position < ids.size(); position++) {
            list.setPosition(workspaceId, ids.get(position), position);
        }
        events.publishEvent(new ShoppingListChanged(workspaceId));
        return list(workspaceId);
    }

    /** @throws ShoppingListException (404) when the family has no such item */
    @Transactional
    void remove(UUID workspaceId, UUID id) {
        if (!list.delete(workspaceId, id)) {
            throw ShoppingListException.itemNotFound();
        }
        events.publishEvent(new ShoppingListChanged(workspaceId));
    }

    /** Takes the ticked items off the list. */
    @Transactional
    ClearedView clearChecked(UUID workspaceId) {
        int removed = list.deleteChecked(workspaceId);
        if (removed > 0) {
            events.publishEvent(new ShoppingListChanged(workspaceId));
        }
        return new ClearedView(removed);
    }

    private ListItemView one(UUID workspaceId, UUID id) {
        ItemRow row = list.find(workspaceId, id).orElseThrow(ShoppingListException::itemNotFound);
        return view(row, productsService.pricesByProduct(workspaceId).getOrDefault(row.productId(), List.of()));
    }

    private static ListItemView view(ItemRow row, List<ProductPriceView> prices) {
        return new ListItemView(
                row.id(),
                row.productId(),
                row.name(),
                ProductUnit.valueOf(row.unit()),
                row.quantity().stripTrailingZeros().toPlainString(),
                row.checked(),
                row.position(),
                prices);
    }

    private static BigDecimal quantity(String text) {
        if (!DECIMAL.matcher(text).matches()) {
            throw ShoppingListException.quantityInvalid();
        }
        BigDecimal quantity = new BigDecimal(text);
        if (quantity.signum() <= 0) {
            throw ShoppingListException.quantityInvalid();
        }
        return quantity;
    }
}
