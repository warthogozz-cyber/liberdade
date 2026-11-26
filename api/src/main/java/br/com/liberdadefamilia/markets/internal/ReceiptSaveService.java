package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.MarketEdited;
import br.com.liberdadefamilia.markets.MarketEdited.Change;
import br.com.liberdadefamilia.markets.ReceiptAdded;
import br.com.liberdadefamilia.markets.domain.FamilyRef;
import br.com.liberdadefamilia.markets.domain.MarketKind;
import br.com.liberdadefamilia.markets.domain.MarketRef;
import br.com.liberdadefamilia.markets.domain.NfceKey;
import br.com.liberdadefamilia.markets.domain.OsmRef;
import br.com.liberdadefamilia.markets.domain.ProductLookup;
import br.com.liberdadefamilia.markets.domain.ProductMatcher;
import br.com.liberdadefamilia.markets.domain.ProductMatcher.Existing;
import br.com.liberdadefamilia.markets.domain.ProductUnit;
import br.com.liberdadefamilia.markets.domain.TextNorm;
import br.com.liberdadefamilia.markets.internal.ReceiptLookupService.ValidatedKey;
import br.com.liberdadefamilia.markets.internal.ReceiptViews.SaveView;
import br.com.liberdadefamilia.markets.internal.SaveReceiptRequest.MarketChoice;
import br.com.liberdadefamilia.markets.internal.SaveReceiptRequest.NewMarketChoice;
import br.com.liberdadefamilia.markets.internal.SaveReceiptRequest.ReceiptItem;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a receipt and its prices in one transaction (D9, D10): the market (made or given the CNPJ when needed), the
 * receipt, the products and aliases and one price per item. Nothing of the receipt is logged: not the key, the CNPJ,
 * a name or a price.
 */
@Service
class ReceiptSaveService {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Pattern DECIMAL = Pattern.compile("\\d{1,15}(\\.\\d{1,4})?");
    private static final int MAX_ITEMS = 200;
    private static final int MAX_NAME = 80;
    private static final String MODE = "MANUAL";
    private static final String RECEIPT_UNIQUE = "receipts_workspace_id_key_hash_key";
    private static final String CNPJ_UNIQUE = "markets_cnpj_once";

    private final ReceiptLookupService lookup;
    private final ReceiptRepository receipts;
    private final MarketRepository markets;
    private final FamilyMarketService familyMarkets;
    private final ProductRepository products;
    private final PriceRepository prices;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReceiptSaveService(
            ReceiptLookupService lookup,
            ReceiptRepository receipts,
            MarketRepository markets,
            FamilyMarketService familyMarkets,
            ProductRepository products,
            PriceRepository prices,
            ApplicationEventPublisher events,
            Clock clock) {
        this.lookup = lookup;
        this.receipts = receipts;
        this.markets = markets;
        this.familyMarkets = familyMarkets;
        this.products = products;
        this.prices = prices;
        this.events = events;
        this.clock = clock;
    }

    /** An item that passed the shape rules. {@code productId} or {@code name} is set, never both. */
    private record Line(
            @Nullable UUID productId,
            @Nullable String name,
            ProductUnit unit,
            @Nullable BigDecimal quantity,
            BigDecimal unitPrice) {}

    /** The market of the receipt and the reference the answer gives back. */
    private record Target(UUID id, String ref) {}

    /**
     * @throws br.com.liberdadefamilia.shared.DomainException for every rule the request breaks; nothing is written
     */
    @Transactional
    SaveView save(WorkspaceMember member, SaveReceiptRequest body) {
        UUID workspaceId = member.workspaceId();
        ValidatedKey validated =
                lookup.validate(workspaceId, new LookupRequest(body.chave(), body.version(), body.p()));
        NfceKey key = validated.key();
        String keyHash = validated.keyHash();

        if ((body.market() == null) == (body.newMarket() == null)) {
            throw ReceiptRuleException.marketRequired();
        }
        LocalDate issuedOn = issuedOn(body.issuedOn(), key);
        List<Line> lines = lines(body.items());

        receipts.lockKey(workspaceId, keyHash);
        if (receipts.existsByHash(workspaceId, keyHash)) {
            throw ReceiptRuleException.duplicate();
        }
        try {
            return write(member, key, keyHash, issuedOn, body, lines);
        } catch (DuplicateKeyException e) {
            throw conflict(e);
        }
    }

    private SaveView write(
            WorkspaceMember member,
            NfceKey key,
            String keyHash,
            LocalDate issuedOn,
            SaveReceiptRequest body,
            List<Line> lines) {
        UUID workspaceId = member.workspaceId();
        Target market = body.market() != null
                ? existingMarket(member, body.market(), key.sellerCnpj())
                : newMarket(member, body.newMarket(), key.sellerCnpj());
        UUID receiptId =
                receipts.insert(workspaceId, market.id(), keyHash, issuedOn, MODE, lines.size(), member.membershipId());
        for (Line line : lines) {
            UUID productId = productOf(workspaceId, market.id(), line);
            prices.insert(
                    workspaceId,
                    market.id(),
                    productId,
                    receiptId,
                    issuedOn,
                    line.unit().name(),
                    line.quantity(),
                    line.unitPrice(),
                    member.membershipId());
        }
        events.publishEvent(new ReceiptAdded(workspaceId, receiptId, member.userId(), MODE, lines.size()));
        return new SaveView(receiptId, market.ref(), lines.size());
    }

    /** The same month as the key, and not after today in São Paulo. */
    private LocalDate issuedOn(@Nullable LocalDate issuedOn, NfceKey key) {
        LocalDate today = clock.instant().atZone(SAO_PAULO).toLocalDate();
        if (issuedOn == null || !YearMonth.from(issuedOn).equals(key.issuedMonth()) || issuedOn.isAfter(today)) {
            throw ReceiptRuleException.dateInvalid();
        }
        return issuedOn;
    }

    /** All the shape rules of the items, before anything is written. */
    private static List<Line> lines(@Nullable List<ReceiptItem> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw ReceiptRuleException.itemsInvalid();
        }
        List<Line> lines = new ArrayList<>(items.size());
        for (ReceiptItem item : items) {
            if (item == null || item.unit() == null || (item.productId() == null) == (item.newProductName() == null)) {
                throw ReceiptRuleException.itemsInvalid();
            }
            String name =
                    item.newProductName() == null ? null : item.newProductName().strip();
            if (name != null
                    && (name.isEmpty()
                            || name.length() > MAX_NAME
                            || TextNorm.normalize(name).isEmpty())) {
                throw ReceiptRuleException.itemsInvalid();
            }
            BigDecimal quantity =
                    item.quantity() == null ? null : decimal(item.quantity(), ReceiptRuleException::quantityInvalid);
            BigDecimal price = decimal(item.unitPrice(), ReceiptRuleException::priceInvalid);
            lines.add(new Line(item.productId(), name, item.unit(), quantity, price));
        }
        return lines;
    }

    /** Up to 15 digits and 4 places, above zero. A fifth place is an error, not a rounding. */
    private static BigDecimal decimal(@Nullable String text, java.util.function.Supplier<ReceiptRuleException> error) {
        if (text == null || !DECIMAL.matcher(text).matches()) {
            throw error.get();
        }
        BigDecimal value = new BigDecimal(text);
        if (value.signum() <= 0) {
            throw error.get();
        }
        return value;
    }

    private Target existingMarket(WorkspaceMember member, MarketChoice choice, String cnpj) {
        UUID workspaceId = member.workspaceId();
        if (choice.ref() == null) {
            throw ReceiptRuleException.marketRequired();
        }
        MarketRef ref = MarketRef.parse(choice.ref()).orElseThrow(MarketNotFoundException::new);
        UUID id = switch (ref) {
            case OsmRef osm -> markets.ensureOsmRow(workspaceId, osm, member.membershipId());
            case FamilyRef family ->
                markets.findByRef(workspaceId, family).map(MarketRow::id).orElseThrow(MarketNotFoundException::new);
        };
        MarketRow row = markets.findByRef(workspaceId, new FamilyRef(id)).orElseThrow(MarketNotFoundException::new);
        if (row.cnpj() != null) {
            if (!row.cnpj().equals(cnpj)) {
                throw ReceiptRuleException.marketCnpjDiffers();
            }
        } else {
            giveCnpj(member, id, cnpj);
        }
        return new Target(id, ref instanceof OsmRef osm ? osm.asText() : id.toString());
    }

    /** The market has none yet: it gets the key's, unless another market already has it. */
    private void giveCnpj(WorkspaceMember member, UUID marketId, String cnpj) {
        UUID workspaceId = member.workspaceId();
        Optional<MarketRow> owner = markets.findByCnpj(workspaceId, cnpj);
        if (owner.isPresent() && !owner.get().id().equals(marketId)) {
            throw MarketRuleException.cnpjTaken();
        }
        if (markets.setCnpjIfMissing(workspaceId, marketId, member.membershipId(), cnpj)) {
            events.publishEvent(new MarketEdited(workspaceId, marketId, member.userId(), Change.CNPJ));
            return;
        }
        // Someone gave it a CNPJ since we read it: fine only if it is this one.
        MarketRow now =
                markets.findByRef(workspaceId, new FamilyRef(marketId)).orElseThrow(MarketNotFoundException::new);
        if (!cnpj.equals(now.cnpj())) {
            throw ReceiptRuleException.marketCnpjDiffers();
        }
    }

    private Target newMarket(WorkspaceMember member, NewMarketChoice choice, String cnpj) {
        String name = choice.name() == null ? "" : choice.name().strip();
        MarketKind kind = choice.kind();
        if (name.isEmpty() || name.length() > MAX_NAME || kind == null) {
            throw ReceiptRuleException.marketRequired();
        }
        if (markets.findByCnpj(member.workspaceId(), cnpj).isPresent()) {
            throw MarketRuleException.cnpjTaken();
        }
        UUID id = familyMarkets.addFromReceipt(member, name, kind, cnpj);
        return new Target(id, id.toString());
    }

    /** The product of the line: the family's own, or the one a new name stands for (D11), made when there is none. */
    private UUID productOf(UUID workspaceId, UUID marketId, Line line) {
        if (line.productId() != null) {
            if (!products.exists(workspaceId, line.productId())) {
                throw ReceiptRuleException.productUnknown();
            }
            return line.productId();
        }
        String norm = TextNorm.normalize(line.name());
        ProductLookup known = new ProductLookup() {
            @Override
            public Optional<UUID> byGtin(String gtin) {
                return products.byGtin(workspaceId, gtin);
            }

            @Override
            public Optional<UUID> byAlias(String descriptionNorm) {
                return products.byAlias(workspaceId, marketId, descriptionNorm);
            }
        };
        if (ProductMatcher.match(null, norm, known) instanceof Existing existing) {
            return existing.productId();
        }
        UUID created = products.create(workspaceId, line.name(), line.unit());
        if (products.addAlias(workspaceId, marketId, norm, created)) {
            return created;
        }
        // Another save named it first, between the lookup and the alias: use its product, drop ours.
        products.discard(workspaceId, created);
        return products.byAlias(workspaceId, marketId, norm).orElseThrow();
    }

    /**
     * The unique violations that mean something to the person, by the name of the constraint. The cause is not kept:
     * its message carries the hash or the CNPJ.
     */
    private static RuntimeException conflict(DuplicateKeyException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains(RECEIPT_UNIQUE)) {
            return ReceiptRuleException.duplicate();
        }
        if (message.contains(CNPJ_UNIQUE)) {
            return MarketRuleException.cnpjTaken();
        }
        return e;
    }
}
