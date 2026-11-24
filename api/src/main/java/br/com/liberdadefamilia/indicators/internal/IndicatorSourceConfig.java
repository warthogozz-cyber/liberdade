package br.com.liberdadefamilia.indicators.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the source by configuration and provides the real sleeper. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IndicatorProperties.class)
class IndicatorSourceConfig {

    @Bean
    IndicatorSource indicatorSource(IndicatorProperties properties, Sleeper sleeper) {
        return properties.fake() ? new FakeIndicatorSource() : new BcbIndicatorSource(properties, sleeper);
    }

    @Bean
    Sleeper indicatorSleeper() {
        return Thread::sleep;
    }
}
