package br.com.liberdadefamilia;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(LiberdadeApplication.class).verify();
    }
}
