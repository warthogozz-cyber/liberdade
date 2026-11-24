package br.com.liberdadefamilia.ledger.internal;

import java.util.List;

/** One hint for each item of the request, in the same order. */
record CategoryHintsResponse(List<CategoryHint> hints) {}
