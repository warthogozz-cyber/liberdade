package br.com.liberdadefamilia.markets.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.markets.internal.MarketViews.InstagramEmbedView;
import br.com.liberdadefamilia.markets.internal.MarketViews.MarketCardView;
import br.com.liberdadefamilia.markets.internal.MarketViews.SearchRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.SearchResultView;
import br.com.liberdadefamilia.markets.internal.MarketViews.SettingsView;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The Feira: markets around the family. Everyone reads; the owner and the adults add, edit and hide. */
@RestController
class MarketController {

    private final MarketSearchService search;
    private final MarketCardService cards;
    private final FamilyMarketService family;
    private final InstagramEmbedService instagram;

    MarketController(
            MarketSearchService search,
            MarketCardService cards,
            FamilyMarketService family,
            InstagramEmbedService instagram) {
        this.search = search;
        this.cards = cards;
        this.family = family;
        this.instagram = instagram;
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

    /**
     * The embed of the Instagram post on the card, fetched when someone asks for it. A read, though it is a POST: the
     * answer is kept in memory, so the app calls Instagram at most once a week per post.
     */
    @PostMapping("/v1/workspaces/{ws}/markets/{ref}/instagram-embed")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    InstagramEmbedView instagramEmbed(@PathVariable String ref, WorkspaceMember member) {
        return instagram.embed(member, ref);
    }

    /** A market the family adds itself; the position is optional. */
    @PostMapping("/v1/workspaces/{ws}/markets")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    @ResponseStatus(HttpStatus.CREATED)
    MarketCardView addMarket(@Valid @RequestBody NewMarketRequest request, WorkspaceMember member) {
        return family.add(member, request);
    }

    /** The website, Instagram and flyer of the card. A null deletes the family's value. */
    @PutMapping("/v1/workspaces/{ws}/markets/{ref}/links")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    MarketCardView putLinks(
            @PathVariable String ref, @Valid @RequestBody LinksRequest request, WorkspaceMember member) {
        return family.setLinks(member, ref, request);
    }

    /** Moves a market the family owns. */
    @PutMapping("/v1/workspaces/{ws}/markets/{ref}/position")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    MarketCardView putPosition(
            @PathVariable String ref, @Valid @RequestBody PositionRequest request, WorkspaceMember member) {
        return family.setPosition(member, ref, request);
    }

    /** Hides a market; hiding it again changes nothing. */
    @PostMapping("/v1/workspaces/{ws}/markets/{ref}/archive")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    @ApiResponse(responseCode = "204", description = "Hidden")
    ResponseEntity<Void> archiveMarket(@PathVariable String ref, WorkspaceMember member) {
        family.archive(member, ref);
        return ResponseEntity.noContent().build();
    }

    /** Takes over a market that left OpenStreetMap, keeping its CNPJ, links and flyer. */
    @PostMapping("/v1/workspaces/{ws}/markets/{ref}/convert")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    MarketCardView convertMarket(
            @PathVariable String ref, @Valid @RequestBody NewMarketRequest request, WorkspaceMember member) {
        return family.convert(member, ref, request);
    }
}
