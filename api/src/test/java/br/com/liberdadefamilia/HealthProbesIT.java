package br.com.liberdadefamilia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HealthProbesIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void livenessIsUp() {
        assertThat(mvc.get().uri("/actuator/health/liveness"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void readinessIsUpWithDatabase() {
        assertThat(mvc.get().uri("/actuator/health/readiness"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void healthHidesDetails() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk().bodyJson().doesNotHavePath("$.components");
    }

    @Test
    void envIsNotExposed() {
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(404);
    }
}
