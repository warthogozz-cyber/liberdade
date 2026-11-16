package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The family does not exist or the caller is not an active member: the answer never tells which. */
final class WorkspaceNotFoundException extends DomainException {

    WorkspaceNotFoundException() {
        super(404, "workspace.not-found", "Família não encontrada.");
    }
}
