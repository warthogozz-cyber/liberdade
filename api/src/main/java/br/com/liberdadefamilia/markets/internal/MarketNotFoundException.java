package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The family has no such market; another family's, or a form of reference that does not exist, is the same. */
public final class MarketNotFoundException extends DomainException {

    public MarketNotFoundException() {
        super(404, "market.not_found", "Mercado não encontrado.");
    }
}
