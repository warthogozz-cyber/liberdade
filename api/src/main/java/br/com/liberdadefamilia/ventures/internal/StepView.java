package br.com.liberdadefamilia.ventures.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A step to start the project; {@code doneAt} is set once it is done. */
record StepView(
        UUID id,
        String title,
        int position,
        @Nullable @Schema(nullable = true) Instant doneAt) {}
