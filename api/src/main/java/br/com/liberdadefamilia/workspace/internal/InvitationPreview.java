package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.InvitationKind;
import br.com.liberdadefamilia.workspace.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** What the person sees before taking the link: which family, who asked, and as what. */
record InvitationPreview(
        String workspaceName,
        @Nullable @Schema(nullable = true) String invitedBy,
        Role role,
        @Nullable @Schema(nullable = true) String nickname,
        InvitationKind kind) {}
