package br.com.liberdadefamilia.privacy.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A consent as its subject or its granter sees it. {@code subjectName} is the youth's nickname and
 * {@code grantedByName} the guardian's, both in that family; null once the account is gone.
 */
record ConsentView(
        UUID id,
        ConsentPurpose purpose,
        String documentVersion,
        UUID workspaceId,
        String workspaceName,
        @Nullable @Schema(nullable = true) String subjectName,
        @Nullable @Schema(nullable = true) String grantedByName,
        Instant grantedAt,
        @Nullable @Schema(nullable = true) Instant revokedAt,
        @Nullable @Schema(nullable = true) Instant supersededAt,
        boolean canRevoke) {}
