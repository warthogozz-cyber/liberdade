package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/**
 * What the family has done so far. The workspace module only asks; the module that owns the data answers, so the
 * workspace never depends on it.
 */
public interface FamilyActivity {

    /** Whether the family has at least one transaction that is not deleted. */
    boolean hasTransactions(UUID workspaceId);
}
