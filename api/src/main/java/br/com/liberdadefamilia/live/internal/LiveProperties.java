package br.com.liberdadefamilia.live.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the live streams are limited and paced.
 *
 * @param maxPerPerson streams one membership may hold open at once
 * @param maxStreams streams the whole process may hold open at once
 * @param heartbeat how often the open streams are checked and pinged
 * @param streamTimeout how long a stream lives before the browser reconnects
 */
@ConfigurationProperties("liberdade.live")
public record LiveProperties(int maxPerPerson, int maxStreams, Duration heartbeat, Duration streamTimeout) {

    public LiveProperties {
        maxPerPerson = maxPerPerson > 0 ? maxPerPerson : 5;
        maxStreams = maxStreams > 0 ? maxStreams : 1000;
        heartbeat = heartbeat == null ? Duration.ofSeconds(25) : heartbeat;
        streamTimeout = streamTimeout == null ? Duration.ofMinutes(30) : streamTimeout;
    }
}
