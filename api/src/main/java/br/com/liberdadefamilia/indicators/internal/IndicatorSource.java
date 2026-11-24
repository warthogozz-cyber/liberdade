package br.com.liberdadefamilia.indicators.internal;

import java.time.LocalDate;
import java.util.List;

/** Where the indicator values come from: the Banco Central in production, fixed values elsewhere. */
public interface IndicatorSource {

    /**
     * The points of one series inside {@code [from, to]}, in ascending date order. An empty window is an empty list;
     * deciding what that means is the caller's job.
     */
    List<FetchedPoint> fetch(IndicatorCode code, String sourceRef, LocalDate from, LocalDate to)
            throws IndicatorFetchException;
}
