package br.com.liberdadefamilia.live.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedRateTask;

/**
 * Scheduling is off in the tests, so this lets Spring read the real annotation on {@link LiveHeartbeat#beat()} the way
 * it does at startup: the configured value {@code 25s}, in the simple duration format, must become 25 seconds.
 */
class HeartbeatScheduleTest {

    @Test
    void simpleDurationFormatIsParsedAsTheFixedRate() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource("test", Map.of("liberdade.live.heartbeat", "25s")));
            // Only the class is registered; its collaborators are never needed, because no beat runs.
            context.registerBean(LiveHeartbeat.class, () -> new LiveHeartbeat(null, null, null));
            context.register(Scheduling.class);
            context.refresh();

            var tasks =
                    context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
            assertThat(tasks).hasSize(1);
            assertThat(tasks.iterator().next().getTask())
                    .isInstanceOf(FixedRateTask.class)
                    .extracting(task -> ((FixedRateTask) task).getIntervalDuration())
                    .isEqualTo(Duration.ofSeconds(25));
        }
    }

    @EnableScheduling
    static class Scheduling {}
}
