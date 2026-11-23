package br.com.liberdadefamilia.ledger.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The family's categories: list (most used first, D31), create, edit, archive and bring back. */
@RestController
class CategoryController {

    /** Today and the 89 days before it. */
    static final int USAGE_WINDOW_DAYS = 90;

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final CategoryService service;
    private final Clock clock;

    CategoryController(
            AccountRepository accounts, CategoryRepository categories, CategoryService service, Clock clock) {
        this.accounts = accounts;
        this.categories = categories;
        this.service = service;
        this.clock = clock;
    }

    /**
     * "Today" is the family's date, in its time zone, not the server's. Every category is listed to every role; the
     * order counts only the use in the member's scope.
     */
    @GetMapping("/v1/workspaces/{ws}/categories")
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    List<CategoryView> listCategories(@RequestParam Kind kind, WorkspaceMember member) {
        LocalDate today = LocalDate.now(clock.withZone(member.timezone()));
        return categories.allByUse(
                member.workspaceId(),
                kind.transactionKind,
                today.minusDays(USAGE_WINDOW_DAYS - 1),
                accounts.scopeFor(member));
    }

    @PostMapping("/v1/workspaces/{ws}/categories")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    ResponseEntity<CategoryView> createCategory(
            @Valid @RequestBody CreateCategoryRequest request, WorkspaceMember member) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(member, request));
    }

    @PatchMapping("/v1/workspaces/{ws}/categories/{id}")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    CategoryView updateCategory(
            @PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request, WorkspaceMember member) {
        return service.update(member, id, request);
    }

    @PostMapping("/v1/workspaces/{ws}/categories/{id}/archive")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    CategoryView archiveCategory(@PathVariable UUID id, WorkspaceMember member) {
        return service.archive(member, id);
    }

    @PostMapping("/v1/workspaces/{ws}/categories/{id}/unarchive")
    @WorkspaceScoped(allow = {OWNER, ADULT})
    CategoryView unarchiveCategory(@PathVariable UUID id, WorkspaceMember member) {
        return service.unarchive(member, id);
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
