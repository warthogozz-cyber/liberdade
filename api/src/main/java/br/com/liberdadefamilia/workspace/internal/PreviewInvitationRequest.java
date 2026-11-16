package br.com.liberdadefamilia.workspace.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token of the link to look at. It goes in the body so that it stays out of URLs and access logs. */
record PreviewInvitationRequest(
        @NotBlank @Size(min = InvitationTokens.TOKEN_LENGTH, max = InvitationTokens.TOKEN_LENGTH)
        String token) {}
