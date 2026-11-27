package br.com.liberdadefamilia.markets;

import java.util.UUID;

/** Something changed on the family's shopping list. Published in the transaction that wrote it; it carries no item. */
public record ShoppingListChanged(UUID workspaceId) {}
