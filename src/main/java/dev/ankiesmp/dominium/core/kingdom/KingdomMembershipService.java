package dev.ankiesmp.dominium.core.kingdom;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class KingdomMembershipService {

    private final KingdomStore store;
    private final Clock clock;
    private final KingdomService.Invalidator invalidator;

    public KingdomMembershipService(KingdomStore store, Clock clock,
                                    KingdomService.Invalidator invalidator) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.invalidator = Objects.requireNonNull(invalidator);
    }

    public KingdomService.Result<KingdomMember> leave(UUID actor) {
        var m = store.findMembership(actor).orElse(null);
        if (m == null) return KingdomService.Result.error(
                KingdomService.Kind.NOT_A_MEMBER, "You are not in a kingdom.");
        if (m.role() == KingdomRole.LEADER) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED,
                    "The leader cannot leave. Transfer leadership or disband first.");
        }
        store.removeMember(m.kingdomId(), actor);
        invalidator.onMembershipChanged(m.kingdomId(), actor);
        return KingdomService.Result.ok(m);
    }

    public KingdomService.Result<KingdomMember> kick(UUID actor, UUID target) {
        if (actor.equals(target)) return KingdomService.Result.error(
                KingdomService.Kind.NOT_ALLOWED, "You cannot kick yourself. Use /kingdom leave.");
        var actorM = store.findMembership(actor).orElse(null);
        var targetM = store.findMembership(target).orElse(null);
        if (actorM == null) return KingdomService.Result.error(
                KingdomService.Kind.NOT_A_MEMBER, "You are not in a kingdom.");
        if (targetM == null || !targetM.kingdomId().equals(actorM.kingdomId())) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_FOUND, "That player is not in your kingdom.");
        }
        if (targetM.role() == KingdomRole.LEADER) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED, "You cannot kick the leader.");
        }
        var required = targetM.role() == KingdomRole.CO_LEADER
                ? KingdomPermissionService.Action.KICK_CO_LEADER
                : KingdomPermissionService.Action.KICK_MEMBER;
        if (!KingdomPermissionService.allowed(actorM.role(), required)) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED,
                    "You do not have permission to kick this player.");
        }
        store.removeMember(targetM.kingdomId(), target);
        invalidator.onMembershipChanged(targetM.kingdomId(), target);
        return KingdomService.Result.ok(targetM);
    }

    public KingdomService.Result<KingdomMember> promote(UUID actor, UUID target) {
        if (actor.equals(target)) return KingdomService.Result.error(
                KingdomService.Kind.NOT_ALLOWED, "You cannot promote yourself.");
        var actorM = store.findMembership(actor).orElse(null);
        var targetM = store.findMembership(target).orElse(null);
        if (actorM == null || targetM == null
                || !actorM.kingdomId().equals(targetM.kingdomId())) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_FOUND, "Target must be in your kingdom.");
        }
        if (!KingdomPermissionService.allowed(actorM.role(),
                KingdomPermissionService.Action.PROMOTE_TO_CO_LEADER)) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED, "Only the leader can promote.");
        }
        if (targetM.role() != KingdomRole.MEMBER) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED,
                    "Only members can be promoted to co-leader.");
        }
        store.updateRole(targetM.kingdomId(), target, KingdomRole.CO_LEADER,
                Instant.ofEpochMilli(clock.millis()));
        invalidator.onMembershipChanged(targetM.kingdomId(), target);
        return KingdomService.Result.ok(new KingdomMember(targetM.kingdomId(), target,
                KingdomRole.CO_LEADER, targetM.joinedAt(),
                Optional.of(Instant.ofEpochMilli(clock.millis()))));
    }

    public KingdomService.Result<KingdomMember> demote(UUID actor, UUID target) {
        if (actor.equals(target)) return KingdomService.Result.error(
                KingdomService.Kind.NOT_ALLOWED, "You cannot demote yourself.");
        var actorM = store.findMembership(actor).orElse(null);
        var targetM = store.findMembership(target).orElse(null);
        if (actorM == null || targetM == null
                || !actorM.kingdomId().equals(targetM.kingdomId())) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_FOUND, "Target must be in your kingdom.");
        }
        if (!KingdomPermissionService.allowed(actorM.role(),
                KingdomPermissionService.Action.DEMOTE_CO_LEADER)) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED, "Only the leader can demote.");
        }
        if (targetM.role() != KingdomRole.CO_LEADER) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED, "Only co-leaders can be demoted.");
        }
        store.updateRole(targetM.kingdomId(), target, KingdomRole.MEMBER,
                Instant.ofEpochMilli(clock.millis()));
        invalidator.onMembershipChanged(targetM.kingdomId(), target);
        return KingdomService.Result.ok(new KingdomMember(targetM.kingdomId(), target,
                KingdomRole.MEMBER, targetM.joinedAt(),
                Optional.of(Instant.ofEpochMilli(clock.millis()))));
    }

    public KingdomService.Result<KingdomMember> transferLeadership(UUID actor, UUID target) {
        if (actor.equals(target)) return KingdomService.Result.error(
                KingdomService.Kind.NOT_ALLOWED, "You cannot transfer to yourself.");
        var actorM = store.findMembership(actor).orElse(null);
        var targetM = store.findMembership(target).orElse(null);
        if (actorM == null || targetM == null
                || !actorM.kingdomId().equals(targetM.kingdomId())) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_FOUND, "Target must be in your kingdom.");
        }
        if (!KingdomPermissionService.allowed(actorM.role(),
                KingdomPermissionService.Action.TRANSFER_LEADERSHIP)) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED, "Only the leader can transfer.");
        }
        store.transferLeadership(actorM.kingdomId(), actor, target,
                Instant.ofEpochMilli(clock.millis()));
        invalidator.onMembershipChanged(actorM.kingdomId(), actor);
        invalidator.onMembershipChanged(actorM.kingdomId(), target);
        return KingdomService.Result.ok(new KingdomMember(actorM.kingdomId(), target,
                KingdomRole.LEADER, targetM.joinedAt(),
                Optional.of(Instant.ofEpochMilli(clock.millis()))));
    }
}
