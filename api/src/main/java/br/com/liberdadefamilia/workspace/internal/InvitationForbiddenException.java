package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** An ADULT tried to invite an OWNER. */
final class InvitationForbiddenException extends DomainException {

    InvitationForbiddenException() {
        super(403, "invitation.role-forbidden", "Só quem é Dona ou Dono pode convidar outra Dona ou Dono.");
    }
}
