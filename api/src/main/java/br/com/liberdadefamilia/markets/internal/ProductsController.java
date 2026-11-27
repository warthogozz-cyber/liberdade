package br.com.liberdadefamilia.markets.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.markets.internal.MarketViews.EditProductRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.MergeProductsRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductOverview;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductsOverview;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The family's products with their last prices; Owner and Adult rename them, change the unit and join them. */
@RestController
class ProductsController {

    private final ProductsService products;
    private final ProductMergeService merges;

    ProductsController(ProductsService products, ProductMergeService merges) {
        this.products = products;
        this.merges = merges;
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
}
