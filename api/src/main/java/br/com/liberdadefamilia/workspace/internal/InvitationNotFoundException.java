package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** No such invitation here: the answer is the same for a token nobody made and for another family's invitation. */
final class InvitationNotFoundException extends DomainException {

    InvitationNotFoundException() {
        super(404, "invitation.not-found", "Convite não encontrado.");
    }
}
