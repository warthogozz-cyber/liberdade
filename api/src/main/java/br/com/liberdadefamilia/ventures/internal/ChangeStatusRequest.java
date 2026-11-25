package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.ventures.domain.VentureStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** A status change. {@code on} is the day it started or ended; it defaults to today in the family's time zone. */
record ChangeStatusRequest(
        @NotNull Long version,
        @NotNull VentureStatus status,
        @Nullable @Schema(nullable = true) LocalDate on) {}
