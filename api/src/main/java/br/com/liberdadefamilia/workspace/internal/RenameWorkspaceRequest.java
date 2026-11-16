package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.shared.TextPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The family's new name, stripped before validation. */
record RenameWorkspaceRequest(
        @NotBlank @Size(max = WorkspaceController.MAX_NAME_LENGTH) @Pattern(regexp = TextPatterns.WITHOUT_NUL)
        String name) {

    RenameWorkspaceRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
