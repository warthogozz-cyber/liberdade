package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.Passkeys;
import br.com.liberdadefamilia.identity.UserDirectory;
import br.com.liberdadefamilia.identity.UserProfile;
import br.com.liberdadefamilia.identity.UserSummary;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcUserDirectory implements UserDirectory {

    private final JdbcClient jdbc;
    private final Passkeys passkeys;

    JdbcUserDirectory(JdbcClient jdbc, Passkeys passkeys) {
        this.jdbc = jdbc;
        this.passkeys = passkeys;
    }

    @Override
    public UserProfile profileOf(UUID userId) {
        UserProfile person = jdbc.sql("select display_name, email, locale, created_at from users where id = :id")
                .param("id", userId)
                .query((rs, row) -> new UserProfile(
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("locale"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        null))
                .single();
        return new UserProfile(
                person.displayName(),
                person.email(),
                person.locale(),
                person.createdAt(),
                passkeys.latestCreatedAt(userId).orElse(null));
    }

    @Override
    public UUID createPasskeyOnlyUser(String displayName) {
        return jdbc.sql("insert into users (display_name) values (:displayName) returning id")
                .param("displayName", displayName)
                .query(UUID.class)
                .single();
    }

    @Override
    public Set<UUID> pendingDeletion(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("select id from users where id in (:ids) and deletion_requested_at is not null")
                .param("ids", userIds)
                .query(UUID.class)
                .list());
    }

    @Override
    public Map<UUID, UserSummary> findAll(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UserSummary> found = new HashMap<>();
        jdbc.sql("""
                        select u.id, u.display_name,
                               exists (select 1 from auth_identities a
                                       where a.user_id = u.id and a.provider = 'google') as has_google
                        from users u
                        where u.id in (:ids)""")
                .param("ids", userIds)
                .query((rs, row) -> new UserSummary(
                        rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getBoolean("has_google")))
                .list()
                .forEach(user -> found.put(user.id(), user));
        return found;
    }
}
