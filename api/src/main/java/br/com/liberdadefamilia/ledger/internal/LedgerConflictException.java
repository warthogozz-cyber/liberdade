package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.DomainException;
import java.util.Map;

/** The request clashes with what the family already has. The message is the code only, never a value. */
final class LedgerConflictException extends DomainException {

    LedgerConflictException(String code, String detail) {
        super(409, code, detail);
    }

    /** With extra members for the problem, such as the {@code current} state the client should see. */
    LedgerConflictException(String code, String detail, Map<String, Object> properties) {
        super(409, code, detail, properties);
    }
}
