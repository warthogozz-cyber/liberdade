package br.com.liberdadefamilia.ledger.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The descriptions of a statement, up to {@value #MAX_ITEMS} a call. */
record CategoryHintsRequest(
        @NotNull @Size(min = 1, max = CategoryHintsRequest.MAX_ITEMS)
        List<@Valid CategoryHintItem> items) {

    static final int MAX_ITEMS = 500;
}
