package br.com.liberdadefamilia.markets.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the extract source by configuration. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MarketsProperties.class)
class MarketsSourceConfig {

    @Bean
    OsmDataset osmDataset(MarketsProperties properties) {
        return properties.osm().fake() ? new FakeOsmDataset() : new GithubOsmDataset(properties);
    }
}
