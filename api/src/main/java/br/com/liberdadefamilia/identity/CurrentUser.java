package br.com.liberdadefamilia.identity;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The signed-in person. Any controller gets it by declaring a parameter of this type.
 *
 * @param familyNameHint the family name Google sent, used to suggest a family name; {@code null} for passkey sign-ins
 */
public record CurrentUser(
        UUID id, String displayName, @Nullable String familyNameHint) {}
