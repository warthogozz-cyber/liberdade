package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.Modules;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every day, plans the next occurrences of the recurring rules (D13). Each family goes in its own transaction with all
 * its rules, so one failure is logged by its class only and does not stop the others. A family with the planning
 * module off is skipped; its rules stay as they are and are caught up when the module is switched on.
 */
@Component
public class RecurrenceJob {

    private static final Logger log = LoggerFactory.getLogger(RecurrenceJob.class);

    private final RecurringRuleRepository rules;
    private final RecurrenceGenerator generator;
    private final Modules modules;
    private final TransactionTemplate tx;

    RecurrenceJob(
            RecurringRuleRepository rules,
            RecurrenceGenerator generator,
            Modules modules,
            PlatformTransactionManager transactions) {
        this.rules = rules;
        this.generator = generator;
        this.modules = modules;
        this.tx = new TransactionTemplate(transactions);
    }

    @Scheduled(cron = "0 10 4 * * *", zone = "America/Sao_Paulo")
    public void run() {
        for (UUID workspaceId : rules.familiesWithRulesToPlan()) {
            try {
                if (!modules.isEnabled(workspaceId, ModuleKey.PLANNING)) {
                    continue;
                }
                tx.executeWithoutResult(status -> {
                    for (UUID ruleId : rules.activeIds(workspaceId)) {
                        generator.generate(workspaceId, ruleId);
                    }
                });
            } catch (RuntimeException failure) {
                // The class only: the exception message could carry a value.
                log.warn(
                        "recurrence job failed for a family: {}",
                        failure.getClass().getName());
            }
        }
    }
}
