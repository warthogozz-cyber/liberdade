package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.CategoryChanged;
import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, edits, archives and brings back the family's categories. A subcategory has a top-level parent of the same
 * kind and no children of its own (D7). Error messages and logs never carry a name. Every change publishes
 * {@link CategoryChanged} inside its transaction.
 */
@Service
class CategoryService {

    private final CategoryRepository categories;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    CategoryService(CategoryRepository categories, Clock clock, ApplicationEventPublisher events) {
        this.categories = categories;
        this.clock = clock;
        this.events = events;
    }

    /**
     * @throws LedgerRejectedException when the kind is not INCOME or EXPENSE, or the parent is not an active top-level
     *     category of the family and of the same kind
     * @throws LedgerConflictException when a sibling has the name
     */
    @Transactional
    CategoryView create(WorkspaceMember member, CreateCategoryRequest request) {
        UUID workspaceId = member.workspaceId();
        if (request.kind() == TransactionKind.TRANSFER) {
            throw new LedgerRejectedException("category.kind-invalid", "Categorias são de receita ou de gasto.");
        }
        UUID parentId = request.parentId();
        if (parentId != null) {
            boolean usable = categories
                    .find(workspaceId, parentId)
                    .filter(parent ->
                            parent.archivedAt() == null && parent.parentId() == null && parent.kind() == request.kind())
                    .isPresent();
            if (!usable) {
                throw new LedgerRejectedException(
                        "category.parent-invalid", "Escolha uma categoria principal ativa, do mesmo tipo.");
            }
        }
        UUID id = nameTakenIfDuplicate(() -> categories.insert(
                workspaceId, request.name(), request.kind(), request.icon(), request.color(), parentId));
        events.publishEvent(new CategoryChanged(workspaceId, id));
        return view(workspaceId, id);
    }

    /**
     * @throws LedgerNotFoundException when the category is not in the family
     * @throws LedgerRejectedException when it would make a category that is not an expense one fixed
     * @throws LedgerConflictException when a sibling has the name
     */
    @Transactional
    CategoryView update(WorkspaceMember member, UUID categoryId, UpdateCategoryRequest request) {
        UUID workspaceId = member.workspaceId();
        CategoryView category = view(workspaceId, categoryId);
        if (Boolean.TRUE.equals(request.isFixed()) && category.kind() != TransactionKind.EXPENSE) {
            throw new LedgerRejectedException("category.fixed-not-expense", "Só categorias de gasto podem ser fixas.");
        }
        nameTakenIfDuplicate(() -> categories.update(
                workspaceId, categoryId, request.name(), request.icon(), request.color(), request.isFixed()));
        events.publishEvent(new CategoryChanged(workspaceId, categoryId));
        return view(workspaceId, categoryId);
    }

    /**
     * Archives the category and its active subcategories. Archiving an archived one changes nothing.
     *
     * @throws LedgerNotFoundException when the category is not in the family
     * @throws LedgerRejectedException when it is the last active top-level category of its kind
     */
    @Transactional
    CategoryView archive(WorkspaceMember member, UUID categoryId) {
        UUID workspaceId = member.workspaceId();
        CategoryView category = view(workspaceId, categoryId);
        if (category.archivedAt() == null) {
            if (category.parentId() == null && categories.countActiveTopLevel(workspaceId, category.kind()) <= 1) {
                throw new LedgerRejectedException(
                        "category.last-active", "É preciso manter pelo menos uma categoria ativa desse tipo.");
            }
            if (categories.archiveWithChildren(workspaceId, categoryId, clock.instant())) {
                events.publishEvent(new CategoryChanged(workspaceId, categoryId));
            }
        }
        return view(workspaceId, categoryId);
    }

    /**
     * Brings the category back, with the subcategories that were archived along with it. Bringing back an active one
     * changes nothing.
     *
     * @throws LedgerNotFoundException when the category is not in the family
     * @throws LedgerRejectedException when its parent is archived
     * @throws LedgerConflictException when an active sibling has its name now
     */
    @Transactional
    CategoryView unarchive(WorkspaceMember member, UUID categoryId) {
        UUID workspaceId = member.workspaceId();
        CategoryView category = view(workspaceId, categoryId);
        if (category.archivedAt() != null) {
            UUID parentId = category.parentId();
            if (parentId != null
                    && categories
                            .find(workspaceId, parentId)
                            .filter(parent -> parent.archivedAt() != null)
                            .isPresent()) {
                throw new LedgerRejectedException(
                        "category.parent-archived", "Reative a categoria principal primeiro.");
            }
            if (nameTakenIfDuplicate(() -> categories.unarchiveWithChildren(workspaceId, categoryId))) {
                events.publishEvent(new CategoryChanged(workspaceId, categoryId));
            }
        }
        return view(workspaceId, categoryId);
    }

    private CategoryView view(UUID workspaceId, UUID categoryId) {
        return categories
                .find(workspaceId, categoryId)
                .orElseThrow(() -> new LedgerNotFoundException("category.not-found", "Categoria não encontrada."));
    }

    /** Runs the write; the unique index on active names turns into a 409 that says nothing of the other category. */
    private static <T> T nameTakenIfDuplicate(Supplier<T> write) {
        try {
            return write.get();
        } catch (DuplicateKeyException e) {
            throw new LedgerConflictException("category.name-taken", "Já existe uma categoria com esse nome aqui.");
        }
    }
}
