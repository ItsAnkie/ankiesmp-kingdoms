package dev.ankiesmp.dominium.core.kingdom;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lifecycle: create + disband + info. Membership-mutaties (invite, kick,
 * promote, demote, transfer, leave) leven in {@link KingdomMembershipService};
 * visitors in {@link KingdomVisitorService}; invites in
 * {@link KingdomInviteService}. Alle mutaties invalidereren via de
 * {@link Invalidator}.
 */
public final class KingdomService {

    public interface Invalidator {
        void onMembershipChanged(UUID kingdomId, UUID playerUuid);
        void onKingdomDisbanded(UUID kingdomId);
        Invalidator NOOP = new Invalidator() {
            @Override public void onMembershipChanged(UUID k, UUID p) {}
            @Override public void onKingdomDisbanded(UUID k) {}
        };
    }

    private final KingdomStore store;
    private final Clock clock;
    private final NameConfig nameConfig;
    private final Invalidator invalidator;

    public KingdomService(KingdomStore store, Clock clock, NameConfig nameConfig,
                          Invalidator invalidator) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.nameConfig = Objects.requireNonNull(nameConfig);
        this.invalidator = Objects.requireNonNull(invalidator);
    }

    public Result<Kingdom> create(String rawName, UUID creator) {
        Objects.requireNonNull(creator);
        var v = KingdomName.validate(rawName, nameConfig.minLength(), nameConfig.maxLength());
        if (!v.ok()) return Result.error(Kind.INVALID_NAME, v.error());

        // Kan de creator al ergens in zitten?
        if (store.findMembership(creator).isPresent()) {
            return Result.error(Kind.ALREADY_IN_KINGDOM, "You are already in a kingdom.");
        }
        String normalized = KingdomName.normalize(v.displayName());
        if (store.findKingdomByNormalizedName(normalized).isPresent()) {
            return Result.error(Kind.NAME_TAKEN, "That kingdom name is taken.");
        }
        UUID kid = UUID.randomUUID();
        Kingdom created = store.createWithLeader(kid, v.displayName(), normalized,
                creator, Instant.ofEpochMilli(clock.millis()));
        invalidator.onMembershipChanged(kid, creator);
        return Result.ok(created);
    }

    public Result<Kingdom> disband(UUID kingdomId, UUID actor) {
        Kingdom kingdom = store.findKingdom(kingdomId)
                .orElse(null);
        if (kingdom == null) return Result.error(Kind.NOT_FOUND, "Kingdom does not exist.");
        KingdomMember membership = store.findMembership(actor).orElse(null);
        if (membership == null || !membership.kingdomId().equals(kingdomId)) {
            return Result.error(Kind.NOT_A_MEMBER, "You are not a member of this kingdom.");
        }
        if (!KingdomPermissionService.allowed(membership.role(),
                KingdomPermissionService.Action.DISBAND)) {
            return Result.error(Kind.NOT_ALLOWED, "Only the leader can disband a kingdom.");
        }
        store.disband(kingdomId);
        invalidator.onKingdomDisbanded(kingdomId);
        return Result.ok(kingdom);
    }

    public Optional<Kingdom> findByNormalizedName(String rawName) {
        return store.findKingdomByNormalizedName(KingdomName.normalize(rawName));
    }

    public Optional<Kingdom> findById(UUID id) { return store.findKingdom(id); }
    public List<KingdomMember> listMembers(UUID kingdomId) { return store.listMembers(kingdomId); }
    public Optional<KingdomMember> membershipFor(UUID playerUuid) { return store.findMembership(playerUuid); }
    public NameConfig nameConfig() { return nameConfig; }

    public enum Kind {
        OK, INVALID_NAME, NAME_TAKEN, ALREADY_IN_KINGDOM,
        NOT_FOUND, NOT_A_MEMBER, NOT_ALLOWED
    }

    public record Result<T>(Kind kind, T value, String message) {
        public static <T> Result<T> ok(T v) { return new Result<>(Kind.OK, v, null); }
        public static <T> Result<T> error(Kind k, String m) { return new Result<>(k, null, m); }
        public boolean isOk() { return kind == Kind.OK; }
    }

    public record NameConfig(int minLength, int maxLength) {
        public NameConfig {
            if (minLength < 1 || maxLength < minLength) {
                throw new IllegalArgumentException("invalid name length bounds");
            }
        }
    }
}
