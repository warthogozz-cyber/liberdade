package br.com.liberdadefamilia.identity;

import java.util.Collection;
import java.util.Set;

/** Whether sessions are still good, for modules that hold something open on behalf of one. */
public interface SessionLiveness {

    /** The ones among these session ids that exist and have not expired, in one query; none for an empty collection. */
    Set<String> aliveAmong(Collection<String> sessionIds);
}
