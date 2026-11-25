package br.com.liberdadefamilia.ventures;

import java.util.UUID;

/**
 * A project was created, edited, had its status changed, its steps changed or was deleted. Published inside the
 * transaction that wrote it.
 */
public record VentureChanged(UUID workspaceId, UUID ventureId) {}
