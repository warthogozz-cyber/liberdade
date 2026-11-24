package br.com.liberdadefamilia.indicators.internal;

/**
 * The five indicator series, in the order of the cards. The source, unit and name live in the V18 seed; a test checks
 * that both agree. What lives here is how each series behaves.
 */
public enum IndicatorCode {
    SELIC_TARGET(Frequency.DAILY, 2, VariationRule.LAST_CHANGE_PP),
    CDI(Frequency.DAILY, 2, VariationRule.PREVIOUS_PP),
    IPCA_MONTH(Frequency.MONTHLY, 2, VariationRule.PREVIOUS_PP),
    IPCA_12M(Frequency.MONTHLY, 2, VariationRule.PREVIOUS_PP),
    USD_PTAX_SELL(Frequency.DAILY, 4, VariationRule.PREVIOUS_PERCENT);

    /** How often the source publishes a new point. */
    public enum Frequency {
        DAILY,
        MONTHLY
    }

    /** How the variation shown on the card is worked out. */
    public enum VariationRule {
        LAST_CHANGE_PP,
        PREVIOUS_PP,
        PREVIOUS_PERCENT
    }

    private final Frequency frequency;
    private final int decimals;
    private final VariationRule variationRule;

    IndicatorCode(Frequency frequency, int decimals, VariationRule variationRule) {
        this.frequency = frequency;
        this.decimals = decimals;
        this.variationRule = variationRule;
    }

    public Frequency frequency() {
        return frequency;
    }

    public int decimals() {
        return decimals;
    }

    public VariationRule variationRule() {
        return variationRule;
    }
}
