package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.domain.TransactionKind;
import br.com.liberdadefamilia.shared.TextPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A new category, or a subcategory when {@code parentId} is set. The name is stripped before validation. */
record CreateCategoryRequest(
        @NotBlank @Size(max = CreateCategoryRequest.MAX_NAME_LENGTH) @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String name,

        @NotNull TransactionKind kind,

        @Nullable @Schema(nullable = true) @Size(min = 1, max = CreateCategoryRequest.MAX_ICON_LENGTH)
        String icon,

        @Nullable @Schema(nullable = true) CategoryColor color,
        @Nullable @Schema(nullable = true) UUID parentId) {

    static final int MAX_NAME_LENGTH = 40;
    static final int MAX_ICON_LENGTH = 16;

    CreateCategoryRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
