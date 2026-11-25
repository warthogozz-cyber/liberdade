package br.com.liberdadefamilia.markets.internal;

import java.io.IOException;

/**
 * Why an import was refused. It is an {@link IOException} so a download stream can raise it from {@code read()}. The
 * message is the reason alone: no URL, no row, no name.
 */
public class OsmImportException extends IOException {

    /** The kinds of refusal; the names go in the alert line and in the runbook. */
    public enum Reason {
        SHA_MISMATCH,
        TOO_FEW_ROWS,
        BAD_ROW,
        HTTP,
        TIMEOUT,
        TOO_LARGE,
        BAD_MANIFEST
    }

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public OsmImportException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
