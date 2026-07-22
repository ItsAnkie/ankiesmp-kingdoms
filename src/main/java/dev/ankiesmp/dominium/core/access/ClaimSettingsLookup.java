package dev.ankiesmp.dominium.core.access;

import java.util.UUID;

/** Hot-path lookup voor per-claim settings zoals No Access. */
@FunctionalInterface
public interface ClaimSettingsLookup {
    boolean noAccess(UUID claimId);

    ClaimSettingsLookup NEVER = id -> false;
}
