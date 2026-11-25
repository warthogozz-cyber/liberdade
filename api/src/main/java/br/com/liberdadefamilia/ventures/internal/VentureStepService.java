package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.ventures.VentureChanged;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The steps of a project. The last write wins: steps carry no version and do not touch the project's. Every write
 * takes the family lock first, so renumbering never interleaves; steps of a closed project stay editable.
 */
@Service
class VentureStepService {

    private final VentureRepository ventures;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    VentureStepService(VentureRepository ventures, ApplicationEventPublisher events, Clock clock) {
        this.ventures = ventures;
        this.events = events;
        this.clock = clock;
    }

    /** Adds the step at the end. */
    @Transactional
    StepView add(WorkspaceMember member, UUID ventureId, CreateStepRequest request) {
        VentureValidation.stepTitle(request.title());
        UUID workspaceId = member.workspaceId();
        List<StoredVenture.Step> steps = lockAndLoad(workspaceId, ventureId);
        if (steps.size() >= VentureValidation.MAX_STEPS) {
            throw VentureRejectedException.tooManySteps();
        }
        StoredVenture.Step added = ventures.addStep(workspaceId, ventureId, request.title(), steps.size());
        events.publishEvent(new VentureChanged(workspaceId, ventureId));
        return new StepView(added.id(), added.title(), added.position(), added.doneAt());
    }

    /** Renames, ticks or moves the step; a move renumbers the others. */
    @Transactional
    StepView update(WorkspaceMember member, UUID ventureId, UUID stepId, UpdateStepRequest request) {
        if (request.title() == null && request.done() == null && request.position() == null) {
            throw VentureRejectedException.invalid("Informe o que mudar no passo: título, feito ou posição.");
        }
        if (request.title() != null) {
            VentureValidation.stepTitle(request.title());
        }
        UUID workspaceId = member.workspaceId();
        List<StoredVenture.Step> steps = lockAndLoad(workspaceId, ventureId);
        StoredVenture.Step current = steps.stream()
                .filter(step -> step.id().equals(stepId))
                .findFirst()
                .orElseThrow(VentureNotFoundException::new);
        Integer position = request.position();
        if (position != null && (position < 0 || position >= steps.size())) {
            throw VentureRejectedException.invalid(
                    "A posição do passo precisa ficar entre 0 e " + (steps.size() - 1) + ".");
        }

        String title = request.title() == null ? current.title() : request.title();
        Instant doneAt = current.doneAt();
        if (request.done() != null) {
            doneAt = request.done() ? clock.instant() : null;
        }
        ventures.updateStep(workspaceId, ventureId, stepId, title, doneAt);

        int finalPosition = current.position();
        if (position != null && position != current.position()) {
            List<UUID> ordered =
                    new ArrayList<>(steps.stream().map(StoredVenture.Step::id).toList());
            ordered.remove(stepId);
            ordered.add(position, stepId);
            ventures.renumberSteps(workspaceId, ventureId, ordered);
            finalPosition = position;
        }
        events.publishEvent(new VentureChanged(workspaceId, ventureId));
        return new StepView(stepId, title, finalPosition, doneAt);
    }

    /** Removes the step; the others close the gap. */
    @Transactional
    void delete(WorkspaceMember member, UUID ventureId, UUID stepId) {
        UUID workspaceId = member.workspaceId();
        List<StoredVenture.Step> steps = lockAndLoad(workspaceId, ventureId);
        if (steps.stream().noneMatch(step -> step.id().equals(stepId))) {
            throw new VentureNotFoundException();
        }
        ventures.deleteStep(workspaceId, ventureId, stepId);
        ventures.renumberSteps(
                workspaceId,
                ventureId,
                steps.stream()
                        .map(StoredVenture.Step::id)
                        .filter(id -> !id.equals(stepId))
                        .toList());
        events.publishEvent(new VentureChanged(workspaceId, ventureId));
    }

    /** Takes the family lock, then reads the project (404 when it is not the family's) and its steps. */
    private List<StoredVenture.Step> lockAndLoad(UUID workspaceId, UUID ventureId) {
        ventures.lockFamily(workspaceId);
        if (!ventures.exists(workspaceId, ventureId)) {
            throw new VentureNotFoundException();
        }
        return ventures.steps(workspaceId, ventureId);
    }
}
