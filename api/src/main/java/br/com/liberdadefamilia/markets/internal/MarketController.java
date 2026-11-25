package br.com.liberdadefamilia.markets.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.markets.internal.MarketViews.MarketCardView;
import br.com.liberdadefamilia.markets.internal.MarketViews.SearchRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.SearchResultView;
import br.com.liberdadefamilia.markets.internal.MarketViews.SettingsView;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The Feira: markets around the family. Everyone reads; the routes that write come with the next tasks. */
@RestController
class MarketController {

    private final MarketSearchService search;
    private final MarketCardService cards;

    MarketController(MarketSearchService search, MarketCardService cards) {
        this.search = search;
        this.cards = cards;
    }

    /** The markets in a box around a point the app rounded to 2 decimals. A read, though it is a POST. */
    @PostMapping("/v1/workspaces/{ws}/markets/search")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    SearchResultView searchMarkets(@Valid @RequestBody SearchRequest request, WorkspaceMember member) {
        return search.search(member, request);
    }

    @GetMapping("/v1/workspaces/{ws}/markets/settings")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    SettingsView getMarketsSettings() {
        return cards.settings();
    }

    /** One market by {@code osm-<type>-<id>} or by the id of the family's row. */
    @GetMapping("/v1/workspaces/{ws}/markets/{ref}")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    MarketCardView getMarket(@PathVariable String ref, WorkspaceMember member) {
        return cards.card(member, ref);
    }
}
