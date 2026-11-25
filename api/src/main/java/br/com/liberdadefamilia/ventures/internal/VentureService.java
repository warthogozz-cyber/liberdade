package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.ventures.VentureChanged;
import br.com.liberdadefamilia.ventures.VentureClosed;
import br.com.liberdadefamilia.ventures.VentureDeleted;
import br.com.liberdadefamilia.ventures.domain.CostLine;
import br.com.liberdadefamilia.ventures.domain.ProductLine;
import br.com.liberdadefamilia.ventures.domain.StatusTransition;
import br.com.liberdadefamilia.ventures.domain.VentureCalculator;
import br.com.liberdadefamilia.ventures.domain.VentureFigures;
import br.com.liberdadefamilia.ventures.domain.VentureStatus;
import br.com.liberdadefamilia.ventures.domain.VentureType;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The family's extra-income projects: create from a model or blank, edit, change status, delete, and the Grátis
 * limit (D5 to D7). Only closing and deleting are audited.
 */
@Service
class VentureService {

    /** The projects outside CLOSED a family on the Grátis plan may have while selling is on (D7). */
    static final int FREE_ACTIVE_VENTURES = 1;

    private static final String BRL = "BRL";

    private final VentureRepository ventures;
    private final Plans plans;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    VentureService(VentureRepository ventures, Plans plans, ApplicationEventPublisher events, Clock clock) {
        this.ventures = ventures;
        this.plans = plans;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    VenturesView list(WorkspaceMember member) {
        UUID workspaceId = member.workspaceId();
        List<VentureSummary> summaries =
                ventures.list(workspaceId).stream().map(VentureService::summary).toList();
        return new VenturesView(summaries, canManage(member) && limitReached(workspaceId));
    }

    @Transactional(readOnly = true)
    VentureView get(WorkspaceMember member, UUID id) {
        return view(ventures.find(member.workspaceId(), id).orElseThrow(VentureNotFoundException::new));
    }

    /** The tracks and models with the figures of each example, in reais. */
    TemplatesView templates() {
        Currency brl = Currency.getInstance(BRL);
        return new TemplatesView(TemplateCatalog.tracks().stream()
                .map(track -> new TemplatesView.TrackView(
                        track.key(),
                        track.name(),
                        track.description(),
                        track.educational(),
                        track.templates().stream()
                                .map(template -> templateView(template, brl))
                                .toList()))
                .toList());
    }

    /**
     * Creates the project in the family's base currency, from a model or blank. A family whose currency is not BRL
     * gets the model's type and steps but none of its values, which are in reais.
     *
     * @throws VentureRejectedException when the name, type or model is wrong
     * @throws VentureLimitReachedException when the Grátis limit stops it
     */
    @Transactional
    VentureView create(WorkspaceMember member, CreateVentureRequest request) {
        UUID workspaceId = member.workspaceId();
        VentureValidation.projectName(request.name());
        Template template = null;
        VentureType type = request.type();
        if (request.templateKey() != null) {
            template = TemplateCatalog.template(request.templateKey())
                    .orElseThrow(VentureRejectedException::templateUnknown);
            type = template.type();
        }
        if (type == null) {
            throw VentureRejectedException.invalid("Escolha o tipo do projeto.");
        }
        String currency = member.baseCurrency().getCurrencyCode();
        ventures.lockFamily(workspaceId);
        if (limitReached(workspaceId)) {
            throw new VentureLimitReachedException();
        }
        UUID id = ventures.insert(
                workspaceId,
                request.name(),
                type,
                template == null ? null : template.key(),
                currency,
                member.membershipId());
        List<String> steps = TemplateCatalog.BLANK_STEPS;
        if (template != null) {
            steps = template.steps();
            if (BRL.equals(currency)) {
                copyValues(workspaceId, id, template, currency);
            }
        }
        for (int i = 0; i < steps.size(); i++) {
            ventures.insertStep(workspaceId, id, steps.get(i), i);
        }
        events.publishEvent(new VentureChanged(workspaceId, id));
        return view(ventures.find(workspaceId, id).orElseThrow());
    }

    /**
     * Replaces the project's name, type, start day, items and products. A line the caller keeps by id stays an
     * example only if nothing in it changed.
     *
     * @throws VentureNotFoundException when the family has no such project
     * @throws VentureRejectedException when a field is wrong or the version is not the project's
     */
    @Transactional
    VentureView update(WorkspaceMember member, UUID id, UpdateVentureRequest request) {
        UUID workspaceId = member.workspaceId();
        StoredVenture stored = ventures.find(workspaceId, id).orElseThrow(VentureNotFoundException::new);
        if (request.version() != stored.version()) {
            throw VentureRejectedException.versionConflict();
        }
        VentureValidation.projectName(request.name());
        VentureValidation.itemCount(request.initialItems());
        VentureValidation.itemCount(request.fixedItems());
        VentureValidation.productCount(request.products());
        request.initialItems().forEach(VentureService::check);
        request.fixedItems().forEach(VentureService::check);
        request.products().forEach(VentureService::check);
        checkStartedOn(stored.status(), stored.closedOn(), request.startedOn(), today(member));
        if (!ventures.update(
                workspaceId,
                id,
                request.name(),
                request.type(),
                request.startedOn(),
                request.version(),
                clock.instant())) {
            throw VentureRejectedException.versionConflict();
        }
        ventures.clearLines(workspaceId, id);
        writeItems(
                workspaceId,
                id,
                VentureRepository.INITIAL,
                request.initialItems(),
                stored.initialItems(),
                stored.currency());
        writeItems(
                workspaceId,
                id,
                VentureRepository.FIXED_MONTHLY,
                request.fixedItems(),
                stored.fixedItems(),
                stored.currency());
        writeProducts(workspaceId, id, request.products(), stored.products(), stored.currency());
        events.publishEvent(new VentureChanged(workspaceId, id));
        return view(ventures.find(workspaceId, id).orElseThrow());
    }

    /**
     * Moves the project along the status table. Opening writes the start day, closing the end day; reopening a
     * closed project clears it and goes through the plan limit.
     *
     * @throws VentureNotFoundException when the family has no such project
     * @throws VentureRejectedException when the change is out of the table, a day is wrong or the version is stale
     * @throws VentureLimitReachedException when reopening would pass the Grátis limit
     */
    @Transactional
    VentureView changeStatus(WorkspaceMember member, UUID id, ChangeStatusRequest request) {
        UUID workspaceId = member.workspaceId();
        StoredVenture stored = ventures.find(workspaceId, id).orElseThrow(VentureNotFoundException::new);
        if (request.version() != stored.version()) {
            throw VentureRejectedException.versionConflict();
        }
        VentureStatus from = stored.status();
        VentureStatus to = request.status();
        if (!StatusTransition.allowed(from, to)) {
            throw VentureRejectedException.statusInvalid();
        }
        LocalDate today = today(member);
        LocalDate on = request.on() != null ? request.on() : today;
        if (on.isAfter(today)) {
            throw VentureRejectedException.startedOnInvalid("O dia não pode ser no futuro.");
        }
        LocalDate startedOn = stored.startedOn();
        LocalDate closedOn = null;
        if (from == VentureStatus.PLANNING) {
            startedOn = on;
        }
        if (to == VentureStatus.CLOSED) {
            closedOn = on;
            if (startedOn != null && on.isBefore(startedOn)) {
                throw VentureRejectedException.startedOnInvalid(
                        "O encerramento não pode ser antes do dia em que o projeto começou.");
            }
        }
        if (from == VentureStatus.CLOSED) {
            ventures.lockFamily(workspaceId);
            if (limitReached(workspaceId)) {
                throw new VentureLimitReachedException();
            }
        }
        if (!ventures.changeStatus(workspaceId, id, to, startedOn, closedOn, request.version(), clock.instant())) {
            throw VentureRejectedException.versionConflict();
        }
        if (to == VentureStatus.CLOSED) {
            events.publishEvent(new VentureClosed(workspaceId, id, member.userId()));
        }
        events.publishEvent(new VentureChanged(workspaceId, id));
        return view(ventures.find(workspaceId, id).orElseThrow());
    }

    /** Deletes the project with its items, products and steps. */
    @Transactional
    void delete(WorkspaceMember member, UUID id) {
        UUID workspaceId = member.workspaceId();
        if (!ventures.delete(workspaceId, id)) {
            throw new VentureNotFoundException();
        }
        events.publishEvent(new VentureDeleted(workspaceId, id, member.userId()));
        events.publishEvent(new VentureChanged(workspaceId, id));
    }

    /** The figures of the stored project: what the screen shows and what the actuals are measured against (9.2). */
    VentureFigures figuresOf(StoredVenture venture) {
        return VentureCalculator.calculate(
                venture.initialItems().stream()
                        .map(item -> new CostLine(item.amount()))
                        .toList(),
                venture.fixedItems().stream()
                        .map(item -> new CostLine(item.amount()))
                        .toList(),
                venture.products().stream()
                        .map(product -> new ProductLine(
                                product.unitVariableCost(), product.unitPrice(), product.plannedUnitsMonth()))
                        .toList());
    }

    private void copyValues(UUID workspaceId, UUID id, Template template, String currency) {
        int position = 0;
        for (TemplateItem item : template.initial()) {
            ventures.insertItem(
                    workspaceId,
                    id,
                    VentureRepository.INITIAL,
                    null,
                    item.name(),
                    item.amount(),
                    currency,
                    true,
                    position++);
        }
        position = 0;
        for (TemplateItem item : template.fixed()) {
            ventures.insertItem(
                    workspaceId,
                    id,
                    VentureRepository.FIXED_MONTHLY,
                    null,
                    item.name(),
                    item.amount(),
                    currency,
                    true,
                    position++);
        }
        position = 0;
        for (TemplateProduct product : template.products()) {
            ventures.insertProduct(
                    workspaceId,
                    id,
                    null,
                    product.name(),
                    product.unitVariableCost(),
                    product.unitPrice(),
                    product.plannedUnitsMonth(),
                    currency,
                    true,
                    position++);
        }
    }

    private void writeItems(
            UUID workspaceId,
            UUID id,
            String kind,
            List<CostItemInput> inputs,
            List<StoredVenture.Item> known,
            String currency) {
        Map<UUID, StoredVenture.Item> byId =
                known.stream().collect(Collectors.toMap(StoredVenture.Item::id, Function.identity()));
        Set<UUID> used = new HashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            CostItemInput input = inputs.get(i);
            StoredVenture.Item before = input.id() == null ? null : byId.get(input.id());
            UUID keep = before != null && used.add(before.id()) ? before.id() : null;
            boolean example = keep != null
                    && before.isExample()
                    && before.name().equals(input.name())
                    && before.amount().compareTo(input.amount()) == 0;
            ventures.insertItem(workspaceId, id, kind, keep, input.name(), input.amount(), currency, example, i);
        }
    }

    private void writeProducts(
            UUID workspaceId, UUID id, List<ProductInput> inputs, List<StoredVenture.Product> known, String currency) {
        Map<UUID, StoredVenture.Product> byId =
                known.stream().collect(Collectors.toMap(StoredVenture.Product::id, Function.identity()));
        Set<UUID> used = new HashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            ProductInput input = inputs.get(i);
            StoredVenture.Product before = input.id() == null ? null : byId.get(input.id());
            UUID keep = before != null && used.add(before.id()) ? before.id() : null;
            boolean example = keep != null
                    && before.isExample()
                    && before.name().equals(input.name())
                    && before.unitVariableCost().compareTo(input.unitVariableCost()) == 0
                    && before.unitPrice().compareTo(input.unitPrice()) == 0
                    && before.plannedUnitsMonth() == input.plannedUnitsMonth();
            ventures.insertProduct(
                    workspaceId,
                    id,
                    keep,
                    input.name(),
                    input.unitVariableCost(),
                    input.unitPrice(),
                    input.plannedUnitsMonth(),
                    currency,
                    example,
                    i);
        }
    }

    private static void check(CostItemInput item) {
        VentureValidation.itemName(item.name());
        VentureValidation.itemAmount(item.amount());
    }

    private static void check(ProductInput product) {
        VentureValidation.productName(product.name());
        VentureValidation.unitCost(product.unitVariableCost());
        VentureValidation.unitPrice(product.unitPrice());
        VentureValidation.plannedUnits(product.plannedUnitsMonth());
    }

    /** In planning there is no start day; after it there must be one, not in the future and not after the end. */
    private static void checkStartedOn(
            VentureStatus status, @Nullable LocalDate closedOn, @Nullable LocalDate startedOn, LocalDate today) {
        if (status == VentureStatus.PLANNING) {
            if (startedOn != null) {
                throw VentureRejectedException.startedOnInvalid(
                        "Um projeto em planejamento ainda não tem dia de início.");
            }
            return;
        }
        if (startedOn == null) {
            throw VentureRejectedException.startedOnInvalid("Informe o dia em que o projeto começou.");
        }
        if (startedOn.isAfter(today)) {
            throw VentureRejectedException.startedOnInvalid("O dia de início não pode ser no futuro.");
        }
        if (closedOn != null && startedOn.isAfter(closedOn)) {
            throw VentureRejectedException.startedOnInvalid("O dia de início não pode ser depois do encerramento.");
        }
    }

    /** Whether a new open project would pass the Grátis limit (D7). Counts the projects as they are now. */
    private boolean limitReached(UUID workspaceId) {
        return plans.legalSetEnabled()
                && plans.current(workspaceId).key() == PlanKey.FREE
                && ventures.countOpen(workspaceId) >= FREE_ACTIVE_VENTURES;
    }

    private LocalDate today(WorkspaceMember member) {
        return LocalDate.ofInstant(clock.instant(), member.timezone());
    }

    private static boolean canManage(WorkspaceMember member) {
        return member.role() == Role.OWNER || member.role() == Role.ADULT;
    }

    private static VentureSummary summary(StoredVenture venture) {
        VentureFigures figures = figures(venture);
        Currency currency = Currency.getInstance(venture.currency());
        return new VentureSummary(
                venture.id(),
                venture.name(),
                venture.type(),
                venture.status(),
                venture.startedOn(),
                venture.closedOn(),
                Money.of(figures.monthlyProfit(), currency),
                figures.paybackState(),
                figures.paybackMonths(),
                venture.hasExamples(),
                venture.version());
    }

    private VentureView view(StoredVenture venture) {
        VentureFigures figures = figures(venture);
        VentureValidation.figuresResult(figures);
        Currency currency = Currency.getInstance(venture.currency());
        List<StepView> steps = new ArrayList<>();
        for (StoredVenture.Step step : venture.steps()) {
            steps.add(new StepView(step.id(), step.title(), step.position(), step.doneAt()));
        }
        return new VentureView(
                venture.id(),
                venture.name(),
                venture.type(),
                venture.templateKey(),
                venture.status(),
                venture.startedOn(),
                venture.closedOn(),
                venture.currency(),
                venture.initialItems().stream()
                        .map(item -> itemView(item, currency))
                        .toList(),
                venture.fixedItems().stream()
                        .map(item -> itemView(item, currency))
                        .toList(),
                venture.products().stream()
                        .map(product -> new ProductView(
                                product.id(),
                                product.name(),
                                Money.of(product.unitVariableCost(), currency),
                                Money.of(product.unitPrice(), currency),
                                product.plannedUnitsMonth(),
                                product.isExample()))
                        .toList(),
                steps,
                FiguresView.of(figures, currency),
                venture.version());
    }

    private static CostItemView itemView(StoredVenture.Item item, Currency currency) {
        return new CostItemView(item.id(), item.name(), Money.of(item.amount(), currency), item.isExample());
    }

    private static TemplatesView.TemplateView templateView(Template template, Currency brl) {
        VentureFigures figures = VentureCalculator.calculate(
                template.initial().stream()
                        .map(item -> new CostLine(item.amount()))
                        .toList(),
                template.fixed().stream()
                        .map(item -> new CostLine(item.amount()))
                        .toList(),
                template.products().stream()
                        .map(product -> new ProductLine(
                                product.unitVariableCost(), product.unitPrice(), product.plannedUnitsMonth()))
                        .toList());
        return new TemplatesView.TemplateView(
                template.key(),
                template.name(),
                template.type(),
                template.initial().stream().map(item -> templateItem(item, brl)).toList(),
                template.fixed().stream().map(item -> templateItem(item, brl)).toList(),
                template.products().stream()
                        .map(product -> new TemplatesView.TemplateProductView(
                                product.name(),
                                Money.of(product.unitVariableCost(), brl),
                                Money.of(product.unitPrice(), brl),
                                product.plannedUnitsMonth()))
                        .toList(),
                template.steps(),
                FiguresView.of(figures, brl));
    }

    private static TemplatesView.TemplateItemView templateItem(TemplateItem item, Currency brl) {
        return new TemplatesView.TemplateItemView(item.name(), Money.of(item.amount(), brl));
    }

    private static VentureFigures figures(StoredVenture venture) {
        return VentureCalculator.calculate(
                venture.initialItems().stream()
                        .map(item -> new CostLine(item.amount()))
                        .toList(),
                venture.fixedItems().stream()
                        .map(item -> new CostLine(item.amount()))
                        .toList(),
                venture.products().stream()
                        .map(product -> new ProductLine(
                                product.unitVariableCost(), product.unitPrice(), product.plannedUnitsMonth()))
                        .toList());
    }
}
