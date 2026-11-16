package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The path names a module that does not exist. */
final class ModuleNotFoundException extends DomainException {

    ModuleNotFoundException() {
        super(404, "module.not-found", "Módulo não encontrado.");
    }
}
