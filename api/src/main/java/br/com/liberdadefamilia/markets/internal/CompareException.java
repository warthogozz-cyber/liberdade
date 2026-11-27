package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The comparison asked for markets it cannot compare. */
final class CompareException extends DomainException {

    private CompareException(int status, String code, String detail) {
        super(status, code, detail);
    }

    static CompareException marketsInvalid() {
        return new CompareException(
                422, "compare.markets_invalid", "Escolha 2 ou 3 mercados diferentes para comparar.");
    }
}
