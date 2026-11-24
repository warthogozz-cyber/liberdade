package br.com.liberdadefamilia.planning.internal;

/** Where the monthly cost of the reserve comes from: the last three months, the family's own figure, or nowhere. */
enum CostSource {
    HISTORY,
    MANUAL,
    NONE
}
