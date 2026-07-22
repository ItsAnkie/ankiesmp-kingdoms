package dev.ankiesmp.dominium.core.kingdom;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class KingdomInviteService {

    private final KingdomStore store;
    private final Clock clock;
    private final Duration inviteTtl;
    private final KingdomService.Invalidator invalidator;

    public KingdomInviteService(KingdomStore store, Clock clock, Duration inviteTtl,
                                KingdomService.Invalidator invalidator) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.inviteTtl = Objects.requireNonNull(inviteTtl);
        this.invalidator = Objects.requireNonNull(invalidator);
    }

    public KingdomService.Result<KingdomInvite> invite(UUID inviter, UUID target) {
        if (inviter.equals(target)) return KingdomService.Result.error(
                KingdomService.Kind.NOT_ALLOWED, "You cannot invite yourself.");
        var inviterM = store.findMembership(inviter).orElse(null);
        if (inviterM == null) return KingdomService.Result.error(
                KingdomService.Kind.NOT_A_MEMBER, "You are not in a kingdom.");
        if (!KingdomPermissionService.allowed(inviterM.role(),
                KingdomPermissionService.Action.INVITE)) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED, "Only leader / co-leader can invite.");
        }
        if (store.findMembership(target).isPresent()) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED, "That player is already in a kingdom.");
        }
        // Existing invite for same kingdom → geen dubbele.
        store.deleteExpiredInvites(Instant.ofEpochMilli(clock.millis()));
        if (store.findInvite(inviterM.kingdomId(), target).isPresent()) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED, "That player already has an invite.");
        }
        Instant now = Instant.ofEpochMilli(clock.millis());
        Instant expires = now.plus(inviteTtl);
        store.insertInvite(inviterM.kingdomId(), target, inviter, now, expires);
        var invite = store.findInvite(inviterM.kingdomId(), target).orElseThrow();
        return KingdomService.Result.ok(invite);
    }

    public KingdomService.Result<KingdomMember> accept(UUID target, UUID kingdomId) {
        Instant now = Instant.ofEpochMilli(clock.millis());
        store.deleteExpiredInvites(now);
        // acceptInvite is transactioneel + returnt empty bij expired/al-lid.
        var member = store.acceptInvite(kingdomId, target, now).orElse(null);
        if (member == null) {
            // Onderscheid tussen expired en al-lid voor duidelijke feedback.
            if (store.findMembership(target).isPresent()) {
                return KingdomService.Result.error(
                        KingdomService.Kind.NOT_ALLOWED,
                        "You have already joined another kingdom.");
            }
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_FOUND,
                    "No valid invite for that kingdom (expired or missing).");
        }
        invalidator.onMembershipChanged(kingdomId, target);
        return KingdomService.Result.ok(member);
    }

    public KingdomService.Result<KingdomInvite> decline(UUID target, UUID kingdomId) {
        var invite = store.findInvite(kingdomId, target).orElse(null);
        if (invite == null) return KingdomService.Result.error(
                KingdomService.Kind.NOT_FOUND, "No invite for that kingdom.");
        store.deleteInvite(kingdomId, target);
        return KingdomService.Result.ok(invite);
    }

    public List<KingdomInvite> invitesFor(UUID target) {
        return store.invitesForTarget(target);
    }

    public Optional<KingdomInvite> find(UUID kingdomId, UUID target) {
        return store.findInvite(kingdomId, target);
    }

    public Duration ttl() { return inviteTtl; }

    public void cleanupExpired() {
        store.deleteExpiredInvites(Instant.ofEpochMilli(clock.millis()));
    }
}
