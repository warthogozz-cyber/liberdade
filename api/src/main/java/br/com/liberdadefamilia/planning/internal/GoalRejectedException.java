package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A goal or contribution request the API turns down: malformed, against the rules, or stale. */
final class GoalRejectedException extends DomainException {

    private GoalRejectedException(int status, String code, String detail) {
        super(status, code, detail);
    }

    static GoalRejectedException invalid() {
        return new GoalRejectedException(400, "request.invalid", "Alguns campos precisam de ajuste.");
    }

    static GoalRejectedException linkedAccountInvalid() {
        return new GoalRejectedException(
                422, "goal.linked-account-invalid", "Escolha uma conta ativa da família para a meta acompanhar.");
    }

    static GoalRejectedException currency() {
        return new GoalRejectedException(422, "goal.currency", "A meta precisa estar na moeda da família.");
    }

    static GoalRejectedException dueInPast() {
        return new GoalRejectedException(422, "goal.due-in-past", "O prazo não pode ser um mês que já passou.");
    }

    static GoalRejectedException linkedAccount() {
        return new GoalRejectedException(422, "goal.linked-account", "Esta meta acompanha o saldo de uma conta.");
    }

    static GoalRejectedException negativeBalance() {
        return new GoalRejectedException(422, "goal.negative-balance", "O guardado não pode ficar negativo.");
    }

    static GoalRejectedException versionConflict() {
        return new GoalRejectedException(
                409, "goal.version-conflict", "A meta foi alterada por outra pessoa. Recarregue e tente de novo.");
    }
}
