package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The membership is not in this family: the answer is the same for one that never existed. */
final class MemberNotFoundException extends DomainException {

    MemberNotFoundException() {
        super(404, "member.not-found", "Pessoa não encontrada na família.");
    }
}
