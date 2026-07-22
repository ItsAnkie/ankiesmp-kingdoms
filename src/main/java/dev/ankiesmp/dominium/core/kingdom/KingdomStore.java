package dev.ankiesmp.dominium.core.kingdom;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Alle kingdom-persistence in één interface, met bewust transactionele
 * signaturen: {@link #createWithLeader}, {@link #acceptInvite},
 * {@link #transferLeadership}, {@link #disband} draaien elk als één
 * DB-transactie in de SQL-impl.
 */
public interface KingdomStore {

    // ---- kingdoms ----
    Optional<Kingdom> findKingdom(UUID kingdomId);
    Optional<Kingdom> findKingdomByNormalizedName(String normalized);
    List<Kingdom> listKingdoms();

    /** Create + leader-membership in één transactie. */
    Kingdom createWithLeader(UUID kingdomId, String displayName, String normalizedName,
                             UUID leaderUuid, Instant createdAt);

    /** Disband + cleanup members/invites/visitors in één transactie. */
    void disband(UUID kingdomId);

    /** Leadership-swap in één transactie. */
    void transferLeadership(UUID kingdomId, UUID oldLeader, UUID newLeader, Instant at);

    // ---- members ----
    Optional<KingdomMember> findMembership(UUID playerUuid);
    List<KingdomMember> listMembers(UUID kingdomId);
    void updateRole(UUID kingdomId, UUID playerUuid, KingdomRole newRole, Instant at);
    void removeMember(UUID kingdomId, UUID playerUuid);

    // ---- invites ----
    void deleteExpiredInvites(Instant now);
    Optional<KingdomInvite> findInvite(UUID kingdomId, UUID targetUuid);
    List<KingdomInvite> invitesForTarget(UUID targetUuid);
    void insertInvite(UUID kingdomId, UUID targetUuid, UUID inviterUuid,
                      Instant createdAt, Instant expiresAt);
    void deleteInvite(UUID kingdomId, UUID targetUuid);

    /**
     * Membership + invite delete + optionele visitor-delete in één transactie.
     * Returns empty als de invite ondertussen weg is of de speler al lid van
     * een ander kingdom is (state gecheckt binnen de transactie).
     */
    Optional<KingdomMember> acceptInvite(UUID kingdomId, UUID targetUuid, Instant now);

    // ---- visitors ----
    List<KingdomVisitor> listVisitors(UUID kingdomId);
    boolean isVisitor(UUID kingdomId, UUID playerUuid);
    void insertVisitor(UUID kingdomId, UUID playerUuid, UUID addedBy, Instant at);
    void removeVisitor(UUID kingdomId, UUID playerUuid);
}
