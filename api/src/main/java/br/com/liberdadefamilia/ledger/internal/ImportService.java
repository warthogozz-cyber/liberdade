package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.AccountChanged;
import br.com.liberdadefamilia.ledger.StatementImported;
import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.ledger.domain.DescriptionKey;
import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.Modules;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statement import: the category hints for the lines, and the batch the person confirmed. The log carries the account
 * and the counts only, never a value, date, description or key of a line.
 */
@Service
class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    /** How far back a past transaction may teach a category. */
    static final int HINT_DAYS = 365;

    private final ImportRepository imports;
    private final AccountRepository accounts;
    private final TransactionService transactions;
    private final Modules modules;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    ImportService(
            ImportRepository imports,
            AccountRepository accounts,
            TransactionService transactions,
            Modules modules,
            Clock clock,
            ApplicationEventPublisher events) {
        this.imports = imports;
        this.accounts = accounts;
        this.transactions = transactions;
        this.modules = modules;
        this.clock = clock;
        this.events = events;
    }

    /**
     * The category of the newest confirmed transaction with the same kind and description key, among the accounts the
     * member may see and the categories still in use. A description with no key, or no history, gets none.
     */
    @Transactional(readOnly = true)
    CategoryHintsResponse categoryHints(WorkspaceMember member, CategoryHintsRequest request) {
        LocalDate since = LocalDate.now(clock.withZone(member.timezone())).minusDays(HINT_DAYS);
        Map<Key, UUID> newest = new HashMap<>();
        for (ImportRepository.CategorizedRow row :
                imports.recentCategorized(member.workspaceId(), accounts.scopeFor(member), since)) {
            newest.putIfAbsent(new Key(row.kind(), DescriptionKey.of(row.description())), row.categoryId());
        }
        List<CategoryHint> hints = request.items().stream()
                .map(item -> item.key().isEmpty() ? null : newest.get(new Key(item.kind(), item.key())))
                .map(CategoryHint::new)
                .toList();
        return new CategoryHintsResponse(hints);
    }

    /**
     * Stores the lines in the account, all or none (D6): one line that breaks a rule of a new transaction refuses the
     * whole batch. A line whose key the family already has (on a transaction, deleted or not, or on a tombstone) is
     * skipped. With planning on, a line that names a planned transaction still planned in the account confirms it
     * instead of making a new one; otherwise it is made new. The account is announced once, when anything changed.
     */
    @Transactional
    ImportResult importRows(WorkspaceMember member, UUID accountId, ImportRequest request) {
        List<ImportRow> rows = request.rows();
        checkBatch(rows);
        UUID workspaceId = member.workspaceId();
        AccountScope scope = accounts.scopeFor(member);
        accounts.choice(workspaceId, accountId, scope)
                .filter(account -> !account.archived())
                .orElseThrow(() -> new LedgerNotFoundException("account.not-found", "Conta não encontrada."));
        for (ImportRow row : rows) {
            transactions.checkImportRow(member, scope, accountId, row);
        }

        boolean planning = modules.isEnabled(workspaceId, ModuleKey.PLANNING);
        int created = 0;
        int confirmed = 0;
        int skipped = 0;
        for (ImportRow row : rows) {
            @Nullable UUID plannedId = planning ? row.confirmsPlannedId() : null;
            if (plannedId != null) {
                if (imports.keyTaken(workspaceId, row.importKey())) {
                    skipped++;
                    continue;
                }
                if (imports.confirmPlanned(workspaceId, plannedId, accountId, scope, member.membershipId(), row)) {
                    confirmed++;
                    continue;
                }
            }
            if (imports.insertImported(workspaceId, accountId, member.membershipId(), row)) {
                created++;
            } else {
                skipped++;
            }
        }

        if (created + confirmed > 0) {
            events.publishEvent(new AccountChanged(workspaceId, accountId));
        }
        events.publishEvent(
                new StatementImported(workspaceId, accountId, member.userId(), created, confirmed, skipped));
        log.info(
                "Imported a statement into account {}: created={}, confirmed={}, skipped={}",
                accountId,
                created,
                confirmed,
                skipped);
        return new ImportResult(created, confirmed, skipped);
    }

    /** Only income and expenses, each with a new-style id, and no id or key twice in the batch. */
    private static void checkBatch(List<ImportRow> rows) {
        Set<UUID> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (ImportRow row : rows) {
            if (row.kind() == TransactionKind.TRANSFER) {
                throw new LedgerRejectedException("import.invalid-kind", "Só é possível importar receitas e despesas.");
            }
            if (row.id().version() != 7) {
                throw new LedgerRejectedException(
                        "transaction.invalid-id", "O identificador do lançamento é inválido.");
            }
            if (!ids.add(row.id()) || !keys.add(row.importKey())) {
                throw new LedgerRejectedException(
                        "import.duplicate-row", "O extrato tem a mesma linha mais de uma vez.");
            }
        }
    }

    private record Key(TransactionKind kind, String key) {}
}
