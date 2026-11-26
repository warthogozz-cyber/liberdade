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

    static ReceiptRuleException marketRequired() {
        return new ReceiptRuleException(
                422,
                "receipt.market_required",
                "Escolha um mercado da lista ou informe o nome e o tipo de um mercado novo.");
    }

    static ReceiptRuleException marketCnpjDiffers() {
        return new ReceiptRuleException(
                409, "receipt.market_cnpj_differs", "Este mercado já tem outro CNPJ. Confira se é o mercado da nota.");
    }

    static ReceiptRuleException dateInvalid() {
        return new ReceiptRuleException(
                422, "receipt.date_invalid", "A data precisa ser do mês da nota e não pode ser de amanhã em diante.");
    }

    static ReceiptRuleException itemsInvalid() {
        return new ReceiptRuleException(
                422, "receipt.items_invalid", "Informe de 1 a 200 itens, cada um com um produto e uma unidade.");
    }

    static ReceiptRuleException productUnknown() {
        return new ReceiptRuleException(422, "receipt.product_unknown", "Esse produto não existe na família.");
    }

    static ReceiptRuleException quantityInvalid() {
        return new ReceiptRuleException(
                422, "receipt.quantity_invalid", "A quantidade precisa ser maior que zero, com até 4 casas decimais.");
    }

    static ReceiptRuleException priceInvalid() {
        return new ReceiptRuleException(
                422, "receipt.price_invalid", "O preço precisa ser maior que zero, com até 4 casas decimais.");
    }
}
