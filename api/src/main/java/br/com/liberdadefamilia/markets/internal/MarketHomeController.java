package br.com.liberdadefamilia.markets.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.markets.internal.MarketViews.GeocodeRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.GeocodeResultView;
import br.com.liberdadefamilia.markets.internal.MarketViews.HomeRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.HomeView;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The family's address and the polite address search. */
@RestController
class MarketHomeController {

    private final MarketHomeService home;
    private final GeocodeService geocode;

    MarketHomeController(MarketHomeService home, GeocodeService geocode) {
        this.home = home;
        this.geocode = geocode;
    }

    @GetMapping("/v1/workspaces/{ws}/markets/home")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    @ApiResponse(responseCode = "204", description = "No address yet")
    ResponseEntity<HomeView> getHome(WorkspaceMember member) {
        return home.get(member).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/v1/workspaces/{ws}/markets/home")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    HomeView putHome(@Valid @RequestBody HomeRequest request, WorkspaceMember member) {
        return home.set(member, request);
    }

    @DeleteMapping("/v1/workspaces/{ws}/markets/home")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    @ApiResponse(responseCode = "204", description = "Address removed")
    ResponseEntity<Void> deleteHome(WorkspaceMember member) {
        home.clear(member);
        return ResponseEntity.noContent().build();
    }

    /** Looks an address up, slowly and rarely. A read, though it is a POST. */
    @PostMapping("/v1/workspaces/{ws}/markets/geocode")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    GeocodeResultView geocode(@Valid @RequestBody GeocodeRequest request, WorkspaceMember member) {
        return geocode.search(member, request.query());
    }
}
