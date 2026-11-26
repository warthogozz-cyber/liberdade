package br.com.liberdadefamilia.markets.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.markets.internal.MarketViews.PricesView;
import br.com.liberdadefamilia.markets.internal.MarketViews.ProductsView;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What the family has seen on its receipts: the last prices of a market and the products. Open to every role. */
@RestController
class MarketPricesController {

    private final MarketPricesService service;

    MarketPricesController(MarketPricesService service) {
        this.service = service;
    }

    /** The last price of each product at one market, by product name. */
    @GetMapping("/v1/workspaces/{ws}/markets/{ref}/prices")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    PricesView prices(@PathVariable String ref, WorkspaceMember member) {
        return service.prices(member.workspaceId(), ref);
    }

    /** The family's products by name; {@code q} keeps those whose name contains it, ignoring case and accents. */
    @GetMapping("/v1/workspaces/{ws}/markets/products")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    ProductsView products(@RequestParam(defaultValue = "") @Size(max = 80) String q, WorkspaceMember member) {
        return service.products(member.workspaceId(), q);
    }
}
