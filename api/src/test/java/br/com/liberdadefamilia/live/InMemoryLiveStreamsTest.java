package br.com.liberdadefamilia.live;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.live.internal.Audience.Everyone;
import br.com.liberdadefamilia.live.internal.InMemoryLiveStreams;
import br.com.liberdadefamilia.live.internal.LiveChange;
import br.com.liberdadefamilia.live.internal.LiveProperties;
import br.com.liberdadefamilia.live.internal.LiveStream;
import br.com.liberdadefamilia.support.Eventually;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.workspace.Role;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class InMemoryLiveStreamsTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    private final InMemoryLiveStreams streams = streams(5, 1000);
    private final UUID ws = TestIds.v7();

    @AfterEach
    void shutDown() {
        streams.destroy();
    }

    private static InMemoryLiveStreams streams(int perPerson, int perProcess) {
        return new InMemoryLiveStreams(
                new LiveProperties(perPerson, perProcess, Duration.ofSeconds(25), Duration.ofMinutes(30)),
                (workspaceId, membershipId, role, accountId, toAccountId) -> true);
    }

    private static LiveStream stream(UUID workspaceId, UUID membershipId, SseEmitter emitter) {
        return new LiveStream(TestIds.v7(), workspaceId, membershipId, TestIds.v7(), Role.ADULT, "session", emitter);
    }

    @Test
    void sixthForSamePersonReplacesTheOldest() {
        UUID person = TestIds.v7();
        TestEmitter oldest = new TestEmitter();
        LiveStream first = stream(ws, person, oldest);
        streams.open(first);
        for (int i = 0; i < 4; i++) {
            assertThat(streams.open(stream(ws, person, new TestEmitter()))).isPresent();
        }

        LiveStream sixth = stream(ws, person, new TestEmitter());
        assertThat(streams.open(sixth)).isPresent();

        assertThat(streams.all()).hasSize(5).contains(sixth).doesNotContain(first);
        Eventually.within(WAIT, () -> assertThat(oldest.completed).isTrue());
        assertThat(String.join("", oldest.sent)).contains("replaced");
        // Another person of the same family is not held back by it.
        assertThat(streams.open(stream(ws, TestIds.v7(), new TestEmitter()))).isPresent();
    }

    @Test
    void replacingAGoneStreamStillCompletesIt() {
        UUID person = TestIds.v7();
        TestEmitter gone = new TestEmitter();
        gone.failOnSend = true;
        streams.open(stream(ws, person, gone));
        for (int i = 0; i < 4; i++) {
            streams.open(stream(ws, person, new TestEmitter()));
        }

        assertThat(streams.open(stream(ws, person, new TestEmitter()))).isPresent();

        Eventually.within(WAIT, () -> assertThat(gone.completed).isTrue());
        assertThat(streams.all()).hasSize(5);
    }

    @Test
    void replacingKeepsTheProcessCount() {
        InMemoryLiveStreams small = streams(2, 2);
        try {
            UUID person = TestIds.v7();
            small.open(stream(ws, person, new TestEmitter()));
            small.open(stream(ws, person, new TestEmitter()));

            assertThat(small.open(stream(ws, person, new TestEmitter()))).isPresent();
            assertThat(small.all()).hasSize(2);
            // The process is still full, so someone else is refused.
            assertThat(small.open(stream(ws, TestIds.v7(), new TestEmitter()))).isEmpty();
        } finally {
            small.destroy();
        }
    }

    @Test
    void processLimitRefused() {
        InMemoryLiveStreams small = streams(5, 2);
        try {
            assertThat(small.open(stream(ws, TestIds.v7(), new TestEmitter()))).isPresent();
            assertThat(small.open(stream(TestIds.v7(), TestIds.v7(), new TestEmitter())))
                    .isPresent();

            assertThat(small.open(stream(ws, TestIds.v7(), new TestEmitter()))).isEmpty();
            assertThat(small.all()).hasSize(2);
        } finally {
            small.destroy();
        }
    }

    @Test
    void closedStreamFreesTheSlot() {
        InMemoryLiveStreams small = streams(5, 1);
        try {
            TestEmitter first = new TestEmitter();
            small.open(stream(ws, TestIds.v7(), first));
            assertThat(small.open(stream(ws, TestIds.v7(), new TestEmitter()))).isEmpty();

            first.complete();

            assertThat(small.all()).isEmpty();
            assertThat(small.open(stream(ws, TestIds.v7(), new TestEmitter()))).isPresent();
        } finally {
            small.destroy();
        }
    }

    @Test
    void failedSendRemovesStream() {
        TestEmitter broken = new TestEmitter();
        broken.failOnSend = true;
        streams.open(stream(ws, TestIds.v7(), broken));

        streams.publish(ws, new LiveChange("budget", null), new Everyone());

        Eventually.within(WAIT, () -> assertThat(streams.all()).isEmpty());
        assertThat(broken.completed).isTrue();
    }

    @Test
    void otherFamilyNeverReceives() {
        UUID otherWorkspace = TestIds.v7();
        TestEmitter mine = new TestEmitter();
        TestEmitter theirs = new TestEmitter();
        streams.open(stream(ws, TestIds.v7(), mine));
        streams.open(stream(otherWorkspace, TestIds.v7(), theirs));
        UUID id = TestIds.v7();

        streams.publish(ws, new LiveChange("transaction", id), new Everyone());

        Eventually.within(
                WAIT,
                () -> assertThat(String.join("", mine.sent))
                        .contains("changed", "{\"entity\":\"transaction\",\"id\":\"" + id + "\"}"));
        // The notice for the first family has been delivered by now, so the other one would have it too.
        assertThat(theirs.sent).isEmpty();
    }

    @Test
    void stopCompletesEveryEmitter() {
        TestEmitter a = new TestEmitter();
        TestEmitter b = new TestEmitter();
        streams.open(stream(ws, TestIds.v7(), a));
        streams.open(stream(TestIds.v7(), TestIds.v7(), b));
        streams.start();

        streams.stop();

        assertThat(a.completed).isTrue();
        assertThat(b.completed).isTrue();
        assertThat(streams.all()).isEmpty();
        assertThat(streams.isRunning()).isFalse();
    }

    @Test
    void closeMemberAndWorkspaceCompleteTheirStreams() {
        UUID person = TestIds.v7();
        TestEmitter mine = new TestEmitter();
        TestEmitter other = new TestEmitter();
        streams.open(stream(ws, person, mine));
        streams.open(stream(ws, TestIds.v7(), other));

        streams.closeMember(ws, person);

        assertThat(mine.completed).isTrue();
        assertThat(other.completed).isFalse();
        streams.closeWorkspace(ws);
        assertThat(other.completed).isTrue();
        assertThat(streams.all()).isEmpty();
    }

    /** Records what it is sent, can fail on command, and runs its completion callback the way the container does. */
    private static final class TestEmitter extends SseEmitter {

        final List<String> sent = new CopyOnWriteArrayList<>();
        volatile boolean failOnSend;
        volatile boolean completed;
        private volatile Runnable completion = () -> {};

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failOnSend) {
                throw new IOException("broken pipe");
            }
            builder.build().forEach(part -> sent.add(String.valueOf(part.getData())));
        }

        @Override
        public void onCompletion(Runnable callback) {
            completion = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {}

        @Override
        public void onError(Consumer<Throwable> callback) {}

        @Override
        public synchronized void complete() {
            completed = true;
            completion.run();
        }
    }
}
