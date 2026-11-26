package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A rule of the receipts that the request broke. The message never carries the key. */
final class ReceiptRuleException extends DomainException {

    private ReceiptRuleException(int status, String code, String detail) {
        super(status, code, detail);
    }

    static ReceiptRuleException keyInvalid() {
        return new ReceiptRuleException(
                422, "receipt.key_invalid", "Esta chave não parece de uma nota fiscal de consumidor válida.");
    }

    static ReceiptRuleException duplicate() {
        return new ReceiptRuleException(409, "receipt.duplicate", "Esta nota já foi registrada.");
    }
}
