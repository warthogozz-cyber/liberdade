package br.com.liberdadefamilia.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.ledger.AccountOwnerChanged;
import br.com.liberdadefamilia.ledger.TransactionChanged;
import br.com.liberdadefamilia.live.internal.Audience;
import br.com.liberdadefamilia.live.internal.Audience.AccountViewers;
import br.com.liberdadefamilia.live.internal.Audience.Everyone;
import br.com.liberdadefamilia.live.internal.Audience.NoYouth;
import br.com.liberdadefamilia.live.internal.LiveChange;
import br.com.liberdadefamilia.live.internal.LiveStream;
import br.com.liberdadefamilia.live.internal.LiveStreams;
import br.com.liberdadefamilia.markets.ProductsChanged;
import br.com.liberdadefamilia.markets.ProductsMerged;
import br.com.liberdadefamilia.markets.ShoppingListChanged;
import br.com.liberdadefamilia.planning.BudgetChanged;
import br.com.liberdadefamilia.planning.GoalChanged;
import br.com.liberdadefamilia.planning.ReserveCostChanged;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.MemberProfileChanged;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** The relay turns an event into a notice only once its transaction commits, and only where the family wants it. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LiveRelayIT.Config.class})
class LiveRelayIT {

    record Published(UUID workspaceId, LiveChange change, Audience audience) {}

    /** Stands in for the registry and remembers what the relay asked it to publish. */
    static class SpyStreams implements LiveStreams {

        final List<Published> published = new CopyOnWriteArrayList<>();

        @Override
        public void publish(UUID workspaceId, LiveChange change, Audience audience) {
            published.add(new Published(workspaceId, change, audience));
        }

        @Override
        public Optional<LiveStream> open(LiveStream stream) {
            return Optional.empty();
        }

        @Override
        public void closeMember(UUID workspaceId, UUID membershipId) {}

        @Override
        public void closeWorkspace(UUID workspaceId) {}

        @Override
        public Collection<LiveStream> all() {
            return List.of();
        }

        @Override
        public void close(LiveStream stream) {}
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        SpyStreams spyStreams() {
            return new SpyStreams();
        }
    }

    @Autowired
    SpyStreams spy;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    private UUID ana;
    private UUID ws;

    @BeforeEach
    void family() {
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        setPlanning(true);
        spy.published.clear();
    }

    private void setPlanning(boolean enabled) {
        jdbc.sql("""
                        insert into workspace_modules (workspace_id, module_key, enabled, updated_by)
                        values (:ws, 'planning', :enabled, :ana)
                        on conflict (workspace_id, module_key) do update set enabled = :enabled, updated_by = :ana""")
                .param("ws", ws)
                .param("enabled", enabled)
                .param("ana", ana)
                .update();
    }

    private void publishInTransaction(Object... published) {
        tx.executeWithoutResult(status -> {
            for (Object event : published) {
                events.publishEvent(event);
            }
        });
    }

    @Test
    void transactionBecomesChange() {
        UUID id = TestIds.v7();
        UUID account = TestIds.v7();

        publishInTransaction(new TransactionChanged(ws, id, account, null));

        assertThat(spy.published)
                .containsExactly(new Published(
                        ws, new LiveChange("transaction", id), new AccountViewers(account, null, Set.of())));
    }

    @Test
    void rolledBackWriteSendsNothing() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    events.publishEvent(new TransactionChanged(ws, TestIds.v7(), TestIds.v7(), null));
                    throw new IllegalStateException("the write failed");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(spy.published).isEmpty();
    }

    @Test
    void planningOffSkipsPlanningEvents() {
        setPlanning(false);

        publishInTransaction(
                new GoalChanged(ws, TestIds.v7()),
                new BudgetChanged(ws, YearMonth.of(2026, 11)),
                new ReserveCostChanged(ws, ana));
        assertThat(spy.published).isEmpty();

        // The events of the other modules do not depend on Planning.
        publishInTransaction(new MemberProfileChanged(ws, TestIds.v7()));
        assertThat(spy.published).containsExactly(new Published(ws, new LiveChange("members", null), new Everyone()));
    }

    @Test
    void planningOnSendsPlanningEvents() {
        UUID goal = TestIds.v7();

        publishInTransaction(new GoalChanged(ws, goal), new BudgetChanged(ws, YearMonth.of(2026, 11)));

        assertThat(spy.published)
                .containsExactly(
                        new Published(ws, new LiveChange("goal", goal), new Everyone()),
                        new Published(ws, new LiveChange("budget", null), new NoYouth()));
    }

    @Test
    void ownerChangeIncludesFormerOwner() {
        UUID account = TestIds.v7();
        UUID former = TestIds.v7();

        publishInTransaction(new AccountOwnerChanged(ws, account, former, TestIds.v7()));

        assertThat(spy.published)
                .containsExactly(new Published(
                        ws, new LiveChange("account", account), new AccountViewers(account, null, Set.of(former))));
    }

    private void setMarkets(boolean enabled) {
        jdbc.sql("""
                        insert into workspace_modules (workspace_id, module_key, enabled, updated_by)
                        values (:ws, 'markets', :enabled, :ana)
                        on conflict (workspace_id, module_key) do update set enabled = :enabled, updated_by = :ana""")
                .param("ws", ws)
                .param("enabled", enabled)
                .param("ana", ana)
                .update();
    }

    @Test
    void shoppingListToEveryoneWhenMarketsOn() {
        setMarkets(true);

        publishInTransaction(
                new ShoppingListChanged(ws),
                new ProductsChanged(ws),
                new ProductsMerged(ws, TestIds.v7(), TestIds.v7(), ana));

        // One audience for all four roles, and no id: the notice names the list, never an item or a product.
        assertThat(spy.published)
                .containsExactly(
                        new Published(ws, new LiveChange("shopping-list", null), new Everyone()),
                        new Published(ws, new LiveChange("shopping-list", null), new Everyone()),
                        new Published(ws, new LiveChange("shopping-list", null), new Everyone()));
    }

    @Test
    void shoppingListSilentWhenMarketsOff() {
        setMarkets(false);

        publishInTransaction(
                new ShoppingListChanged(ws),
                new ProductsChanged(ws),
                new ProductsMerged(ws, TestIds.v7(), TestIds.v7(), ana));

        assertThat(spy.published).isEmpty();
    }
}
