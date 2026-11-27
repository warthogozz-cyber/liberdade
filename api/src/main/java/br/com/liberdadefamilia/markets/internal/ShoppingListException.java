package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.shared.DomainException;

/** A rule of the shopping list that the request broke. The message never carries a name or a quantity. */
final class ShoppingListException extends DomainException {

    private ShoppingListException(int status, String code, String detail) {
        super(status, code, detail);
    }

    static ShoppingListException quantityInvalid() {
        return new ShoppingListException(
                422,
                "list.quantity_invalid",
                "A quantidade precisa ser maior que zero, com até 4 casas depois do ponto.");
    }

    static ShoppingListException productRequired() {
        return new ShoppingListException(
                422, "list.product_required", "Escolha um produto ou digite o nome de um novo.");
    }

    static ShoppingListException full() {
        return new ShoppingListException(422, "list.full", "A lista já tem o máximo de 300 itens.");
    }

    static ShoppingListException orderStale() {
        return new ShoppingListException(
                409, "list.order_stale", "A lista mudou em outro aparelho. Atualize e tente de novo.");
    }

    static ShoppingListException itemNotFound() {
        return new ShoppingListException(404, "list.item_not_found", "Item da lista não encontrado.");
    }
}
