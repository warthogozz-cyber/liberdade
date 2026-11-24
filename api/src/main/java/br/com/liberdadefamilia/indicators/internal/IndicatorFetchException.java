package br.com.liberdadefamilia.indicators.internal;

/** A fetch that did not give usable points. The message is only the kind: never a value and never a URL. */
public class IndicatorFetchException extends Exception {

    /** Why the fetch failed. */
    public enum Kind {
        TIMEOUT,
        IO,
        HTTP_STATUS,
        BAD_JSON,
        EMPTY,
        OUT_OF_RANGE
    }

    private final Kind kind;

    public IndicatorFetchException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
