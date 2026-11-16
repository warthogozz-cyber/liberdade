package br.com.liberdadefamilia.identity;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Looks people up for other modules. */
public interface UserDirectory {

    /** The people with these ids, in one query. An id with no person (an account already deleted) is left out. */
    Map<UUID, UserSummary> findAll(Collection<UUID> userIds);
}
