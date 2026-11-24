package br.com.liberdadefamilia.indicators.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The public market indicators. The data is the same for every family; all four roles may read it. */
@RestController
class IndicatorController {

    private final IndicatorService service;

    IndicatorController(IndicatorService service) {
        this.service = service;
    }

    @GetMapping("/v1/workspaces/{ws}/indicators")
    @RequiresModule(ModuleKey.INDICATORS)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER, YOUTH})
    IndicatorsView listIndicators() {
        return service.list();
    }
}
