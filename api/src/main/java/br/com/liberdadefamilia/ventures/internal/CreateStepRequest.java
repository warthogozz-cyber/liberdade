package br.com.liberdadefamilia.ventures.internal;

import jakarta.validation.constraints.NotNull;

/** A new step; it goes to the end of the list. */
record CreateStepRequest(@NotNull String title) {

    CreateStepRequest {
        if (title != null) {
            title = title.strip();
        }
    }
}
