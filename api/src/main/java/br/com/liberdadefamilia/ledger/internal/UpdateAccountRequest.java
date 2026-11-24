package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PartialRequest;
import br.com.liberdadefamilia.shared.TextPatterns;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A partial change to an account: what is null stays as it is. {@code initialBalance} sets the starting balance;
 * {@code balance} sets what the account has now, and the service works the starting balance out from it. Only one of
 * them may come. {@code isReserve} marks or unmarks the account as part of the emergency reserve. The name is stripped
 * before validation.
 */
@PartialRequest
record UpdateAccountRequest(
        @Nullable
        @Schema(nullable = true)
        @Size(min = 1, max = CreateAccountRequest.MAX_NAME_LENGTH)
        @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String name,

        @Nullable @Schema(nullable = true) AccountType type,
        @Nullable @Schema(nullable = true) @BalanceAmount Money initialBalance,
        @Nullable @Schema(nullable = true) @BalanceAmount Money balance,
        @Nullable @Schema(nullable = true) Boolean isReserve) {

    UpdateAccountRequest {
        if (name != null) {
            name = name.strip();
        }
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue
    boolean isOneBalanceAtMost() {
        return initialBalance == null || balance == null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue
    boolean isNotEmpty() {
        return name != null || type != null || initialBalance != null || balance != null || isReserve != null;
    }
}
