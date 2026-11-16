package br.com.liberdadefamilia.ledger.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import java.time.YearMonth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The month at a glance: what came in and went out, what the family has, and where the money went. */
@RestController
class DashboardController {

    private final DashboardService service;

    DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/v1/workspaces/{ws}/dashboard")
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    DashboardView dashboard(@RequestParam YearMonth month, WorkspaceMember member) {
        return service.monthOf(member, month);
    }
}
