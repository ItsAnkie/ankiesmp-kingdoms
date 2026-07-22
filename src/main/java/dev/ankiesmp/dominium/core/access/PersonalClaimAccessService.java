package dev.ankiesmp.dominium.core.access;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimType;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application-service voor trusted/visitor/no-access op persoonlijke claims.
 * Alleen de owner mag muteren; niemand kan zichzelf toevoegen; owner kan niet
 * als trusted of visitor worden geregistreerd. Promotie is atomair (één
 * transactie in de store).
 *
 * <p>Callers (commands, tests) roepen alleen deze methods aan; ze delen
 * dezelfde {@link PersonalClaimAccessStore}. Cache-invalidatie gebeurt via
 * de meegegeven {@link Invalidator}.
 */
public final class PersonalClaimAccessService {

    public interface Invalidator {
        void onAccessChanged(UUID claimId, UUID playerUuid);
        void onSettingsChanged(UUID claimId);
        Invalidator NOOP = new Invalidator() {
            @Override public void onAccessChanged(UUID c, UUID p) {}
            @Override public void onSettingsChanged(UUID c) {}
        };
    }

    private final PersonalClaimAccessStore store;
    private final Clock clock;
    private final Invalidator invalidator;

    public PersonalClaimAccessService(PersonalClaimAccessStore store,
                                      Clock clock,
                                      Invalidator invalidator) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.invalidator = Objects.requireNonNull(invalidator);
    }

    public Result trust(Claim claim, UUID actorOwner, UUID target) {
        return apply(claim, actorOwner, target, AccessLevel.TRUSTED);
    }

    public Result visitor(Claim claim, UUID actorOwner, UUID target) {
        return apply(claim, actorOwner, target, AccessLevel.VISITOR);
    }

    public Result untrust(Claim claim, UUID actorOwner, UUID target) {
        return remove(claim, actorOwner, target, AccessLevel.TRUSTED);
    }

    public Result unvisitor(Claim claim, UUID actorOwner, UUID target) {
        return remove(claim, actorOwner, target, AccessLevel.VISITOR);
    }

    public Result setNoAccess(Claim claim, UUID actorOwner, boolean noAccess) {
        Objects.requireNonNull(claim);
        Objects.requireNonNull(actorOwner);
        if (!isPersonalOwner(claim, actorOwner)) {
            return Result.notOwner();
        }
        store.setNoAccess(claim.id(), noAccess, clock.millis());
        invalidator.onSettingsChanged(claim.id());
        return Result.ok(null, null);
    }

    public List<PersonalClaimAccessEntry> list(Claim claim) {
        return store.listForClaim(claim.id());
    }

    public PersonalClaimSettings settings(Claim claim) {
        return store.settingsFor(claim.id());
    }

    private Result apply(Claim claim, UUID actorOwner, UUID target, AccessLevel level) {
        Objects.requireNonNull(claim);
        Objects.requireNonNull(actorOwner);
        Objects.requireNonNull(target);
        Objects.requireNonNull(level);
        if (!isPersonalOwner(claim, actorOwner)) return Result.notOwner();
        if (target.equals(actorOwner)) return Result.cannotTargetSelf();
        if (claim.owner().type() == ClaimType.PERSONAL && claim.owner().id().equals(target)) {
            return Result.cannotTargetOwner();
        }
        var entry = new PersonalClaimAccessEntry(
                claim.id(), target, level, Instant.ofEpochMilli(clock.millis()), actorOwner.toString());
        store.upsert(entry);
        invalidator.onAccessChanged(claim.id(), target);
        return Result.ok(target, level);
    }

    private Result remove(Claim claim, UUID actorOwner, UUID target, AccessLevel expected) {
        Objects.requireNonNull(claim);
        Objects.requireNonNull(actorOwner);
        Objects.requireNonNull(target);
        if (!isPersonalOwner(claim, actorOwner)) return Result.notOwner();
        Optional<AccessLevel> removed = store.remove(claim.id(), target);
        if (removed.isEmpty()) return Result.notFound();
        if (expected != null && removed.get() != expected) {
            // Verwijder toch (idempotent), maar meld de discrepantie.
            invalidator.onAccessChanged(claim.id(), target);
            return Result.removedDifferentLevel(target, removed.get());
        }
        invalidator.onAccessChanged(claim.id(), target);
        return Result.ok(target, removed.get());
    }

    private static boolean isPersonalOwner(Claim claim, UUID actor) {
        return claim.owner().type() == ClaimType.PERSONAL
                && claim.owner().id().equals(actor);
    }

    public enum Kind {
        OK, NOT_OWNER, CANNOT_TARGET_SELF, CANNOT_TARGET_OWNER,
        NOT_FOUND, REMOVED_DIFFERENT_LEVEL
    }

    public record Result(Kind kind, UUID target, AccessLevel level) {
        public static Result ok(UUID target, AccessLevel level) { return new Result(Kind.OK, target, level); }
        public static Result notOwner()             { return new Result(Kind.NOT_OWNER, null, null); }
        public static Result cannotTargetSelf()     { return new Result(Kind.CANNOT_TARGET_SELF, null, null); }
        public static Result cannotTargetOwner()    { return new Result(Kind.CANNOT_TARGET_OWNER, null, null); }
        public static Result notFound()             { return new Result(Kind.NOT_FOUND, null, null); }
        public static Result removedDifferentLevel(UUID target, AccessLevel actual) {
            return new Result(Kind.REMOVED_DIFFERENT_LEVEL, target, actual);
        }
        public boolean isOk() { return kind == Kind.OK; }
    }
}
