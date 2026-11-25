package br.com.liberdadefamilia.ventures.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The previews of the project figures and of the investment simulator. They write nothing, so the viewer may use them. */
@RestController
class SimulationController {

    private final SimulationService service;

    SimulationController(SimulationService service) {
        this.service = service;
    }

    @PostMapping("/v1/workspaces/{ws}/ventures/simulation")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    FiguresView simulate(@Valid @RequestBody SimulationRequest request, WorkspaceMember member) {
        return service.simulate(member, request);
    }

    @PostMapping("/v1/workspaces/{ws}/ventures/investment-simulation")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    InvestmentSimulationView simulateInvestment(
            @Valid @RequestBody InvestmentSimulationRequest request, WorkspaceMember member) {
        return service.simulateInvestment(member, request);
    }
}
