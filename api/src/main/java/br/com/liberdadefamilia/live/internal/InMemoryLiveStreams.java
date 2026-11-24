package br.com.liberdadefamilia.live.internal;

import br.com.liberdadefamilia.ledger.AccountVisibility;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Keeps the streams of each family in a map (D5). A family's list is replaced whole under the map's per-key lock, so
 * counting a person's streams and adding one is a single step and readers iterate a snapshot. The counter of the whole
 * process moves inside the same step.
 *
 * <p>Sending happens on virtual threads the bean owns, one task per stream, so a slow browser never holds the thread
 * that committed a write nor the other streams. A stream whose send fails is taken out and completed. At shutdown
 * every emitter is completed, so a deploy does not wait for open streams.
 *
 * <p>A person over their limit gives up their oldest stream instead of being refused: a reloaded page leaves its
 * stream behind until the next write fails, and refusing would leave the new page without one.
 */
@Component
@EnableConfigurationProperties(LiveProperties.class)
public class InMemoryLiveStreams implements LiveStreams, SmartLifecycle, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(InMemoryLiveStreams.class);

    private final LiveProperties properties;
    private final AccountVisibility visibility;
    private final ConcurrentMap<UUID, List<LiveStream>> byWorkspace = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running;

    public InMemoryLiveStreams(LiveProperties properties, AccountVisibility visibility) {
        this.properties = properties;
        this.visibility = visibility;
    }

    @Override
    public Optional<LiveStream> open(LiveStream stream) {
        AtomicBoolean accepted = new AtomicBoolean();
        AtomicReference<LiveStream> replaced = new AtomicReference<>();
        byWorkspace.compute(stream.workspaceId(), (key, current) -> {
            List<LiveStream> streams = current == null ? List.of() : current;
            List<LiveStream> ofPerson = streams.stream()
                    .filter(s -> s.membershipId().equals(stream.membershipId()))
                    .toList();
            List<LiveStream> next = new ArrayList<>(streams);
            if (!ofPerson.isEmpty() && ofPerson.size() >= properties.maxPerPerson()) {
                // A page that reloaded or closed keeps its stream until the next write shows it gone, so the
                // person's oldest stream makes room; the slot passes from one to the other.
                next.remove(ofPerson.getFirst());
                replaced.set(ofPerson.getFirst());
            } else if (!reserveSlot()) {
                return current;
            }
            accepted.set(true);
            next.add(stream);
            return List.copyOf(next);
        });
        if (!accepted.get()) {
            return Optional.empty();
        }
        if (replaced.get() != null) {
            replace(replaced.get());
        }
        stream.emitter().onCompletion(() -> remove(stream));
        stream.emitter().onTimeout(() -> close(stream));
        stream.emitter().onError(failure -> remove(stream));
        return Optional.of(stream);
    }

    @Override
    public void publish(UUID workspaceId, LiveChange change, Audience audience) {
        List<LiveStream> streams = byWorkspace.get(workspaceId);
        if (streams == null) {
            return;
        }
        String json = change.toJson();
        for (LiveStream stream : streams) {
            try {
                executor.execute(() -> deliver(stream, json, audience));
            } catch (RejectedExecutionException closed) {
                return;
            }
        }
    }

    @Override
    public void closeMember(UUID workspaceId, UUID membershipId) {
        complete(removeWhere(workspaceId, s -> s.membershipId().equals(membershipId)));
    }

    @Override
    public void closeWorkspace(UUID workspaceId) {
        complete(removeWhere(workspaceId, s -> true));
    }

    @Override
    public Collection<LiveStream> all() {
        List<LiveStream> everything = new ArrayList<>();
        byWorkspace.values().forEach(everything::addAll);
        return everything;
    }

    @Override
    public void close(LiveStream stream) {
        remove(stream);
        complete(List.of(stream));
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        List<LiveStream> everything = new ArrayList<>();
        for (UUID workspaceId : byWorkspace.keySet()) {
            everything.addAll(removeWhere(workspaceId, s -> true));
        }
        complete(everything);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    private void deliver(LiveStream stream, String json, Audience audience) {
        try {
            if (!audience.includes(stream, visibility)) {
                return;
            }
        } catch (RuntimeException failure) {
            log.warn("live audience check failed ({})", failure.getClass().getName());
            return;
        }
        try {
            stream.emitter().send(SseEmitter.event().name("changed").data(json, MediaType.APPLICATION_JSON));
        } catch (IOException | RuntimeException failure) {
            // A closed or broken connection: the browser reconnects and refetches everything.
            close(stream);
        }
    }

    /**
     * Tells the replaced stream's page to stop reconnecting, so a person with more open tabs than the limit does not
     * see them take each other's place, and completes it. Off the opening request's thread, like every other send.
     */
    private void replace(LiveStream stream) {
        try {
            executor.execute(() -> {
                try {
                    stream.emitter().send(SseEmitter.event().name("replaced").data("{}", MediaType.APPLICATION_JSON));
                } catch (IOException | RuntimeException gone) {
                    // The page is already gone, which is the usual case.
                }
                complete(List.of(stream));
            });
        } catch (RejectedExecutionException closed) {
            complete(List.of(stream));
        }
    }

    private boolean reserveSlot() {
        int now;
        do {
            now = total.get();
            if (now >= properties.maxStreams()) {
                return false;
            }
        } while (!total.compareAndSet(now, now + 1));
        return true;
    }

    private void remove(LiveStream stream) {
        removeWhere(stream.workspaceId(), s -> s.streamId().equals(stream.streamId()));
    }

    /** Takes out the matching streams of the family and frees their slots; the caller completes them. */
    private List<LiveStream> removeWhere(UUID workspaceId, Predicate<LiveStream> matches) {
        List<LiveStream> removed = new ArrayList<>();
        byWorkspace.computeIfPresent(workspaceId, (key, current) -> {
            List<LiveStream> kept = new ArrayList<>();
            for (LiveStream stream : current) {
                (matches.test(stream) ? removed : kept).add(stream);
            }
            if (removed.isEmpty()) {
                return current;
            }
            total.addAndGet(-removed.size());
            return kept.isEmpty() ? null : List.copyOf(kept);
        });
        return removed;
    }

    private void complete(Collection<LiveStream> streams) {
        for (LiveStream stream : streams) {
            try {
                stream.emitter().complete();
            } catch (RuntimeException failure) {
                log.debug("live emitter already closed ({})", failure.getClass().getName());
            }
        }
    }
}
