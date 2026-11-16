package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The person is already an active member of the family, so the invitation is left unused. */
final class InvitationConflictException extends DomainException {

    InvitationConflictException() {
        super(409, "invitation.already-member", "Você já faz parte desta família.");
    }
}
