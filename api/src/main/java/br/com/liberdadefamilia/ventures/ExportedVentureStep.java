package br.com.liberdadefamilia.ventures;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A step to start a project as the data export carries it. */
public record ExportedVentureStep(
        UUID id, String title, int position, @Nullable Instant doneAt) {}
