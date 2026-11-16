package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.PartialRequest;
import br.com.liberdadefamilia.shared.TextPatterns;
import br.com.liberdadefamilia.workspace.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A partial change to a member: what is null stays as it is. {@code parentalConsent} only counts when the role becomes
 * YOUTH, where it must be true. The nickname is stripped before validation.
 */
@PartialRequest
record UpdateMemberRequest(
        @Nullable @Schema(nullable = true) Role role,

        @Nullable
        @Schema(nullable = true)
        @Size(min = 1, max = UpdateMemberRequest.MAX_NICKNAME_LENGTH)
        @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String nickname,

        @Nullable @Schema(nullable = true) Boolean parentalConsent) {

    static final int MAX_NICKNAME_LENGTH = 40;

    UpdateMemberRequest {
        if (nickname != null) {
            nickname = nickname.strip();
        }
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue
    boolean isNotEmpty() {
        return role != null || nickname != null || parentalConsent != null;
    }
}
