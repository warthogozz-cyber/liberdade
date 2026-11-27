package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** The family has no such product; another family's is the same. */
final class ProductNotFoundException extends DomainException {

    ProductNotFoundException() {
        super(404, "product.not_found", "Produto não encontrado.");
    }
}
