package br.com.liberdadefamilia.live.internal;

import br.com.liberdadefamilia.workspace.Role;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * One open connection of one person. The session id is read once, on the request thread, and only compared with the
 * live sessions afterwards; it never reaches a log.
 */
public record LiveStream(
        UUID streamId,
        UUID workspaceId,
        UUID membershipId,
        UUID userId,
        Role role,
        String sessionId,
        SseEmitter emitter) {

    @Override
    public String toString() {
        return "LiveStream[" + streamId + "]";
    }
}
