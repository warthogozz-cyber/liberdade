package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A budget request the API turns down: malformed, against the rules, stale, or about a category the family lacks. */
final class BudgetRejectedException extends DomainException {

    private BudgetRejectedException(int status, String code, String detail) {
        super(status, code, detail);
    }

    static BudgetRejectedException invalid() {
        return new BudgetRejectedException(400, "request.invalid", "Alguns campos precisam de ajuste.");
    }

    static BudgetRejectedException categoryNotFound() {
        return new BudgetRejectedException(404, "category.not-found", "Categoria não encontrada.");
    }

    static BudgetRejectedException categoryNotExpense() {
        return new BudgetRejectedException(
                422, "budget.category-not-expense", "Orçamento só vale para categorias de gasto.");
    }

    static BudgetRejectedException currency() {
        return new BudgetRejectedException(422, "budget.currency", "O limite precisa estar na moeda da família.");
    }

    static BudgetRejectedException versionConflict() {
        return new BudgetRejectedException(
                409, "budget.version-conflict", "O limite foi alterado por outra pessoa. Recarregue e tente de novo.");
    }
}
