package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A well-formed request that the family's rules refuse. The message is the code only, never a name. */
final class WorkspaceRejectedException extends DomainException {

    WorkspaceRejectedException(String code, String detail) {
        super(422, code, detail);
    }
}
