package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.domain.FounderTheme;
import jakarta.validation.constraints.NotNull;

/** The theme the owner picks. */
record ThemeRequest(@NotNull FounderTheme theme) {}
