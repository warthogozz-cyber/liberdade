package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** Each person belongs to one family for now (D14). */
final class WorkspaceAlreadyExistsException extends DomainException {

    WorkspaceAlreadyExistsException() {
        super(409, "workspace.already-exists", "Você já faz parte de uma família.");
    }
}
