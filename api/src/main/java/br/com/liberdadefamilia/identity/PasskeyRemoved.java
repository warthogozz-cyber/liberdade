package br.com.liberdadefamilia.identity;

import java.util.UUID;

/** A person removed one of their own passkeys. Published inside the removing transaction. */
public record PasskeyRemoved(UUID userId) {}
