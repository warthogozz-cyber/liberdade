package br.com.liberdadefamilia.workspace.internal;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every night, removes the invitations whose last event (used, revoked or expired) was more than 90 days ago. The log
 * carries only how many were removed.
 */
@Component
public class InvitationPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(InvitationPurgeJob.class);
    private static final int KEEP_DAYS = 90;

    private final InvitationRepository invitations;
    private final Clock clock;

    InvitationPurgeJob(InvitationRepository invitations, Clock clock) {
        this.invitations = invitations;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "America/Fortaleza")
    public void purge() {
        int purged = invitations.purgeEndedBefore(clock.instant().minus(KEEP_DAYS, DAYS));
        log.info("Purged {} invitations ended over {} days ago", purged, KEEP_DAYS);
    }
}
