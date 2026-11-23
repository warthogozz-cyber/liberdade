package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.ModuleToggled;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Switching planning on plans the family's active rules at once, in the transaction of the switch, so the next days
 * are not empty until the nightly job. Switching it off changes nothing: no rule or planned transaction is touched.
 */
@Component
class PlanningModuleListener {

    private final RecurringRuleRepository rules;
    private final RecurrenceGenerator generator;

    PlanningModuleListener(RecurringRuleRepository rules, RecurrenceGenerator generator) {
        this.rules = rules;
        this.generator = generator;
    }

    @EventListener
    void on(ModuleToggled event) {
        if (event.key() != ModuleKey.PLANNING || !event.enabled()) {
            return;
        }
        for (UUID ruleId : rules.activeIds(event.workspaceId())) {
            generator.generate(event.workspaceId(), ruleId);
        }
    }
}
