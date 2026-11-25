package br.com.liberdadefamilia.gamification.internal;

import br.com.liberdadefamilia.gamification.AchievementExport;
import br.com.liberdadefamilia.gamification.ExportedAchievement;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads the achievements for the data export. They are stored personal data, so it does not look at the modules. */
@Component
class AchievementJdbcExport implements AchievementExport {

    private final JdbcClient jdbc;

    AchievementJdbcExport(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ExportedAchievement> of(UUID workspaceId, UUID membershipId) {
        return jdbc.sql("""
                        select achievement_key, earned_at
                        from achievements
                        where workspace_id = :workspaceId and membership_id = :membershipId
                        order by earned_at, achievement_key""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query((rs, row) -> new ExportedAchievement(
                        rs.getString("achievement_key"),
                        rs.getTimestamp("earned_at").toInstant()))
                .list();
    }
}
