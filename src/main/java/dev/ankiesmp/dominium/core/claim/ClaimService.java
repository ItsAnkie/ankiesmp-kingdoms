package dev.ankiesmp.dominium.core.claim;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.integrations.WorldGuardHook;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.PostingOutcome;
import dev.ankiesmp.dominium.core.ledger.PostingRequest;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application service voor claim create/resize/delete met correcte
 * ledger-verrekening. Alles is blocking — de plugin roept dit via de
 * database-executor aan, nooit direct op de serverthread.
 *
 * <p>Volgorde per operatie:
 * <ol>
 *     <li>Validate placement (grootte, overlap, buffer).</li>
 *     <li>Boek ledger-delta atomair (spend of refund).</li>
 *     <li>Persisteer geometrie + revision (in dezelfde repo-transactie).</li>
 *     <li>Update in-memory {@link ClaimIndex}.</li>
 * </ol>
 * Als stap 2 faalt, worden 3/4 niet uitgevoerd. Falen van 3 wordt
 * expliciet gepropageerd zodat de plugin een compensatie-boeking kan
 * doen; de default hier is een terugboeking in dezelfde stap.
 */
public final class ClaimService {

    /** Persistence-strategie zodat pure core niet aan JDBC hangt. */
    public interface ClaimStore {
        void insert(Claim claim);
        void updateRectangle(UUID claimId, ClaimRectangle oldRect, ClaimRectangle newRect);
        /** Multi-region: vervang de volledige geometry (regions + cached bounds). */
        default void replaceGeometry(UUID claimId, ClaimGeometry oldGeometry, ClaimGeometry newGeometry) {
            // Default: single-rect fallback via updateRectangle (backwards-compat).
            updateRectangle(claimId, oldGeometry.bounds(), newGeometry.bounds());
        }
        void delete(UUID claimId);

        /**
         * Atomic expand: ledger-spend + WG-check + region-replace + bounds + revision
         * uitgevoerd in exact één JDBC-transactie. Iedere fout binnen de tx (inclusief
         * WG-blokkade of onvoldoende balance) rolt <b>alles</b> terug — er wordt geen
         * compensatie-boeking gedaan.
         *
         * <p>De default-implementatie gooit; alleen stores die op JDBC draaien
         * (bijv. {@code SqlClaimStore}) implementeren dit werkelijk. In-memory
         * teststores die deze methode niet ondersteunen falen expliciet, zodat
         * er nooit stilzwijgend een niet-atomisch pad in productie wordt gebruikt.
         */
        default ExpandAtomicOutcome expandAtomic(ExpandAtomicRequest request) {
            throw new UnsupportedOperationException(
                    "ClaimStore " + getClass().getSimpleName()
                            + " does not support atomic expand — required for production");
        }
    }

    /**
     * Alle informatie die het atomic-expand-pad nodig heeft. De {@code wgCheck}
     * wordt binnen de tx aangeroepen; als hij een niet-lege {@link Optional}
     * teruggeeft rolt de hele tx terug met dat region-id als reden.
     */
    public record ExpandAtomicRequest(
            UUID claimId,
            ClaimGeometry oldGeometry,
            ClaimGeometry newGeometry,
            HolderKey holder,
            ClaimBlockReason spendReason,
            long extraCost,
            UUID idempotencyKey,
            Supplier<Optional<String>> wgCheck) {
        public ExpandAtomicRequest {
            Objects.requireNonNull(claimId);
            Objects.requireNonNull(oldGeometry);
            Objects.requireNonNull(newGeometry);
            Objects.requireNonNull(holder);
            Objects.requireNonNull(spendReason);
            Objects.requireNonNull(idempotencyKey);
            Objects.requireNonNull(wgCheck);
            if (extraCost < 0) throw new IllegalArgumentException("extraCost must be >= 0");
        }
    }

    public record ExpandAtomicOutcome(boolean ok, PostingOutcome spend, String rejection) {
        public static ExpandAtomicOutcome ok(PostingOutcome sp) {
            return new ExpandAtomicOutcome(true, sp, null);
        }
        public static ExpandAtomicOutcome rejected(String reason) {
            return new ExpandAtomicOutcome(false, null, reason);
        }
    }

    private final ClaimIndex index;
    private final PlacementValidator validator;
    private final ClaimBlockLedger ledger;
    private final ClaimStore store;
    private final ClaimMutationGuard guard;
    private final WorldGuardHook wgHook;
    private final boolean wgIgnoreGlobal;

    public ClaimService(ClaimIndex index,
                        PlacementValidator validator,
                        ClaimBlockLedger ledger,
                        ClaimStore store) {
        this(index, validator, ledger, store, ClaimMutationGuard.ALLOW_ALL);
    }

    public ClaimService(ClaimIndex index,
                        PlacementValidator validator,
                        ClaimBlockLedger ledger,
                        ClaimStore store,
                        ClaimMutationGuard guard) {
        this(index, validator, ledger, store, guard, WorldGuardHook.NO_OP, true);
    }

    public ClaimService(ClaimIndex index,
                        PlacementValidator validator,
                        ClaimBlockLedger ledger,
                        ClaimStore store,
                        ClaimMutationGuard guard,
                        WorldGuardHook wgHook,
                        boolean wgIgnoreGlobal) {
        this.index = Objects.requireNonNull(index, "index");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.store = Objects.requireNonNull(store, "store");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.wgHook = Objects.requireNonNull(wgHook, "wgHook");
        this.wgIgnoreGlobal = wgIgnoreGlobal;
    }

    public CreateResult create(WorldRef world, ClaimRectangle rect, ClaimOwner owner, UUID idempotencyKey) {
        Objects.requireNonNull(owner, "owner");
        if (owner.type() == ClaimType.ADMIN) {
            throw new IllegalArgumentException("admin claims are not supported in this phase");
        }
        if (guard.isBlocked(owner.type(), owner.id())) {
            return CreateResult.rejected(PlacementResult.blocked(
                    guard.reason(owner.type(), owner.id())));
        }
        // Defensive: één claim per PERSONAL/KINGDOM owner, ongeacht of de
        // DB-index al staat. Voorkomt dat een tweede create ooit slaagt.
        if (index.findByOwner(owner.type(), owner.id()).isPresent()) {
            return CreateResult.rejected(PlacementResult.duplicateOwner());
        }
        HolderKey holder = owner.toLedgerHolder();
        long available = ledger.balanceOrZero(holder).balance();
        PlacementResult check = validator.validateCreate(world, rect, available);
        if (!check.isOk()) return CreateResult.rejected(check);

        PostingOutcome spend = ledger.post(PostingRequest.builder()
                .holder(holder)
                .delta(-rect.cost())
                .reason(owner.type() == ClaimType.KINGDOM
                        ? ClaimBlockReason.KINGDOM_CLAIM_SPEND
                        : ClaimBlockReason.PERSONAL_CLAIM_SPEND)
                .idempotencyKey(idempotencyKey)
                .reference("claim:create")
                .build());
        if (spend.kind() == PostingOutcome.Kind.INSUFFICIENT_BALANCE) {
            return CreateResult.rejected(PlacementResult.insufficientClaimBlocks(rect.cost(), available));
        }

        UUID claimId = UUID.randomUUID();
        Claim claim = new Claim(claimId, world, rect, owner, Instant.now());
        try {
            store.insert(claim);
            index.add(claim);
        } catch (RuntimeException e) {
            // compensatie: refund exact hetzelfde bedrag met een afgeleide key
            ledger.post(PostingRequest.builder()
                    .holder(holder)
                    .delta(rect.cost())
                    .reason(owner.type() == ClaimType.KINGDOM
                            ? ClaimBlockReason.KINGDOM_CLAIM_REFUND
                            : ClaimBlockReason.PERSONAL_CLAIM_REFUND)
                    .idempotencyKey(UUID.nameUUIDFromBytes(("compensate:" + idempotencyKey).getBytes()))
                    .reference("claim:create-compensation")
                    .build());
            throw e;
        }
        return CreateResult.ok(claim, spend);
    }

    public ResizeResult resize(UUID claimId, ClaimRectangle newRect, UUID idempotencyKey) {
        Claim existing = index.get(claimId)
                .orElseThrow(() -> new IllegalArgumentException("unknown claim " + claimId));
        if (existing.owner().type() == ClaimType.ADMIN) {
            throw new IllegalArgumentException("admin claim resize not supported yet");
        }
        if (guard.isBlocked(existing.owner().type(), existing.owner().id())) {
            return ResizeResult.rejected(PlacementResult.blocked(
                    guard.reason(existing.owner().type(), existing.owner().id())));
        }
        HolderKey holder = existing.owner().toLedgerHolder();
        long available = ledger.balanceOrZero(holder).balance();
        PlacementResult check = validator.validateResize(claimId, newRect, available);
        if (!check.isOk()) return ResizeResult.rejected(check);

        long delta = existing.rect().resizeCostDelta(newRect);
        Optional<PostingOutcome> boot;
        if (delta > 0) {
            PostingOutcome outcome = ledger.post(PostingRequest.builder()
                    .holder(holder)
                    .delta(-delta)
                    .reason(existing.owner().type() == ClaimType.KINGDOM
                            ? ClaimBlockReason.KINGDOM_CLAIM_SPEND
                            : ClaimBlockReason.PERSONAL_CLAIM_SPEND)
                    .idempotencyKey(idempotencyKey)
                    .reference("claim:resize")
                    .build());
            if (outcome.kind() == PostingOutcome.Kind.INSUFFICIENT_BALANCE) {
                return ResizeResult.rejected(PlacementResult.insufficientClaimBlocks(delta, available));
            }
            boot = Optional.of(outcome);
        } else if (delta < 0) {
            boot = Optional.of(ledger.post(PostingRequest.builder()
                    .holder(holder)
                    .delta(-delta)
                    .reason(existing.owner().type() == ClaimType.KINGDOM
                            ? ClaimBlockReason.KINGDOM_CLAIM_REFUND
                            : ClaimBlockReason.PERSONAL_CLAIM_REFUND)
                    .idempotencyKey(idempotencyKey)
                    .reference("claim:resize")
                    .build()));
        } else {
            boot = Optional.empty();
        }

        try {
            store.updateRectangle(claimId, existing.rect(), newRect);
            index.replace(claimId, newRect);
        } catch (RuntimeException e) {
            if (boot.isPresent()) {
                long compensate = -boot.get().entry().delta();
                ledger.post(PostingRequest.builder()
                        .holder(holder)
                        .delta(compensate)
                        .reason(compensate > 0
                                ? (existing.owner().type() == ClaimType.KINGDOM
                                    ? ClaimBlockReason.KINGDOM_CLAIM_REFUND
                                    : ClaimBlockReason.PERSONAL_CLAIM_REFUND)
                                : (existing.owner().type() == ClaimType.KINGDOM
                                    ? ClaimBlockReason.KINGDOM_CLAIM_SPEND
                                    : ClaimBlockReason.PERSONAL_CLAIM_SPEND))
                        .idempotencyKey(UUID.nameUUIDFromBytes(("compensate:" + idempotencyKey).getBytes()))
                        .reference("claim:resize-compensation")
                        .build());
            }
            throw e;
        }
        return ResizeResult.ok(newRect, delta, boot.orElse(null));
    }

    public DeleteResult delete(UUID claimId, UUID idempotencyKey) {
        Claim existing = index.get(claimId)
                .orElseThrow(() -> new IllegalArgumentException("unknown claim " + claimId));
        HolderKey holder = existing.owner().toLedgerHolder();
        // Refund via unieke area van de multi-region geometry.
        long refund = existing.geometry().area();

        store.delete(claimId);
        index.remove(claimId);
        PostingOutcome refunded = ledger.post(PostingRequest.builder()
                .holder(holder)
                .delta(refund)
                .reason(existing.owner().type() == ClaimType.KINGDOM
                        ? ClaimBlockReason.KINGDOM_CLAIM_REFUND
                        : ClaimBlockReason.PERSONAL_CLAIM_REFUND)
                .idempotencyKey(idempotencyKey)
                .reference("claim:delete")
                .build());
        return new DeleteResult(existing, refunded);
    }

    /**
     * Multi-region expansion: voegt {@code addedRegion} toe aan de bestaande
     * geometry en boekt alleen de nieuwe unieke blocks. Overlap met eigen
     * geometry kost 0 extra. Edge-connected vereist; corner-only en detached
     * worden door {@link ClaimGeometry#union} geweigerd.
     */
    public ExpansionResult expandGeometry(UUID claimId, ClaimRectangle addedRegion, UUID idempotencyKey) {
        Claim existing = index.get(claimId)
                .orElseThrow(() -> new IllegalArgumentException("unknown claim " + claimId));
        if (existing.owner().type() == ClaimType.ADMIN) {
            throw new IllegalArgumentException("admin claim expansion not supported yet");
        }
        if (guard.isBlocked(existing.owner().type(), existing.owner().id())) {
            return ExpansionResult.rejected(PlacementResult.blocked(
                    guard.reason(existing.owner().type(), existing.owner().id())));
        }
        var union = existing.geometry().union(addedRegion);
        switch (union.kind()) {
            case NO_OP -> {
                return ExpansionResult.noOp(existing.geometry());
            }
            case REJECT_CORNER_ONLY, REJECT_DETACHED -> {
                return ExpansionResult.rejected(PlacementResult.invalidGeometry(
                        union.kind() == ClaimGeometry.UnionKind.REJECT_CORNER_ONLY
                                ? "Selection only touches by corner; must share a full edge."
                                : "Selection is not connected to your claim."));
            }
            case OK -> { /* proceed */ }
        }
        // Overlap-check tegen andere owners (via de ClaimIndex; die gebruikt geometry.intersects).
        for (Claim other : index.overlapping(existing.world(), addedRegion)) {
            if (!other.id().equals(claimId)) {
                return ExpansionResult.rejected(PlacementResult.overlap(
                        java.util.List.of(other)));
            }
        }
        long extraCost = union.extraCost();
        HolderKey holder = existing.owner().toLedgerHolder();
        long available = ledger.balanceOrZero(holder).balance();
        if (extraCost > available) {
            return ExpansionResult.rejected(
                    PlacementResult.insufficientClaimBlocks(extraCost, available));
        }
        // Atomair pad: ledger-spend + WG-check + region-replace + bounds + revision
        // op één JDBC-Connection. Bij fout rolt alles terug — geen compensatie.
        ClaimBlockReason reason = existing.owner().type() == ClaimType.KINGDOM
                ? ClaimBlockReason.KINGDOM_CLAIM_SPEND
                : ClaimBlockReason.PERSONAL_CLAIM_SPEND;
        var world = existing.world();
        var newRegion = addedRegion;
        Supplier<Optional<String>> wgCheck = () ->
                wgHook.firstBlockingRegion(world, java.util.List.of(newRegion), wgIgnoreGlobal);
        ExpandAtomicOutcome outcome = store.expandAtomic(new ExpandAtomicRequest(
                claimId, existing.geometry(), union.geometry(),
                holder, reason, extraCost, idempotencyKey, wgCheck));
        if (!outcome.ok()) {
            // Store heeft alles rolled-back; alleen index/spatial-cache blijven onaangeraakt.
            return ExpansionResult.rejected(PlacementResult.invalidGeometry(outcome.rejection()));
        }
        index.replaceGeometry(claimId, union.geometry());
        return ExpansionResult.ok(union.geometry(), extraCost, outcome.spend());
    }

    public record ExpansionResult(Kind kind, ClaimGeometry newGeometry, long extraCost,
                                  PostingOutcome spend, PlacementResult rejection) {
        public enum Kind { OK, NO_OP, REJECTED }
        public static ExpansionResult ok(ClaimGeometry g, long extra, PostingOutcome sp) {
            return new ExpansionResult(Kind.OK, g, extra, sp, null);
        }
        public static ExpansionResult noOp(ClaimGeometry g) {
            return new ExpansionResult(Kind.NO_OP, g, 0, null, null);
        }
        public static ExpansionResult rejected(PlacementResult r) {
            return new ExpansionResult(Kind.REJECTED, null, 0, null, r);
        }
        public boolean isOk() { return kind == Kind.OK; }
    }

    // ---------- Result records ----------

    public record CreateResult(Claim claim, PostingOutcome spend, PlacementResult rejection) {
        public static CreateResult ok(Claim claim, PostingOutcome spend) {
            return new CreateResult(claim, spend, null);
        }
        public static CreateResult rejected(PlacementResult rejection) {
            return new CreateResult(null, null, rejection);
        }
        public boolean isOk() { return claim != null; }
    }

    public record ResizeResult(ClaimRectangle newRect, long claimBlockDelta,
                               PostingOutcome ledgerOutcome, PlacementResult rejection) {
        public static ResizeResult ok(ClaimRectangle rect, long delta, PostingOutcome outcome) {
            return new ResizeResult(rect, delta, outcome, null);
        }
        public static ResizeResult rejected(PlacementResult rejection) {
            return new ResizeResult(null, 0, null, rejection);
        }
        public boolean isOk() { return newRect != null; }
    }

    public record DeleteResult(Claim removed, PostingOutcome refund) {}
}
