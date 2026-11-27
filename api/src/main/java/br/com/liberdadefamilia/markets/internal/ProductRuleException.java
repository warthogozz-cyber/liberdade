package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A rule of editing or joining products that the request broke. The message never carries a name. */
final class ProductRuleException extends DomainException {

    private ProductRuleException(int status, String code, String detail) {
        super(status, code, detail);
    }

    static ProductRuleException nameInvalid() {
        return new ProductRuleException(
                422, "product.name_invalid", "O nome do produto precisa ter de 1 a 80 caracteres.");
    }

    static ProductRuleException mergeSame() {
        return new ProductRuleException(422, "product.merge_same", "Escolha dois produtos diferentes para juntar.");
    }

    static ProductRuleException mergeGtinConflict() {
        return new ProductRuleException(
                422,
                "product.merge_gtin_conflict",
                "Estes produtos têm códigos de barras diferentes, então são produtos diferentes.");
    }
}
