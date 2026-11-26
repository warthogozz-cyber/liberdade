package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.KeyHash;
import br.com.liberdadefamilia.markets.domain.MarketKind;
import br.com.liberdadefamilia.markets.domain.NfceKey;
import br.com.liberdadefamilia.markets.domain.OsmRef;
import br.com.liberdadefamilia.markets.internal.MarketViews.Status;
import br.com.liberdadefamilia.markets.internal.OsmMarketRepository.OsmObject;
import br.com.liberdadefamilia.markets.internal.ReceiptViews.LookupMarket;
import br.com.liberdadefamilia.markets.internal.ReceiptViews.LookupMode;
import br.com.liberdadefamilia.markets.internal.ReceiptViews.LookupView;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a key says (D7): it checks the key, its {@code p} and the duplicate, then finds the family's market with the
 * CNPJ. It writes nothing, asks no state reader and never logs the key.
 */
@Service
class ReceiptLookupService {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_V2_P = 300;

    private final ReceiptRepository receipts;
    private final MarketRepository markets;
    private final OsmMarketRepository osm;
    private final Clock clock;

    ReceiptLookupService(ReceiptRepository receipts, MarketRepository markets, OsmMarketRepository osm, Clock clock) {
        this.receipts = receipts;
        this.markets = markets;
        this.osm = osm;
        this.clock = clock;
    }

    /** A key that passed every rule, with the hash the family stores in its place. */
    record ValidatedKey(NfceKey key, String keyHash) {

        @Override
        public String toString() {
            return "ValidatedKey[***]";
        }
    }

    /**
     * The key rules (D2), the {@code p} rules (D7) and the duplicate, in this order. The save repeats it, so the
     * device is never trusted for a hash or a CNPJ.
     *
     * @throws br.com.liberdadefamilia.markets.domain.InvalidKeyException when the key breaks a rule (422)
     * @throws br.com.liberdadefamilia.shared.DomainException 422 {@code receipt.key_invalid} for the {@code p}, 409 {@code receipt.duplicate}
     */
    @Transactional(readOnly = true)
    ValidatedKey validate(UUID workspaceId, LookupRequest body) {
        NfceKey key = NfceKey.parse(body.chave(), YearMonth.now(clock.withZone(SAO_PAULO)));
        if (!acceptsP(key, body.version(), body.p())) {
            throw ReceiptRuleException.keyInvalid();
        }
        String hash = KeyHash.of(workspaceId, key);
        if (receipts.existsByHash(workspaceId, hash)) {
            throw ReceiptRuleException.duplicate();
        }
        return new ValidatedKey(key, hash);
    }

    @Transactional(readOnly = true)
    LookupView lookup(UUID workspaceId, LookupRequest body) {
        ValidatedKey validated = validate(workspaceId, body);
        NfceKey key = validated.key();
        LookupMarket market = markets.findByCnpj(workspaceId, key.sellerCnpj())
                .map(this::view)
                .orElse(null);
        return new LookupView(LookupMode.MANUAL, key.uf(), key.issuedMonth().toString(), market, List.of());
    }

    /**
     * Version 1 and 0 have no {@code p}. Version 2 keeps its {@code p} (up to 300 characters, production) and it is
     * dropped here. Version 3 is exactly {@code <key>|3|1}: the offline form carries the buyer's document.
     */
    private static boolean acceptsP(NfceKey key, int version, @Nullable String p) {
        return switch (version) {
            case 0, 1 -> p == null;
            case 2 -> p != null && p.length() <= MAX_V2_P && p.startsWith(key.digits() + "|2|1|");
            case 3 -> p != null && p.equals(key.digits() + "|3|1");
            default -> false;
        };
    }

    private LookupMarket view(MarketRow row) {
        if (!row.isOsm()) {
            return new LookupMarket(
                    row.id().toString(), row.id(), row.name(), kind(row.kind()), archivedOr(row, Status.ACTIVE));
        }
        OsmRef ref = new OsmRef(row.osmType(), row.osmId());
        Optional<OsmObject> object = osm.find(ref);
        return new LookupMarket(
                ref.asText(),
                row.id(),
                object.map(OsmObject::name).orElse(null),
                object.map(each -> kind(each.kind())).orElse(null),
                archivedOr(row, object.isPresent() ? Status.ACTIVE : Status.LEFT_OSM));
    }

    private static Status archivedOr(MarketRow row, Status otherwise) {
        return row.archivedAt() != null ? Status.ARCHIVED : otherwise;
    }

    private static @Nullable MarketKind kind(@Nullable String kind) {
        return kind == null ? null : MarketKind.valueOf(kind);
    }
}
