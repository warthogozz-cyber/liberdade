package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.UserDirectory;
import br.com.liberdadefamilia.identity.UserSummary;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcUserDirectory implements UserDirectory {

    private final JdbcClient jdbc;

    JdbcUserDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
