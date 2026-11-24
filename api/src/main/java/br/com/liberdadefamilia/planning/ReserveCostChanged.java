package br.com.liberdadefamilia.planning;

import java.util.UUID;

/** The family typed in, changed or cleared the monthly cost of the reserve. Published only when it changes. */
public record ReserveCostChanged(UUID workspaceId, UUID actorUserId) {}
