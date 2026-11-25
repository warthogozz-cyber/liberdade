package br.com.liberdadefamilia.gamification;

import java.util.List;
import java.util.UUID;

/** What gamification owns for the data export: the achievements a membership earned. */
public interface AchievementExport {

    /** Everything the membership earned, silent ones included, oldest first. */
    List<ExportedAchievement> of(UUID workspaceId, UUID membershipId);
}
