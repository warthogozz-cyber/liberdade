package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The invitation was used, cancelled or has expired. */
final class InvitationGoneException extends DomainException {

    InvitationGoneException() {
        super(410, "invitation.gone", "Este convite não vale mais. Peça um novo.");
    }
}
