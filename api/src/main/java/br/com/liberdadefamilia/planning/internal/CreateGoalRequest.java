package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.TextPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A new goal. {@code icon} is one of the fixed keys; {@code linkedAccountId}, when given, makes the goal follow that
 * account's balance for good. The name is stripped before validation.
 */
record CreateGoalRequest(
        @NotBlank @Size(max = CreateGoalRequest.MAX_NAME_LENGTH) @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String name,

        @NotNull Money targetAmount,
        @Nullable @Schema(nullable = true) YearMonth dueOn,
        @NotNull String icon,
        @Nullable @Schema(nullable = true) UUID linkedAccountId) {

    static final int MAX_NAME_LENGTH = 40;

    CreateGoalRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
