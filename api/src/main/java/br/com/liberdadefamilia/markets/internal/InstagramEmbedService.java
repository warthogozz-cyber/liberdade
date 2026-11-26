package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.internal.MarketViews.InstagramEmbedView;
import br.com.liberdadefamilia.markets.internal.MarketViews.MarketCardView;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * "Ver aqui" (D10): the embed of the Instagram link the card shows. It asks only for what the card calls embeddable,
 * keeps the answer in memory, and says "unavailable" whenever there is nothing to show: switch off, a link that is not
 * embeddable, a refusal or a failure.
 */
@Service
class InstagramEmbedService {

    private final MarketCardService cards;
    private final MarketsProperties properties;
    private final InstagramOembed oembed;
    private final InstagramEmbedCache cache;

    InstagramEmbedService(
            MarketCardService cards, MarketsProperties properties, InstagramOembed oembed, InstagramEmbedCache cache) {
        this.cards = cards;
        this.properties = properties;
        this.oembed = oembed;
        this.cache = cache;
    }

    /**
     * @throws MarketNotFoundException when the family cannot see the market
     * @throws InstagramUnavailableException when there is no embed to show
     */
    InstagramEmbedView embed(WorkspaceMember member, String refText) {
        if (!properties.instagram().enabled()) {
            throw new InstagramUnavailableException();
        }
        MarketCardView card = cards.card(member, refText);
        if (!card.instagramEmbeddable() || card.instagram() == null) {
            throw new InstagramUnavailableException();
        }
        String url = card.instagram().value();
        Optional<Optional<String>> kept = cache.get(url);
        Optional<String> html;
        if (kept.isPresent()) {
            html = kept.get();
        } else {
            try {
                html = oembed.html(url);
            } catch (OembedException e) {
                throw new InstagramUnavailableException();
            }
            cache.put(url, html);
        }
        return new InstagramEmbedView(html.orElseThrow(InstagramUnavailableException::new));
    }
}
