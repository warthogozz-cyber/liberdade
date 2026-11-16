package br.com.liberdadefamilia.audit.internal;

import br.com.liberdadefamilia.audit.ActivityItem;
import br.com.liberdadefamilia.audit.AuditTrail;
import br.com.liberdadefamilia.identity.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's own history. Outside any family, so there is no {@code @WorkspaceScoped}. */
@RestController
class ActivityController {

    private final AuditTrail trail;

    ActivityController(AuditTrail trail) {
        this.trail = trail;
    }

    @GetMapping("/v1/me/activity")
    List<ActivityItem> listMyActivity(CurrentUser user) {
        return trail.activityOf(user.id(), JdbcAuditTrail.MAX_ITEMS);
    }
}
