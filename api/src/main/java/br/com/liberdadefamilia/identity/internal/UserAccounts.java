package br.com.liberdadefamilia.identity.internal;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Users and the external identities they sign in with. */
@Component
public class UserAccounts {

    private static final String GOOGLE = "google";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    UserAccounts(JdbcClient jdbc, PlatformTransactionManager transactions, Clock clock) {
        this.jdbc = jdbc;
        // Each attempt gets its own transaction, even inside a caller's: a failed insert aborts the
        // transaction it ran in, so the retry could not run in that one.
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Finds or creates the user behind a Google account. An existing user keeps their display name (D8); the email
     * follows Google. When two first sign-ins race, the loser retries once and finds the winner's identity.
     */
    public UUID signInWithGoogle(String subject, String displayName, @Nullable String email) {
        try {
            return signInOnce(subject, displayName, email);
        } catch (DuplicateKeyException firstSignInRace) {
            return signInOnce(subject, displayName, email);
        }
    }

    /** The user, unless they do not exist or asked for their account to be deleted. */
    public Optional<UserRecord> findActive(UUID id) {
        return jdbc.sql("select id, display_name, email from users where id = :id and deletion_requested_at is null")
                .param("id", id)
                .query(UserRecord.class)
                .optional();
    }

    private UUID signInOnce(String subject, String displayName, @Nullable String email) {
        return Objects.requireNonNull(tx.execute(status -> signIn(subject, displayName, email)));
    }

    private UUID signIn(String subject, String displayName, @Nullable String email) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<UUID> existing = jdbc.sql(
                        "select user_id from auth_identities where provider = :provider and subject = :subject")
                .param("provider", GOOGLE)
                .param("subject", subject)
                .query(UUID.class)
                .optional();
        if (existing.isPresent()) {
            UUID userId = existing.get();
            jdbc.sql("update auth_identities set last_used_at = :now where provider = :provider and subject = :subject")
                    .param("now", now)
                    .param("provider", GOOGLE)
                    .param("subject", subject)
                    .update();
            jdbc.sql("""
                            update users set email = :email, updated_at = :now, version = version + 1
                            where id = :id and email is distinct from :email""")
                    .param("email", email)
                    .param("now", now)
                    .param("id", userId)
                    .update();
            return userId;
        }
        UUID userId = jdbc.sql("insert into users (display_name, email) values (:displayName, :email) returning id")
                .param("displayName", displayName)
                .param("email", email)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        insert into auth_identities (user_id, provider, subject, last_used_at)
                        values (:userId, :provider, :subject, :now)""")
                .param("userId", userId)
                .param("provider", GOOGLE)
                .param("subject", subject)
                .param("now", now)
                .update();
        return userId;
    }

    public record UserRecord(
            UUID id, String displayName, @Nullable String email) {}
}
