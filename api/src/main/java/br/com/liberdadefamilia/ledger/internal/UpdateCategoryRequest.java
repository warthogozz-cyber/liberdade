package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.PartialRequest;
import br.com.liberdadefamilia.shared.TextPatterns;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** A partial change to a category: what is null stays as it is. The name is stripped before validation. */
@PartialRequest
record UpdateCategoryRequest(
        @Nullable
        @Schema(nullable = true)
        @Size(min = 1, max = CreateCategoryRequest.MAX_NAME_LENGTH)
        @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String name,

        @Nullable @Schema(nullable = true) @Size(min = 1, max = CreateCategoryRequest.MAX_ICON_LENGTH)
        String icon,

        @Nullable @Schema(nullable = true) CategoryColor color) {

    UpdateCategoryRequest {
        if (name != null) {
            name = name.strip();
        }
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue
    boolean isNotEmpty() {
        return name != null || icon != null || color != null;
    }
}
