package br.com.liberdadefamilia.markets;

import java.util.UUID;

/**
 * The family saved a receipt. Published in the transaction that wrote it. It says how the items came and how many
 * there were, never the key, the market's CNPJ, a product or a price.
 */
public record ReceiptAdded(UUID workspaceId, UUID receiptId, UUID actorUserId, String mode, int itemCount) {}
