package br.com.liberdadefamilia.live.internal;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * The open streams and the way a notice reaches them. Callers never see how they are kept, so a version that fans out
 * through the database can take the place of the in-memory one (D5).
 */
public interface LiveStreams {

    /** Registers the stream; empty when the person or the process is over its limit. */
    Optional<LiveStream> open(LiveStream stream);

    /** Sends the notice, off the calling thread, to the family's streams the audience includes. */
    void publish(UUID workspaceId, LiveChange change, Audience audience);

    /** Closes the streams of one membership. */
    void closeMember(UUID workspaceId, UUID membershipId);

    /** Closes every stream of the family. */
    void closeWorkspace(UUID workspaceId);

    /** A snapshot of every open stream. */
    Collection<LiveStream> all();

    /** Takes the stream out and completes its emitter. */
    void close(LiveStream stream);
}
