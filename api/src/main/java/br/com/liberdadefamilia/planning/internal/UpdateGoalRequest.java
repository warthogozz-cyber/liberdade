package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.TextPatterns;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import org.jspecify.annotations.Nullable;

/**
 * A partial change to a goal, with the version the caller read: what is null stays as it is, so the due month can be
 * moved but not removed. The linked account never changes after the goal is created. The name is stripped before
 * validation. Not a {@code PartialRequest}: the version is required, so the contract lists it and leaves every other
 * field optional.
 */
record UpdateGoalRequest(
        @Nullable
        @Schema(nullable = true)
        @Size(min = 1, max = CreateGoalRequest.MAX_NAME_LENGTH)
        @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String name,

        @Nullable @Schema(nullable = true) Money targetAmount,
        @Nullable @Schema(nullable = true) YearMonth dueOn,
        @Nullable @Schema(nullable = true) String icon,
        @NotNull Long version) {

    UpdateGoalRequest {
        if (name != null) {
            name = name.strip();
        }
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue
    boolean isNotEmpty() {
        return name != null || targetAmount != null || dueOn != null || icon != null;
    }
}
