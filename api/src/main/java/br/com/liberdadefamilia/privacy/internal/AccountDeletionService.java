package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.audit.AuditEntry;
import br.com.liberdadefamilia.audit.AuditTrail;
import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.identity.UserDeletion;
import br.com.liberdadefamilia.workspace.Memberships;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Asking for the account to be deleted, and taking the request back (D23). */
@Service
class AccountDeletionService {

    private static final String SOLE_OWNER_DETAIL =
            "Você é a única pessoa responsável por uma família. Passe o papel de Dono a outra pessoa antes de excluir"
                    + " a conta.";

    private final UserDeletion deletion;
    private final Memberships memberships;
    private final AuditTrail trail;

    AccountDeletionService(UserDeletion deletion, Memberships memberships, AuditTrail trail) {
        this.deletion = deletion;
        this.memberships = memberships;
        this.trail = trail;
    }

    /**
     * Schedules the deletion. A repeat answers the same day and records nothing. Someone who is the only active Owner
     * of a family that is still live must hand it over first; a family already marked for deletion does not count.
     */
    @Transactional
    Instant request(CurrentUser user) {
        if (user.deletionRequestedAt() != null) {
            return user.deletionScheduledFor();
        }
        // Lock order: the user's row first, then the Owners' memberships (the purge takes them the same way).
        deletion.lock(user.id());
        if (memberships.isSoleActiveOwnerOfLiveFamily(user.id())) {
            throw new PrivacyRejectedException(409, "account.sole-owner", SOLE_OWNER_DETAIL);
        }
        UserDeletion.Scheduled scheduled = deletion.schedule(user.id());
        if (scheduled.changed()) {
            record(user, "account.deletion_requested");
        }
        return scheduled.date();
    }

    /** Nothing pending: nothing happens. */
    @Transactional
    void cancel(CurrentUser user) {
        if (user.deletionRequestedAt() == null) {
            return;
        }
        if (deletion.cancel(user.id())) {
            record(user, "account.deletion_cancelled");
        }
    }

    private void record(CurrentUser user, String action) {
        trail.record(new AuditEntry(null, user.id(), action, "USER", user.id(), Map.of()));
    }
}
