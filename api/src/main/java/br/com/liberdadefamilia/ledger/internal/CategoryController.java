package br.com.liberdadefamilia.ledger.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;

import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The family's categories of one kind, most used first (D31). */
@RestController
class CategoryController {

    /** Today and the 89 days before it. */
    static final int USAGE_WINDOW_DAYS = 90;

    private final CategoryRepository categories;
    private final Clock clock;

    CategoryController(CategoryRepository categories, Clock clock) {
        this.categories = categories;
        this.clock = clock;
    }

    /** "Today" is the family's date, in its time zone, not the server's. */
    @GetMapping("/v1/workspaces/{ws}/categories")
    @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
    List<CategoryView> listCategories(@RequestParam Kind kind, WorkspaceMember member) {
        LocalDate today = LocalDate.now(clock.withZone(member.timezone()));
        return categories.unarchivedByUse(
                member.workspaceId(), kind.transactionKind, today.minusDays(USAGE_WINDOW_DAYS - 1));
    }

    /** The kinds a category can have. Anything else in {@code kind}, TRANSFER included, is a 400. */
    enum Kind {
        EXPENSE(TransactionKind.EXPENSE),
        INCOME(TransactionKind.INCOME);

        private final TransactionKind transactionKind;

        Kind(TransactionKind transactionKind) {
            this.transactionKind = transactionKind;
        }
    }
}
