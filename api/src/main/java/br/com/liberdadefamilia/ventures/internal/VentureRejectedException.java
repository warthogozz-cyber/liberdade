package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A project request the API turns down: a wrong field, a wrong date, a status change out of the table, or stale. */
final class VentureRejectedException extends DomainException {

    private VentureRejectedException(int status, String code, String detail) {
        super(status, code, detail);
    }

    /** A wrong field; the detail says which and why. */
    static VentureRejectedException invalid(String detail) {
        return new VentureRejectedException(422, "venture.invalid", detail);
    }

    static VentureRejectedException templateUnknown() {
        return new VentureRejectedException(422, "venture.template-unknown", "Esse modelo não existe.");
    }

    static VentureRejectedException startedOnInvalid(String detail) {
        return new VentureRejectedException(422, "venture.started-on-invalid", detail);
    }

    static VentureRejectedException statusInvalid() {
        return new VentureRejectedException(
                422, "venture.status-invalid", "O projeto não pode mudar para esse status.");
    }

    static VentureRejectedException versionConflict() {
        return new VentureRejectedException(
                409,
                "venture.version-conflict",
                "O projeto foi alterado por outra pessoa. Recarregue e tente de novo.");
    }
}
