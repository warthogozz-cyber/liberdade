package br.com.liberdadefamilia.markets.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.markets.internal.ReceiptViews.LookupView;
import br.com.liberdadefamilia.markets.internal.ReceiptViews.SaveView;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The receipts of the Feira. The owner and the adults register them; the young and the readers do not. */
@RestController
class ReceiptController {

    private final ReceiptLookupService lookup;
    private final ReceiptSaveService save;

    ReceiptController(ReceiptLookupService lookup, ReceiptSaveService save) {
        this.lookup = lookup;
        this.save = save;
    }

    /**
     * Saves the receipt and one price per item, in one transaction. The key travels again, because the API checks it
     * and takes the CNPJ and the month from it instead of trusting the device.
     */
    @PostMapping("/v1/workspaces/{ws}/markets/receipts")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    @ResponseStatus(HttpStatus.CREATED)
    SaveView save(@Valid @RequestBody SaveReceiptRequest request, WorkspaceMember member) {
        return save.save(member, request);
    }

    /**
     * What an NFC-e key says: its state and month, and the family's market with the seller's CNPJ. A write-free call,
     * though it is a POST: the key travels in the body, never in the address.
     */
    @PostMapping("/v1/workspaces/{ws}/markets/receipts/lookup")
    @RequiresModule(ModuleKey.MARKETS)
    @WorkspaceScoped(allow = {OWNER, ADULT})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    LookupView lookup(@Valid @RequestBody LookupRequest request, WorkspaceMember member) {
        return lookup.lookup(member.workspaceId(), request);
    }
}
