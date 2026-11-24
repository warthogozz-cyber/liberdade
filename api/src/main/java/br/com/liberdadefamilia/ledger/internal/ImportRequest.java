package br.com.liberdadefamilia.ledger.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The lines of a statement the person confirmed for one account, up to {@value #MAX_ROWS} a call. */
record ImportRequest(
        @NotNull @Size(min = 1, max = ImportRequest.MAX_ROWS)
        List<@NotNull @Valid ImportRow> rows) {

    static final int MAX_ROWS = 500;
}
