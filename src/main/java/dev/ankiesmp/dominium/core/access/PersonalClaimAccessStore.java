package dev.ankiesmp.dominium.core.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence-contract voor trusted/visitor lijsten en per-claim settings.
 * Alle methoden zijn blocking; callers gebruiken de db-executor.
 */
public interface PersonalClaimAccessStore {

    /**
     * Atomair: verwijder een bestaande entry voor (claim, player) en insert de nieuwe.
     * Zo is een promotie visitor → trusted (of omgekeerd) één enkele transactie.
     */
    void upsert(PersonalClaimAccessEntry entry);

    /** Verwijdert een entry voor (claim, player). Retourneert de vorige level, of empty. */
    Optional<AccessLevel> remove(UUID claimId, UUID playerId);

    Optional<AccessLevel> levelFor(UUID claimId, UUID playerId);

    List<PersonalClaimAccessEntry> listForClaim(UUID claimId);

    void setNoAccess(UUID claimId, boolean noAccess, long updatedAtEpochMillis);

    PersonalClaimSettings settingsFor(UUID claimId);
}
