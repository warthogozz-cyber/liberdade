package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.TextPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** The token of the link, and who the new person is when nobody is signed in. */
record AcceptInvitationRequest(
        @NotBlank @Size(min = InvitationTokens.TOKEN_LENGTH, max = InvitationTokens.TOKEN_LENGTH)
        String token,

        @Nullable @Valid @Schema(nullable = true) NewUser newUser) {

    /** @param displayName how the person is called on their own account */
    record NewUser(
            @NotBlank @Size(max = 200) @Pattern(regexp = TextPatterns.WITHOUT_NUL)
            String displayName) {}
}
