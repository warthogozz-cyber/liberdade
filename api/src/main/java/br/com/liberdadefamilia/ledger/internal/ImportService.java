package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.ledger.domain.DescriptionKey;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Statement import: the category hints for now, the batch itself in the next step. */
@Service
class ImportService {

    /** How far back a past transaction may teach a category. */
    static final int HINT_DAYS = 365;

    private final ImportRepository imports;
    private final AccountRepository accounts;
    private final Clock clock;

    ImportService(ImportRepository imports, AccountRepository accounts, Clock clock) {
        this.imports = imports;
        this.accounts = accounts;
        this.clock = clock;
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

    private record Key(TransactionKind kind, String key) {}
}
