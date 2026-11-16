package br.com.liberdadefamilia.workspace;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * What the family has done so far. The workspace module only asks; the module that owns the data answers, so the
 * workspace never depends on it.
 */
public interface FamilyActivity {

    /** Which of the families have at least one transaction that is not deleted, in one query. */
    Set<UUID> withTransactions(Collection<UUID> workspaceIds);
}
