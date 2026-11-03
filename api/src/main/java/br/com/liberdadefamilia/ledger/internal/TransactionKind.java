package br.com.liberdadefamilia.ledger.internal;

/** Money coming in, going out, or moving between two of the family's accounts. */
enum TransactionKind {
    INCOME,
    EXPENSE,
    TRANSFER
}
