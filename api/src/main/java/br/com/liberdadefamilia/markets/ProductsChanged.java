package br.com.liberdadefamilia.markets;

import java.util.UUID;

/** The family renamed a product or changed its unit. Published in the transaction that wrote it; it carries no name. */
public record ProductsChanged(UUID workspaceId) {}
