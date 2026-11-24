package br.com.liberdadefamilia.ledger.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Statement import. The file is read on the device; the API only sees the rows the person confirmed. */
@RestController
class ImportController {

    private final ImportService service;

    ImportController(ImportService service) {
        this.service = service;
    }

    /** The category the family last used for each description of the statement. */
    @PostMapping("/v1/workspaces/{ws}/imports/category-hints")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH})
    @RequiresPlan(value = PlanKey.FAMILY, openWhileBillingOff = true)
    CategoryHintsResponse categoryHints(@Valid @RequestBody CategoryHintsRequest request, WorkspaceMember member) {
        return service.categoryHints(member, request);
    }
}
