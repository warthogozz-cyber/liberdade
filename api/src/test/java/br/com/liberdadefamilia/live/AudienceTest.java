package br.com.liberdadefamilia.live;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.ledger.AccountVisibility;
import br.com.liberdadefamilia.live.internal.Audience;
import br.com.liberdadefamilia.live.internal.Audience.AccountViewers;
import br.com.liberdadefamilia.live.internal.Audience.Everyone;
import br.com.liberdadefamilia.live.internal.Audience.NoYouth;
import br.com.liberdadefamilia.live.internal.LiveStream;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.workspace.Role;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AudienceTest {

    private final UUID ws = TestIds.v7();
    private final UUID account = TestIds.v7();
    private final AtomicInteger asked = new AtomicInteger();

    private AccountVisibility visibility(boolean answer) {
        return (workspaceId, membershipId, role, accountId, toAccountId) -> {
            asked.incrementAndGet();
            return answer;
        };
    }

    private LiveStream stream(Role role) {
        return new LiveStream(TestIds.v7(), ws, TestIds.v7(), TestIds.v7(), role, "session", new SseEmitter());
    }

    @Test
    void everyoneIncludesYouth() {
        assertThat(new Everyone().includes(stream(Role.YOUTH), visibility(false)))
                .isTrue();
    }

    @Test
    void noYouthExcludesOnlyYouth() {
        Audience audience = new NoYouth();

        assertThat(audience.includes(stream(Role.VIEWER), visibility(false))).isTrue();
        assertThat(audience.includes(stream(Role.YOUTH), visibility(true))).isFalse();
    }

    @Test
    void accountViewersAsksVisibilityOnlyForYouth() {
        Audience audience = new AccountViewers(account, null, Set.of());

        assertThat(audience.includes(stream(Role.ADULT), visibility(false))).isTrue();
        assertThat(asked).hasValue(0);

        assertThat(audience.includes(stream(Role.YOUTH), visibility(true))).isTrue();
        assertThat(audience.includes(stream(Role.YOUTH), visibility(false))).isFalse();
        assertThat(asked).hasValue(2);
    }

    @Test
    void extraMembershipIsIncluded() {
        LiveStream youth = stream(Role.YOUTH);
        Audience audience = new AccountViewers(account, null, Set.of(youth.membershipId()));

        assertThat(audience.includes(youth, visibility(false))).isTrue();
        assertThat(asked).hasValue(0);
    }
}
