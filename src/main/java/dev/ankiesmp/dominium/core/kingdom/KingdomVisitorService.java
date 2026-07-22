package dev.ankiesmp.dominium.core.kingdom;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class KingdomVisitorService {

    private final KingdomStore store;
    private final Clock clock;
    private final KingdomService.Invalidator invalidator;

    public KingdomVisitorService(KingdomStore store, Clock clock,
                                 KingdomService.Invalidator invalidator) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.invalidator = Objects.requireNonNull(invalidator);
    }

    public KingdomService.Result<KingdomVisitor> add(UUID actor, UUID target) {
        if (actor.equals(target)) return KingdomService.Result.error(
                KingdomService.Kind.NOT_ALLOWED, "You cannot add yourself.");
        var actorM = store.findMembership(actor).orElse(null);
        if (actorM == null) return KingdomService.Result.error(
                KingdomService.Kind.NOT_A_MEMBER, "You are not in a kingdom.");
        if (!KingdomPermissionService.allowed(actorM.role(),
                KingdomPermissionService.Action.MANAGE_VISITORS)) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED, "Only leader / co-leader can manage visitors.");
        }
        // Een kingdomlid kan niet ook visitor zijn.
        var targetMembership = store.findMembership(target).orElse(null);
        if (targetMembership != null && targetMembership.kingdomId().equals(actorM.kingdomId())) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED,
                    "That player is already a member of this kingdom.");
        }
        Instant now = Instant.ofEpochMilli(clock.millis());
        store.insertVisitor(actorM.kingdomId(), target, actor, now);
        invalidator.onMembershipChanged(actorM.kingdomId(), target);
        return KingdomService.Result.ok(new KingdomVisitor(actorM.kingdomId(), target, actor, now));
    }

    public KingdomService.Result<UUID> remove(UUID actor, UUID target) {
        var actorM = store.findMembership(actor).orElse(null);
        if (actorM == null) return KingdomService.Result.error(
                KingdomService.Kind.NOT_A_MEMBER, "You are not in a kingdom.");
        if (!KingdomPermissionService.allowed(actorM.role(),
                KingdomPermissionService.Action.MANAGE_VISITORS)) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_ALLOWED, "Only leader / co-leader can manage visitors.");
        }
        if (!store.isVisitor(actorM.kingdomId(), target)) {
            return KingdomService.Result.error(
                    KingdomService.Kind.NOT_FOUND, "That player is not a visitor of this kingdom.");
        }
        store.removeVisitor(actorM.kingdomId(), target);
        invalidator.onMembershipChanged(actorM.kingdomId(), target);
        return KingdomService.Result.ok(target);
    }

    public List<KingdomVisitor> list(UUID kingdomId) {
        return store.listVisitors(kingdomId);
    }

    public boolean isVisitor(UUID kingdomId, UUID player) {
        return store.isVisitor(kingdomId, player);
    }
}
