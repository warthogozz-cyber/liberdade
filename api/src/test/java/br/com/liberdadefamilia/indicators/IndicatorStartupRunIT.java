package br.com.liberdadefamilia.indicators;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.indicators.internal.IndicatorJob;
import br.com.liberdadefamilia.indicators.internal.IndicatorRepository;
import br.com.liberdadefamilia.indicators.internal.IndicatorStartupRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The startup collection is built here by hand, with the real repository and a stand-in for the job, so nothing runs
 * in the background while the test asserts. The test profile keeps scheduling (and the real bean) off.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IndicatorStartupRunIT {

    @Autowired
    IndicatorRepository repository;

    @Autowired
    JdbcClient jdbc;

    private IndicatorJob job;
    private IndicatorStartupRun startup;

    /** The tables are shared with the other ITs: leave them as V18 seeded them. */
    @AfterEach
    void restoreSeed() {
        jdbc.sql("delete from indicator_points").update();
        jdbc.sql("update indicator_series set last_success_at = null, last_failure_at = null, failure_streak = 0")
                .update();
    }

    @BeforeEach
    void setUp() {
        jdbc.sql("update indicator_series set last_success_at = null").update();
        job = mock(IndicatorJob.class);
        startup = new IndicatorStartupRun(repository, job);
    }

    @Test
    void collectsWhenASeriesNeverSucceeded() {
        jdbc.sql("update indicator_series set last_success_at = now() where code <> 'CDI'")
                .update();

        startup.collectIfNeverSucceeded();

        verify(job).runOnce(false);
    }

    @Test
    void skipsWhenAllSucceeded() {
        jdbc.sql("update indicator_series set last_success_at = now()").update();

        startup.collectIfNeverSucceeded();

        verify(job, never()).runOnce(anyBoolean());
    }
}
