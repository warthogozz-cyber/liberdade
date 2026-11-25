package br.com.liberdadefamilia.live.internal;

import br.com.liberdadefamilia.ledger.AccountVisibility;
import br.com.liberdadefamilia.workspace.Role;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Who of a family receives a notice, decided per stream. */
public sealed interface Audience {

    /** Whether the stream's person receives the notice. Only a youth ever costs a query. */
    boolean includes(LiveStream stream, AccountVisibility visibility);

    /** Everybody in the family. */
    record Everyone() implements Audience {

        @Override
        public boolean includes(LiveStream stream, AccountVisibility visibility) {
            return true;
        }
    }

    /** Everybody but the youth: the budget, the recurring rules and the reserve are not theirs to see. */
    record NoYouth() implements Audience {

        @Override
        public boolean includes(LiveStream stream, AccountVisibility visibility) {
            return stream.role() != Role.YOUTH;
        }
    }

    /**
     * Whoever sees the account (and, for a transfer, the other one). A youth also receives it when listed in
     * {@code extraMemberships}: a former owner has to learn the account is no longer theirs.
     */
    record AccountViewers(UUID accountId, @Nullable UUID toAccountId, Set<UUID> extraMemberships) implements Audience {

        public AccountViewers {
            extraMemberships = Set.copyOf(extraMemberships);
        }

        @Override
        public boolean includes(LiveStream stream, AccountVisibility visibility) {
            if (stream.role() != Role.YOUTH || extraMemberships.contains(stream.membershipId())) {
                return true;
            }
            return visibility.canSee(
                    stream.workspaceId(), stream.membershipId(), stream.role(), accountId, toAccountId);
        }
    }

    /** One person: the streams of that membership and nobody else's. */
    record Member(UUID membershipId) implements Audience {

        @Override
        public boolean includes(LiveStream stream, AccountVisibility visibility) {
            return stream.membershipId().equals(membershipId);
        }
    }
}
