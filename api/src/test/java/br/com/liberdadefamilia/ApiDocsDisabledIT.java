package br.com.liberdadefamilia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Outside the dev and test profiles the contract is not served (D32). Its own context, for the property. */
@SpringBootTest(properties = "springdoc.api-docs.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiDocsDisabledIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void contractIsNotServed() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(404);
    }
}
