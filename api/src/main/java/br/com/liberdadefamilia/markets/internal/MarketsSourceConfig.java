package br.com.liberdadefamilia.markets.internal;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the extract source and the address search by configuration. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MarketsProperties.class)
class MarketsSourceConfig {

    @Bean
    OsmDataset osmDataset(MarketsProperties properties) {
        return properties.osm().fake() ? new FakeOsmDataset() : new GithubOsmDataset(properties);
    }

    @Bean
    Geocoder geocoder(MarketsProperties properties) {
        return properties.geocoding().fake() ? new FakeGeocoder() : new NominatimGeocoder(properties);
    }

    /** The real pause; the tests bring one that does not wait. */
    @Bean
    @ConditionalOnMissingBean
    Sleeper geocodingSleeper() {
        return Thread::sleep;
    }

    @Bean
    InstagramOembed instagramOembed(MarketsProperties properties) {
        return properties.instagram().fake() ? new FakeInstagramOembed() : new MetaInstagramOembed(properties);
    }

    @Bean
    InstagramEmbedCache instagramEmbedCache(Clock clock) {
        return new InstagramEmbedCache(clock, 1000);
    }

    @Bean
    GeocodingThrottle geocodingThrottle(Clock clock, Sleeper sleeper, MarketsProperties properties) {
        return new GeocodingThrottle(clock, sleeper, properties.geocoding().minInterval());
    }

    @Bean
    GeocodingCache geocodingCache(Clock clock) {
        return new GeocodingCache(clock, Duration.ofDays(1), 1000);
    }

    @Bean
    GeocodingLimits geocodingLimits(Clock clock, MarketsProperties properties) {
        return new GeocodingLimits(clock, properties.geocoding().dailyLimit());
    }
}
