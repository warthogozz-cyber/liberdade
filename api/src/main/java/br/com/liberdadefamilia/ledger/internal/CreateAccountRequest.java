package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.TextPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A new account. The name is stripped before validation. The currency is the one of the initial balance. */
record CreateAccountRequest(
        @NotBlank @Size(max = CreateAccountRequest.MAX_NAME_LENGTH) @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String name,

        @NotNull AccountType type,
        @NotNull @BalanceAmount Money initialBalance,

        @Nullable @Schema(nullable = true) UUID ownerMembershipId) {

    static final int MAX_NAME_LENGTH = 60;

    CreateAccountRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
