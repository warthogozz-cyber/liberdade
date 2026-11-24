package br.com.liberdadefamilia.live.internal;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.shared.RateLimitedException;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The family's event stream (D6). The family guard has already checked the membership and the role; what is left is
 * the limit, the emitter and the registration. Cleanup of the emitter belongs to the registry, which sets the emitter's
 * callbacks, so none are set here.
 */
@RestController
class LiveController {

    /** The first bytes of every stream: how long a browser waits before it reconnects, and a comment. */
    static final String OPENING = "retry: 5000\n: open\n\n";

    private static final long RETRY_AFTER_SECONDS = 60;

    private final LiveStreams streams;
    private final LiveProperties properties;

    LiveController(LiveStreams streams, LiveProperties properties) {
        this.streams = streams;
        this.properties = properties;
    }

    /** Opens the stream; a browser's {@code EventSource} reads the notices and the comments of the heartbeat. */
    @GetMapping(value = "/v1/workspaces/{ws}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @WorkspaceScoped(allow = {OWNER, ADULT, YOUTH, VIEWER})
    SseEmitter events(WorkspaceMember member, HttpServletRequest request) {
        // Read here, on the request thread: the session is not safe to touch from another one.
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AuthenticationCredentialsNotFoundException("Not signed in");
        }
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        LiveStream stream = new LiveStream(
                UUID.randomUUID(),
                member.workspaceId(),
                member.membershipId(),
                member.userId(),
                member.role(),
                session.getId(),
                emitter);
        if (streams.open(stream).isEmpty()) {
            throw new RateLimitedException(RETRY_AFTER_SECONDS);
        }
        try {
            // Sent before the handler returns, so the browser sees the connection open at once.
            emitter.send(Set.of(new DataWithMediaType(OPENING, MediaType.TEXT_PLAIN)));
        } catch (IOException failure) {
            streams.close(stream);
            throw new UncheckedIOException(failure);
        }
        return emitter;
    }
}
