package br.com.liberdadefamilia.live.internal;

import br.com.liberdadefamilia.identity.SessionLiveness;
import br.com.liberdadefamilia.workspace.Memberships;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Every beat (D7) looks at all the open streams with one query on the sessions and one on the memberships, closes the
 * ones whose person signed out or left the family, and writes a comment to the rest, which keeps proxies from closing a
 * quiet connection and shows a broken one. The job logs nothing about who was closed.
 */
@Component
public class LiveHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(LiveHeartbeat.class);

    private final LiveStreams streams;
    private final SessionLiveness sessions;
    private final Memberships memberships;

    LiveHeartbeat(LiveStreams streams, SessionLiveness sessions, Memberships memberships) {
        this.streams = streams;
        this.sessions = sessions;
        this.memberships = memberships;
    }

    @Scheduled(fixedRateString = "${liberdade.live.heartbeat}")
    public void beat() {
        try {
            check();
        } catch (RuntimeException failure) {
            log.warn("live heartbeat failed ({})", failure.getClass().getName());
        }
    }

    private void check() {
        Collection<LiveStream> open = streams.all();
        if (open.isEmpty()) {
            return;
        }
        Set<String> aliveSessions = sessions.aliveAmong(
                open.stream().map(LiveStream::sessionId).distinct().toList());
        Set<UUID> activeMemberships = memberships.activeAmong(
                open.stream().map(LiveStream::membershipId).distinct().toList());
        for (LiveStream stream : open) {
            if (!aliveSessions.contains(stream.sessionId()) || !activeMemberships.contains(stream.membershipId())) {
                streams.close(stream);
                continue;
            }
            try {
                stream.emitter().send(SseEmitter.event().comment(""));
            } catch (IOException | RuntimeException failure) {
                streams.close(stream);
            }
        }
    }
}
