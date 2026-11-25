package br.com.liberdadefamilia.gamification.internal;

import br.com.liberdadefamilia.gamification.domain.AchievementKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The achievements a membership earned. The key is stored as the enum's name. */
@Repository
public class AchievementRepository {

    /** One earned achievement. */
    public record Row(AchievementKey key, Instant earnedAt, boolean silent) {}

    private final JdbcClient jdbc;

    AchievementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Writes the achievement unless the membership already has it; returns whether a row was written. */
    public boolean grant(UUID workspaceId, UUID membershipId, AchievementKey key, Instant at, boolean silent) {
        return jdbc.sql("""
                        insert into achievements (workspace_id, membership_id, achievement_key, earned_at, silent)
                        values (:workspaceId, :membershipId, :key, :at, :silent)
                        on conflict do nothing""")
                        .param("workspaceId", workspaceId)
                        .param("membershipId", membershipId)
                        .param("key", key.name())
                        .param("at", Timestamp.from(at))
                        .param("silent", silent)
                        .update()
                == 1;
    }

    public List<Row> earned(UUID workspaceId, UUID membershipId) {
        return jdbc.sql("""
                        select achievement_key, earned_at, silent
                        from achievements
                        where workspace_id = :workspaceId and membership_id = :membershipId
                        order by earned_at, achievement_key""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query((rs, row) -> new Row(
                        AchievementKey.valueOf(rs.getString("achievement_key")),
                        rs.getTimestamp("earned_at").toInstant(),
                        rs.getBoolean("silent")))
                .list();
    }

    /** Erases everything the membership earned; returns how many rows went. */
    public int deleteFor(UUID workspaceId, UUID membershipId) {
        return jdbc.sql("delete from achievements where workspace_id = :workspaceId and membership_id = :membershipId")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .update();
    }
}
