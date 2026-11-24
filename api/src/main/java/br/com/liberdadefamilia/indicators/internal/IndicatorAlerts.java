package br.com.liberdadefamilia.indicators.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Tells the person watching the logs that a series needs a look. The lines carry the series code and no value. */
@Component
public class IndicatorAlerts {

    private static final Logger log = LoggerFactory.getLogger("br.com.liberdadefamilia.indicators.alerts");

    public void fetchFailed(IndicatorCode code) {
        log.warn("INDICATOR_ALERT type=FETCH_FAILED series={}", code);
    }

    public void stale(IndicatorCode code) {
        log.warn("INDICATOR_ALERT type=STALE series={}", code);
    }

    public void run(int ok, int failed) {
        log.info("INDICATOR_RUN ok={} failed={}", ok, failed);
    }
}
