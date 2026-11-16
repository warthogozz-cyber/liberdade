package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A consent request that the rules refuse. The message is the code only, never a name. */
final class PrivacyRejectedException extends DomainException {

    PrivacyRejectedException(int status, String code, String detail) {
        super(status, code, detail);
    }
}
