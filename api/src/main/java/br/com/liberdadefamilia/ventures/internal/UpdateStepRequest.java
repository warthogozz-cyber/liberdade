package br.com.liberdadefamilia.ventures.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** What changes in a step: any of the title, the done mark and the position (0 is first). At least one comes. */
record UpdateStepRequest(
        @Nullable @Schema(nullable = true) String title,
        @Nullable @Schema(nullable = true) Boolean done,
        @Nullable @Schema(nullable = true) Integer position) {

    UpdateStepRequest {
        if (title != null) {
            title = title.strip();
        }
    }
}
