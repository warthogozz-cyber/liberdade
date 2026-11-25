package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.ventures.domain.VentureType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * A new project. With a {@code templateKey} the type comes from the model and {@code type} is ignored; without one,
 * {@code type} is required. The name is stripped before it is checked (1 to 40 characters, by the service).
 */
record CreateVentureRequest(
        @Nullable @Schema(nullable = true) String templateKey,
        @NotNull String name,
        @Nullable @Schema(nullable = true) VentureType type) {

    CreateVentureRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
