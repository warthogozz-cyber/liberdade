package br.com.liberdadefamilia.ledger.internal;

/**
 * What became of the lines: new transactions, planned ones confirmed, and lines already in the family (imported before,
 * deleted since or sent again), which were left alone.
 */
record ImportResult(int created, int confirmed, int skipped) {}
