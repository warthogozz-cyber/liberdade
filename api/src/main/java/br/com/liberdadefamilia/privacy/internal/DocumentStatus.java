package br.com.liberdadefamilia.privacy.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Where one legal document stands for the signed-in person: the version in force and the one they accepted. */
record DocumentStatus(
        ConsentPurpose purpose,
        String currentVersion,
        @Nullable @Schema(nullable = true) String acceptedVersion,
        @Nullable @Schema(nullable = true) Instant acceptedAt,
        boolean acceptanceRequired) {}
