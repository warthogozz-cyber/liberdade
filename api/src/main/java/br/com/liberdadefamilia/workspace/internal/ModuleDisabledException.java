package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The route belongs to an optional module the family has not switched on. */
final class ModuleDisabledException extends DomainException {

    ModuleDisabledException() {
        super(403, "module.disabled", "Este módulo está desligado na sua família.");
    }
}
