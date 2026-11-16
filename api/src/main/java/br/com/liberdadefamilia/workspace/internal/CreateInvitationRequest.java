package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.TextPatterns;
import br.com.liberdadefamilia.workspace.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A link for one new person. The nickname is what the family will call them; it is stripped before validation.
 * {@code parentalConsent} only counts for YOUTH, where it must be true.
 */
record CreateInvitationRequest(
        @NotNull Role role,

        @NotBlank @Size(max = CreateInvitationRequest.MAX_NICKNAME_LENGTH) @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String nickname,

        @Nullable @Schema(nullable = true) Boolean parentalConsent) {

    static final int MAX_NICKNAME_LENGTH = 40;

    CreateInvitationRequest {
        if (nickname != null) {
            nickname = nickname.strip();
        }
    }
}
