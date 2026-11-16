package br.com.liberdadefamilia.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.identity.internal.UserAccounts.UserRecord;
import br.com.liberdadefamilia.support.TestUsers;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserAccountsIT {

    @Autowired
    UserAccounts accounts;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DataSource dataSource;

    @Autowired
    Clock clock;

    @Test
    void findsAnActiveUser() {
        UUID id = users.create("Ana Teste", "ana@example.com");

        assertThat(accounts.findActive(id)).contains(new UserRecord(id, "Ana Teste", "ana@example.com"));
    }

    @Test
    void doesNotFindAnUnknownUser() {
        assertThat(accounts.findActive(UUID.randomUUID())).isEmpty();
    }

    @Test
    void doesNotFindAUserWhoAskedForDeletion() {
        UUID id = users.create("Ana Teste", "ana@example.com");
        jdbc.sql("update users set deletion_requested_at = now() where id = :id")
                .param("id", id)
                .update();

        assertThat(accounts.findActive(id)).isEmpty();
    }

    /**
     * Two first sign-ins race: another sign-in commits the same Google identity after this one looked for it and
     * before it inserted it. The insert hits the unique key and the retry finds the winner's user, also when a caller
     * already holds a transaction.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void firstSignInRaceRetriesAndReturnsTheWinner(boolean insideCallerTransaction) {
        String subject = "race-" + UUID.randomUUID();
        var winner = new AtomicReference<UUID>();
        var identityInserts = new AtomicInteger();
        DataSource racing = beforeIdentityInsert(() -> {
            if (identityInserts.getAndIncrement() == 0) {
                winner.set(otherSignInCommits(subject));
            }
        });
        var racingAccounts = new UserAccounts(JdbcClient.create(racing), new JdbcTransactionManager(racing), clock);

        UUID userId = insideCallerTransaction
                ? new TransactionTemplate(new JdbcTransactionManager(racing))
                        .execute(status -> racingAccounts.signInWithGoogle(subject, "Bia Teste", "bia@example.com"))
                : racingAccounts.signInWithGoogle(subject, "Bia Teste", "bia@example.com");

        assertThat(identityInserts).as("only the first attempt inserts").hasValue(1);
        assertThat(userId).isEqualTo(winner.get());
        assertThat(jdbc.sql("select count(*) from auth_identities where subject = :subject")
                        .param("subject", subject)
                        .query(Long.class)
                        .single())
                .isOne();
        assertThat(jdbc.sql("select count(*) from users where display_name = 'Bia Teste'")
                        .query(Long.class)
                        .single())
                .as("the losing attempt's user was rolled back")
                .isZero();
    }

    /** The other sign-in, on its own connection and committed at once. */
    private UUID otherSignInCommits(String subject) {
        UUID id = users.create("Ana Teste", "ana@example.com");
        jdbc.sql("""
                        insert into auth_identities (user_id, provider, subject, last_used_at)
                        values (:id, 'google', :subject, now())""").param("id", id).param("subject", subject).update();
        return id;
    }

    /** The real data source, but {@code hook} runs right before each insert into auth_identities is prepared. */
    private DataSource beforeIdentityInsert(Runnable hook) {
        return new DelegatingDataSource(dataSource) {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(
                        Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                            if (method.getName().equals("prepareStatement")
                                    && args[0] instanceof String sql
                                    && sql.contains("insert into auth_identities")) {
                                hook.run();
                            }
                            try {
                                return method.invoke(connection, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        });
            }
        };
    }
}
