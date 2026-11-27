package br.com.liberdadefamilia.markets.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.markets.internal.MarketViews.DismissPairRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.EditProductRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.MergeProductsRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductOverview;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductsOverview;
import br.com.liberdadefamilia.markets.internal.MarketViews.SuggestionsView;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The family's products with their last prices; Owner and Adult rename them, change the unit and join them. */
@RestController
class ProductsController {

    private final ProductsService products;
    private final ProductMergeService merges;
    private final ProductSuggestionsService suggestions;

    ProductsController(ProductsService products, ProductMergeService merges, ProductSuggestionsService suggestions) {
        this.products = products;
        this.merges = merges;
        this.suggestions = suggestions;
    }

    @GetMapping("/v1/workspaces/{ws}/markets/products/overview")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    ProductsOverview overview(WorkspaceMember member) {
        return products.overview(member.workspaceId());
    }

    @PatchMapping("/v1/workspaces/{ws}/markets/products/{id}")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    ProductOverview edit(
            @PathVariable UUID id, @Valid @RequestBody EditProductRequest request, WorkspaceMember member) {
        return products.edit(member.workspaceId(), id, request);
    }

    /** Folds {@code removeId} into the product in the path, which stays. */
    @PostMapping("/v1/workspaces/{ws}/markets/products/{keepId}/merge")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    ProductOverview merge(
            @PathVariable UUID keepId, @Valid @RequestBody MergeProductsRequest request, WorkspaceMember member) {
        return merges.merge(member.workspaceId(), member.userId(), keepId, request.removeId());
    }

    /** Pairs of products that may be the same one; the app shows the buttons to Owner and Adult only. */
    @GetMapping("/v1/workspaces/{ws}/markets/products/suggestions")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    SuggestionsView suggestions(WorkspaceMember member) {
        return suggestions.suggestions(member.workspaceId());
    }

    /** "These two are not the same": the pair is not suggested again. */
    @PostMapping("/v1/workspaces/{ws}/markets/products/dismissals")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    void dismissPair(@Valid @RequestBody DismissPairRequest request, WorkspaceMember member) {
        suggestions.dismiss(member.workspaceId(), request.a(), request.b());
    }
}
