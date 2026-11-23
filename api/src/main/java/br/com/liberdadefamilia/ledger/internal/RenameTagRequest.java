package br.com.liberdadefamilia.ledger.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The new name of a tag, stripped before validation: 1 to 30 characters, no control characters. */
record RenameTagRequest(
        @NotBlank @Size(max = TagNamesValidator.MAX_NAME_LENGTH) @Pattern(regexp = "[^\\p{Cc}]*")
        String name) {

    RenameTagRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
