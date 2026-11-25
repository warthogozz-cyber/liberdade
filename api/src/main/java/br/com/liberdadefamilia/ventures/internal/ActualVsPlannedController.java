package br.com.liberdadefamilia.ventures.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

import br.com.liberdadefamilia.ledger.LedgerFigures;
import br.com.liberdadefamilia.ventures.domain.ActualVsPlanned;
import br.com.liberdadefamilia.ventures.domain.VentureFigures;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** A project's real months against its plan. It reads only; everyone but the youth may see it. */
@RestController
class ActualVsPlannedController {

    private final VentureRepository ventures;
    private final VentureService service;
    private final LedgerFigures ledger;
    private final Clock clock;

    ActualVsPlannedController(VentureRepository ventures, VentureService service, LedgerFigures ledger, Clock clock) {
        this.ventures = ventures;
        this.service = service;
        this.ledger = ledger;
        this.clock = clock;
    }

    /**
     * The months, the totals and the real payback of the project, the current month being the one in the family's
     * time zone.
     *
     * @throws VentureNotFoundException when the family has no such project
     */
    @GetMapping("/v1/workspaces/{ws}/ventures/{id}/actual-vs-planned")
    @RequiresModule(ModuleKey.VENTURES)
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    ActualVsPlannedView getActualVsPlanned(@PathVariable UUID id, WorkspaceMember member) {
        StoredVenture venture = ventures.find(member.workspaceId(), id).orElseThrow(VentureNotFoundException::new);
        VentureFigures planned = service.figuresOf(venture);
        var result = ActualVsPlanned.compute(
                planned,
                venture.startedOn(),
                venture.closedOn(),
                venture.status(),
                ledger.ventureMonths(member.workspaceId(), id),
                YearMonth.from(LocalDate.ofInstant(clock.instant(), member.timezone())));
        return ActualVsPlannedView.of(result, planned, Currency.getInstance(venture.currency()));
    }
}
