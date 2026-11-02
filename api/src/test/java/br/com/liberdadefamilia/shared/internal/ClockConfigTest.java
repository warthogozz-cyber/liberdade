package br.com.liberdadefamilia.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void clockRunsInUtc() {
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
