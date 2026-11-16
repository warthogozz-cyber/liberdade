package br.com.liberdadefamilia.identity;

import java.util.UUID;

/** What other modules may know about a person: no email. {@code hasGoogle} tells whether Google can sign them in. */
public record UserSummary(UUID id, String displayName, boolean hasGoogle) {}
