package br.com.liberdadefamilia.gamification.internal;

import br.com.liberdadefamilia.gamification.domain.AchievementKey;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** One achievement on the screen; {@code earnedAt} is null while it is not earned. */
record AchievementView(
        AchievementKey key,
        @Nullable @Schema(nullable = true) Instant earnedAt,
        boolean silent) {}
