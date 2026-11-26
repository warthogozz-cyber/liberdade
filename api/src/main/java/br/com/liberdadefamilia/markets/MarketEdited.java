package br.com.liberdadefamilia.markets;

import java.util.UUID;

/**
 * The family changed a market. Published in the transaction that wrote it. It says what kind of change it was and
 * never what the new values are: no link, position or name travels in it.
 */
public record MarketEdited(UUID workspaceId, UUID marketId, UUID actorUserId, Change change) {

    /** What the family changed. */
    public enum Change {
        LINKS,
        POSITION,
        ARCHIVE,
        CONVERT,
        CNPJ
    }
}
