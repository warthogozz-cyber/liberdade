package br.com.liberdadefamilia.markets.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.markets.internal.MarketViews.CompareView;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The shopping list at two or three markets. Open to every role. */
@RestController
class CompareController {

    private final CompareService service;

    CompareController(CompareService service) {
        this.service = service;
    }

    /**
     * The last price of each list item at the chosen markets and the total over the items that every market prices in
     * the same unit. {@code markets} is two or three different refs, comma separated; without it, only the suggestion.
     */
    @GetMapping("/v1/workspaces/{ws}/markets/compare")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    CompareView compareList(@RequestParam(required = false) @Size(max = 300) String markets, WorkspaceMember member) {
        return service.compare(member.workspaceId(), markets);
    }
}
