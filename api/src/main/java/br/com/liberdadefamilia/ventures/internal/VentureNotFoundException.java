package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The family has no such project; another family's is the same. */
final class VentureNotFoundException extends DomainException {

    VentureNotFoundException() {
        super(404, "venture.not-found", "Projeto não encontrado.");
    }
}
