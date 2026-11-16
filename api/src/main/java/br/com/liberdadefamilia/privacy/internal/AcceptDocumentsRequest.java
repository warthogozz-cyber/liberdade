package br.com.liberdadefamilia.privacy.internal;

import jakarta.validation.constraints.NotBlank;

/** The versions the person read and accepts. Both must be the ones in force. */
record AcceptDocumentsRequest(
        @NotBlank String terms, @NotBlank String privacy) {}
