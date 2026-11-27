package br.com.liberdadefamilia.markets.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.markets.internal.MarketViews.AddListItemRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.ClearedView;
import br.com.liberdadefamilia.markets.internal.MarketViews.ListItemView;
import br.com.liberdadefamilia.markets.internal.MarketViews.ListOrderRequest;
import br.com.liberdadefamilia.markets.internal.MarketViews.ShoppingListView;
import br.com.liberdadefamilia.markets.internal.MarketViews.UpdateListItemRequest;
import br.com.liberdadefamilia.markets.internal.ShoppingListService.AddResult;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The family's shopping list. Everyone reads it; Owner, Adult and Youth add, change, tick and reorder; only Owner and
 * Adult take an item off or clear the ticked ones, so the youth deletes nothing, not even their own items.
 */
@RestController
class ShoppingListController {

    private final ShoppingListService shoppingList;

    ShoppingListController(ShoppingListService shoppingList) {
        this.shoppingList = shoppingList;
    }

    @GetMapping("/v1/workspaces/{ws}/markets/list")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    ShoppingListView listItems(WorkspaceMember member) {
        return shoppingList.list(member.workspaceId());
    }

    /** 201 for a new item; 200 when the product was already on the list and the quantity was added to it. */
    @PostMapping("/v1/workspaces/{ws}/markets/list")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    @ApiResponse(
            responseCode = "201",
            description = "The product was not on the list; the item was added",
            content = @Content(schema = @Schema(implementation = ListItemView.class)))
    @ApiResponse(
            responseCode = "200",
            description = "The product was already on the list; the quantity was added to it",
            content = @Content(schema = @Schema(implementation = ListItemView.class)))
    ResponseEntity<ListItemView> addListItem(@Valid @RequestBody AddListItemRequest request, WorkspaceMember member) {
        AddResult result = shoppingList.add(member.workspaceId(), member.membershipId(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.item());
    }

    @PatchMapping("/v1/workspaces/{ws}/markets/list/{id}")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    ListItemView updateListItem(
            @PathVariable UUID id, @Valid @RequestBody UpdateListItemRequest request, WorkspaceMember member) {
        return shoppingList.update(member.workspaceId(), id, request);
    }

    @PutMapping("/v1/workspaces/{ws}/markets/list/order")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    ShoppingListView reorderList(@Valid @RequestBody ListOrderRequest request, WorkspaceMember member) {
        return shoppingList.reorder(member.workspaceId(), request.ids());
    }

    @DeleteMapping("/v1/workspaces/{ws}/markets/list/{id}")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    @ApiResponse(responseCode = "204", description = "Item removed")
    ResponseEntity<Void> removeListItem(@PathVariable UUID id, WorkspaceMember member) {
        shoppingList.remove(member.workspaceId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/workspaces/{ws}/markets/list/clear-checked")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    ClearedView clearCheckedItems(WorkspaceMember member) {
        return shoppingList.clearChecked(member.workspaceId());
    }
}
