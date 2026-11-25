package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.MarketAdded;
import br.com.liberdadefamilia.markets.MarketEdited;
import br.com.liberdadefamilia.markets.MarketEdited.Change;
import br.com.liberdadefamilia.markets.domain.FamilyRef;
import br.com.liberdadefamilia.markets.domain.GeoPoint;
import br.com.liberdadefamilia.markets.domain.HttpsLink;
import br.com.liberdadefamilia.markets.domain.InstagramRef;
import br.com.liberdadefamilia.markets.domain.MarketRef;
import br.com.liberdadefamilia.markets.domain.OsmRef;
import br.com.liberdadefamilia.markets.internal.MarketRepository.FamilyLinks;
import br.com.liberdadefamilia.markets.internal.MarketViews.MarketCardView;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the family writes about markets (D7, D8): its own markets, their position, the links on a card, hiding a market
 * and taking over one that left OpenStreetMap. Every write answers with the card, and publishes an event with ids only.
 */
@Service
class FamilyMarketService {

    private static final int POSITION_DECIMALS = 5;
    private static final int LINK_MAX_LENGTH = 500;

    private final MarketRepository markets;
    private final OsmMarketRepository osm;
    private final MarketCardService cards;
    private final ApplicationEventPublisher events;

    FamilyMarketService(
            MarketRepository markets,
            OsmMarketRepository osm,
            MarketCardService cards,
            ApplicationEventPublisher events) {
        this.markets = markets;
        this.osm = osm;
        this.cards = cards;
        this.events = events;
    }

    @Transactional
    MarketCardView add(WorkspaceMember member, NewMarketRequest request) {
        Optional<GeoPoint> position = position(request.lat(), request.lon());
        UUID id = markets.insertFamily(
                member.workspaceId(),
                member.membershipId(),
                request.name().strip(),
                request.kind(),
                position.orElse(null));
        events.publishEvent(new MarketAdded(member.workspaceId(), id, member.userId()));
        return cards.card(member, id.toString());
    }

    @Transactional
    MarketCardView setLinks(WorkspaceMember member, String refText, LinksRequest request) {
        FamilyLinks links = links(request);
        UUID id = rowToWrite(member, ref(refText));
        markets.updateLinks(member.workspaceId(), id, member.membershipId(), links);
        events.publishEvent(new MarketEdited(member.workspaceId(), id, member.userId(), Change.LINKS));
        return cards.card(member, id.toString());
    }

    @Transactional
    MarketCardView setPosition(WorkspaceMember member, String refText, PositionRequest request) {
        GeoPoint position = position(request.lat(), request.lon()).orElseThrow(MarketRuleException::positionInvalid);
        MarketRow row = existing(member, ref(refText)).orElseThrow(MarketRuleException::notFamily);
        if (row.isOsm()) {
            throw MarketRuleException.notFamily();
        }
        markets.updatePosition(member.workspaceId(), row.id(), member.membershipId(), position);
        events.publishEvent(new MarketEdited(member.workspaceId(), row.id(), member.userId(), Change.POSITION));
        return cards.card(member, row.id().toString());
    }

    /** Hides the market, creating the family row first for an OpenStreetMap one that has none. */
    @Transactional
    void archive(WorkspaceMember member, String refText) {
        UUID id = rowToWrite(member, ref(refText));
        if (markets.archive(member.workspaceId(), id, member.membershipId())) {
            events.publishEvent(new MarketEdited(member.workspaceId(), id, member.userId(), Change.ARCHIVE));
        }
    }

    /** Takes over a row whose object is no longer in OpenStreetMap: the CNPJ, links and flyer stay, nothing is copied. */
    @Transactional
    MarketCardView convert(WorkspaceMember member, String refText, NewMarketRequest request) {
        Optional<GeoPoint> position = position(request.lat(), request.lon());
        MarketRow row = existing(member, ref(refText)).orElseThrow(MarketRuleException::notLeft);
        boolean left = row.isOsm()
                && row.archivedAt() == null
                && osm.find(new OsmRef(row.osmType(), row.osmId())).isEmpty();
        if (!left) {
            throw MarketRuleException.notLeft();
        }
        markets.convertToFamily(
                member.workspaceId(),
                row.id(),
                member.membershipId(),
                request.name().strip(),
                request.kind(),
                position.orElse(null));
        events.publishEvent(new MarketEdited(member.workspaceId(), row.id(), member.userId(), Change.CONVERT));
        return cards.card(member, row.id().toString());
    }

    /** The id of the row a write goes to: an OpenStreetMap object gets its row on the first write (D4). */
    private UUID rowToWrite(WorkspaceMember member, MarketRef ref) {
        return switch (ref) {
            case OsmRef osmRef -> markets.ensureOsmRow(member.workspaceId(), osmRef, member.membershipId());
            case FamilyRef family ->
                markets.findByRef(member.workspaceId(), family)
                        .map(MarketRow::id)
                        .orElseThrow(MarketNotFoundException::new);
        };
    }

    /**
     * The family row for this reference. Empty for an OpenStreetMap object that is in the extract and has no row yet;
     * nothing is created.
     *
     * @throws MarketNotFoundException when there is neither a row nor an object
     */
    private Optional<MarketRow> existing(WorkspaceMember member, MarketRef ref) {
        Optional<MarketRow> row = markets.findByRef(member.workspaceId(), ref);
        if (row.isEmpty()
                && (!(ref instanceof OsmRef osmRef) || osm.find(osmRef).isEmpty())) {
            throw new MarketNotFoundException();
        }
        return row;
    }

    private static MarketRef ref(String text) {
        return MarketRef.parse(text).orElseThrow(MarketNotFoundException::new);
    }

    /** Both coordinates, or neither; at most 5 decimals. */
    private static Optional<GeoPoint> position(@Nullable String lat, @Nullable String lon) {
        if (lat == null && lon == null) {
            return Optional.empty();
        }
        if (lat == null || lon == null) {
            throw MarketRuleException.positionInvalid();
        }
        return Optional.of(MarketSearchService.point(lat, lon).requireScale(POSITION_DECIMALS));
    }

    /** The links in their stored form. Nothing is written when one is bad. */
    private static FamilyLinks links(LinksRequest request) {
        String website = https(request.website());
        String flyerUrl = https(request.flyerUrl());
        String instagram = null;
        if (request.instagram() != null) {
            instagram = InstagramRef.parse(request.instagram())
                    .map(InstagramRef::canonicalUrl)
                    .orElseThrow(MarketRuleException::instagramInvalid);
        }
        String flyerSource =
                request.flyerSource() == null ? null : request.flyerSource().strip();
        if (flyerUrl != null && (flyerSource == null || flyerSource.isEmpty())) {
            throw MarketRuleException.flyerSourceRequired();
        }
        return new FamilyLinks(website, instagram, flyerUrl, flyerSource, request.flyerValidUntil());
    }

    private static @Nullable String https(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        return HttpsLink.parse(raw, LINK_MAX_LENGTH).map(URI::toString).orElseThrow(MarketRuleException::linkInvalid);
    }
}
