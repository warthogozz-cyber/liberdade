package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.DomainException;

/**
 * The id is already taken by a transaction with other data, or by another family's. The answer says nothing about the
 * stored one.
 */
final class TransactionIdConflictException extends DomainException {

    TransactionIdConflictException() {
        super(409, "transaction.id-conflict", "Este lançamento já existe com outros dados.");
    }
}
