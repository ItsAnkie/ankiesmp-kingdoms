package dev.ankiesmp.dominium.storage.claim;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimGeometry;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.claim.MutableClaimMutationGuard;
import dev.ankiesmp.dominium.core.claim.PlacementResult;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.integrations.WorldGuardHook;
import dev.ankiesmp.dominium.core.ledger.PostingOutcome;
import dev.ankiesmp.dominium.core.ledger.PostingRequest;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.ledger.SqlClaimBlockLedger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-store atomaire operaties: expand + duplicate-repair-merge in exact
 * één SQLite-transactie op één {@link java.sql.Connection}. Bij iedere
 * fout rolt alles terug (claims, regions, ledger, revisions) — geen
 * compensatieboekingen.
 *
 * <p>Cache/index/guard-invalidatie gebeurt <b>na</b> een succesvolle commit
 * door de caller. Voor test-doeleinden is er een {@link Hook}-parameter die
 * het mogelijk maakt na iedere mutatiestap een fout te injecteren.
 */
public final class AtomicClaimOps {

    /** Failure-injection points voor tests; in productie is {@link #NO_HOOK} actief. */
    public interface Hook {
        void afterLedgerSpend()   throws java.sql.SQLException;
        void afterRegionsDelete() throws java.sql.SQLException;
        void afterFirstRegionInsert() throws java.sql.SQLException;
        void afterBoundsUpdate()  throws java.sql.SQLException;
        void afterRevisionWrite() throws java.sql.SQLException;
        void beforeCommit()       throws java.sql.SQLException;
    }
    public static final Hook NO_HOOK = new Hook() {
        public void afterLedgerSpend()       {}
        public void afterRegionsDelete()     {}
        public void afterFirstRegionInsert() {}
        public void afterBoundsUpdate()      {}
        public void afterRevisionWrite()     {}
        public void beforeCommit()           {}
    };

    private final Database database;
    private final SqlClaimRepository repo;
    private final SqlClaimBlockLedger ledger;

    public AtomicClaimOps(Database database, SqlClaimRepository repo,
                          SqlClaimBlockLedger ledger) {
        this.database = Objects.requireNonNull(database);
        this.repo = Objects.requireNonNull(repo);
        this.ledger = Objects.requireNonNull(ledger);
    }

    // ---------- Atomic expand ----------

    public ExpandResult expandAtomic(UUID claimId, ClaimRectangle addedRegion,
                                     UUID idempotencyKey, WorldGuardHook wgHook,
                                     boolean wgIgnoreGlobal, Hook hook) {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(addedRegion);
        Objects.requireNonNull(idempotencyKey);
        try {
            return database.withTransaction(conn -> {
                // 1) Herlees claim + regions binnen tx.
                Claim existing = readClaim(conn, claimId).orElseThrow(
                        () -> new IllegalStateException("claim not found: " + claimId));
                if (existing.owner().type() == ClaimType.ADMIN) {
                    throw new RollbackSignal("admin claim expansion not supported");
                }
                // 2) Union-plan.
                var union = existing.geometry().union(addedRegion);
                if (union.kind() == ClaimGeometry.UnionKind.NO_OP) {
                    throw new RollbackSignal("no-op: selection fully inside existing claim");
                }
                if (union.kind() == ClaimGeometry.UnionKind.REJECT_CORNER_ONLY
                        || union.kind() == ClaimGeometry.UnionKind.REJECT_DETACHED) {
                    throw new RollbackSignal("not edge-connected");
                }
                // 3) Overlap met andere owners (in-connection query).
                for (Claim other : readOverlapping(conn, existing.world().id(), addedRegion)) {
                    if (!other.id().equals(claimId)) {
                        throw new RollbackSignal("overlaps other owner's claim " + other.id());
                    }
                }
                // 4) WorldGuard (buiten tx om — WG is read-only op zichzelf, en dan
                //    rollbackken wanneer conflict).
                Optional<String> wg = wgHook.firstBlockingRegion(existing.world(),
                        List.of(addedRegion), wgIgnoreGlobal);
                if (wg.isPresent()) {
                    throw new RollbackSignal("worldguard region " + wg.get());
                }
                long extraCost = union.extraCost();
                // 5) Ledger-spend binnen dezelfde connection.
                HolderKey holder = existing.owner().toLedgerHolder();
                PostingOutcome spend = null;
                if (extraCost > 0) {
                    spend = ledger.postInConnection(conn, PostingRequest.builder()
                            .holder(holder)
                            .delta(-extraCost)
                            .reason(existing.owner().type() == ClaimType.KINGDOM
                                    ? ClaimBlockReason.KINGDOM_CLAIM_SPEND
                                    : ClaimBlockReason.PERSONAL_CLAIM_SPEND)
                            .idempotencyKey(idempotencyKey)
                            .reference("claim:expand-atomic")
                            .build());
                    if (spend.kind() == PostingOutcome.Kind.INSUFFICIENT_BALANCE) {
                        throw new RollbackSignal("insufficient claim blocks: need " + extraCost);
                    }
                }
                hook.afterLedgerSpend();
                // 6) Vervang geometry stapsgewijs met failure-injection hooks.
                replaceGeometryStepwise(conn, claimId, existing.geometry(), union.geometry(),
                        "EXPAND", hook);
                hook.beforeCommit();
                return ExpandResult.ok(union.geometry(), extraCost, spend);
            });
        } catch (RollbackSignal rs) {
            return ExpandResult.rejected(rs.getMessage());
        }
    }

    // ---------- Atomic merge ----------

    public MergeResult mergeAtomic(ClaimType ownerType, UUID ownerId, WorldGuardHook wgHook,
                                   boolean wgIgnoreGlobal, Hook hook) {
        try {
            return database.withTransaction(conn -> {
                // 1) Herlees alle claims voor deze owner binnen tx.
                List<Claim> owned = readOwnerClaims(conn, ownerType, ownerId);
                if (owned.size() < 2) {
                    throw new RollbackSignal("no duplicates: owner has "
                            + owned.size() + " claim(s)");
                }
                // 2) Zelfde world?
                UUID world = owned.get(0).world().id();
                for (Claim c : owned) if (!c.world().id().equals(world)) {
                    throw new RollbackSignal("claims span multiple worlds");
                }
                // 3) Bereken bbox-union + valideer connected & rechthoekig.
                int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                long sumArea = 0;
                for (Claim c : owned) {
                    var b = c.geometry().bounds();
                    minX = Math.min(minX, b.minX()); minZ = Math.min(minZ, b.minZ());
                    maxX = Math.max(maxX, b.maxX()); maxZ = Math.max(maxZ, b.maxZ());
                    sumArea += c.geometry().area();
                }
                ClaimRectangle bbox = ClaimRectangle.ofCorners(minX, minZ, maxX, maxZ);
                long overlap = 0;
                for (int i = 0; i < owned.size(); i++) {
                    for (int j = i + 1; j < owned.size(); j++) {
                        overlap += overlapArea(owned.get(i).geometry().bounds(),
                                               owned.get(j).geometry().bounds());
                    }
                }
                long unionArea = sumArea - overlap;
                if (bbox.cost() != unionArea) {
                    throw new RollbackSignal("bounding box would enclose empty space; safe merge not possible");
                }
                // 4) Overlap-check tegen andere owners in de bbox.
                Set<UUID> conflictIds = new HashSet<>();
                for (Claim c : owned) conflictIds.add(c.id());
                for (Claim other : readOverlapping(conn, new dev.ankiesmp.dominium.core.common.WorldRef(world).id(), bbox)) {
                    if (!conflictIds.contains(other.id())) {
                        throw new RollbackSignal("bounding box overlaps other owner's claim " + other.id());
                    }
                }
                // 5) WorldGuard-check op de bbox.
                Optional<String> wg = wgHook.firstBlockingRegion(
                        new dev.ankiesmp.dominium.core.common.WorldRef(world),
                        List.of(bbox), wgIgnoreGlobal);
                if (wg.isPresent()) {
                    throw new RollbackSignal("worldguard region " + wg.get());
                }
                // 6) Netto claimblockdelta = 0 (unionArea == sumArea). Geen spend/refund nodig
                //    voor de merge zelf; edge-adjacent claims → 0. Overlap-cases (identieke
                //    geometry) leveren refund op.
                long delta = 0; // union == som → geen extra kosten
                Claim keep = owned.get(0);
                UUID keepId = keep.id();
                ClaimGeometry newGeometry = ClaimGeometry.ofRectangle(bbox);
                // 7) Vervang keep geometry stapsgewijs.
                replaceGeometryStepwise(conn, keepId, keep.geometry(), newGeometry, "MERGE", hook);
                // 8) Verwijder extra claims (cascade zorgt voor regions/revisions cleanup).
                for (int i = 1; i < owned.size(); i++) {
                    repo.deleteInConnection(conn, owned.get(i).id());
                }
                // 9) Als er overlap-refund nodig zou zijn (trivial duplicate zelfde geometry
                //    → union.area < sumArea), boek dan de refund.
                long refund = sumArea - unionArea; // positief bij overlap
                if (refund > 0) {
                    ledger.postInConnection(conn, PostingRequest.builder()
                            .holder(keep.owner().toLedgerHolder())
                            .delta(refund)
                            .reason(keep.owner().type() == ClaimType.KINGDOM
                                    ? ClaimBlockReason.KINGDOM_CLAIM_REFUND
                                    : ClaimBlockReason.PERSONAL_CLAIM_REFUND)
                            .idempotencyKey(UUID.nameUUIDFromBytes(
                                    ("merge-refund:" + keepId).getBytes(
                                            java.nio.charset.StandardCharsets.UTF_8)))
                            .reference("claim:merge-refund")
                            .build());
                }
                hook.beforeCommit();
                return new MergeResult(true, keepId, newGeometry,
                        owned.size() - 1, refund, delta, null,
                        owned.stream().map(Claim::id).toList());
            });
        } catch (RollbackSignal rs) {
            return MergeResult.rejected(rs.getMessage());
        }
    }

    private void replaceGeometryStepwise(java.sql.Connection conn, UUID claimId,
                                         ClaimGeometry oldGeometry, ClaimGeometry newGeometry,
                                         String reason, Hook hook) throws java.sql.SQLException {
        long delta = newGeometry.area() - oldGeometry.area();
        ClaimRectangle b = newGeometry.bounds();
        // A) regions delete
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM claim_regions WHERE claim_id = ?")) {
            ps.setString(1, claimId.toString());
            ps.executeUpdate();
        }
        hook.afterRegionsDelete();
        // B) region inserts
        long now = System.currentTimeMillis();
        boolean firstDone = false;
        for (ClaimRectangle r : newGeometry.regions()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO claim_regions(claim_id, min_x, min_z, max_x, max_z, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, claimId.toString());
                ps.setInt(2, r.minX()); ps.setInt(3, r.minZ());
                ps.setInt(4, r.maxX()); ps.setInt(5, r.maxZ());
                ps.setLong(6, now);
                ps.executeUpdate();
            }
            if (!firstDone) { hook.afterFirstRegionInsert(); firstDone = true; }
        }
        // C) bounds update
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE claims SET min_x=?, min_z=?, max_x=?, max_z=? WHERE id=?")) {
            ps.setInt(1, b.minX()); ps.setInt(2, b.minZ());
            ps.setInt(3, b.maxX()); ps.setInt(4, b.maxZ());
            ps.setString(5, claimId.toString());
            if (ps.executeUpdate() == 0) throw new IllegalStateException("claim disappeared: " + claimId);
        }
        hook.afterBoundsUpdate();
        // D) revision
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO claim_geometry_revisions(claim_id, min_x, min_z, max_x, max_z, reason, cost_delta, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, claimId.toString());
            ps.setInt(2, b.minX()); ps.setInt(3, b.minZ());
            ps.setInt(4, b.maxX()); ps.setInt(5, b.maxZ());
            ps.setString(6, reason);
            ps.setLong(7, delta);
            ps.setLong(8, now);
            ps.executeUpdate();
        }
        hook.afterRevisionWrite();
    }

    private static Optional<Claim> readClaim(java.sql.Connection conn, UUID id) throws java.sql.SQLException {
        Claim stub;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, world_id, owner_type, owner_id, min_x, min_z, max_x, max_z, created_at " +
                        "FROM claims WHERE id=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                stub = readStub(rs);
            }
        }
        List<ClaimRectangle> regs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT min_x, min_z, max_x, max_z FROM claim_regions WHERE claim_id=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    regs.add(new ClaimRectangle(rs.getInt(1), rs.getInt(2),
                            rs.getInt(3), rs.getInt(4)));
                }
            }
        }
        ClaimGeometry g = regs.isEmpty() ? ClaimGeometry.ofRectangle(stub.rect())
                : ClaimGeometry.ofRegions(regs);
        return Optional.of(new Claim(stub.id(), stub.world(), g, stub.owner(), stub.createdAt()));
    }

    private static List<Claim> readOwnerClaims(java.sql.Connection conn, ClaimType type, UUID ownerId)
            throws java.sql.SQLException {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM claims WHERE owner_type=? AND owner_id=?")) {
            ps.setString(1, type.name());
            ps.setString(2, ownerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(UUID.fromString(rs.getString(1)));
            }
        }
        List<Claim> out = new ArrayList<>();
        for (UUID id : ids) readClaim(conn, id).ifPresent(out::add);
        return out;
    }

    private static List<Claim> readOverlapping(java.sql.Connection conn, UUID worldId,
                                               ClaimRectangle rect) throws java.sql.SQLException {
        // Bounds-overlap via SQL, gevolgd door exacte region-check per candidate.
        List<UUID> candidateIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM claims WHERE world_id=? AND min_x<=? AND max_x>=? AND min_z<=? AND max_z>=?")) {
            ps.setString(1, worldId.toString());
            ps.setInt(2, rect.maxX()); ps.setInt(3, rect.minX());
            ps.setInt(4, rect.maxZ()); ps.setInt(5, rect.minZ());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) candidateIds.add(UUID.fromString(rs.getString(1)));
            }
        }
        List<Claim> out = new ArrayList<>();
        for (UUID id : candidateIds) {
            Optional<Claim> maybe = readClaim(conn, id);
            if (maybe.isPresent() && maybe.get().geometry().intersects(rect)) out.add(maybe.get());
        }
        return out;
    }

    private static Claim readStub(ResultSet rs) throws java.sql.SQLException {
        UUID id = UUID.fromString(rs.getString(1));
        var world = new dev.ankiesmp.dominium.core.common.WorldRef(UUID.fromString(rs.getString(2)));
        ClaimType type = ClaimType.valueOf(rs.getString(3));
        UUID ownerId = UUID.fromString(rs.getString(4));
        var owner = switch (type) {
            case PERSONAL -> dev.ankiesmp.dominium.core.claim.ClaimOwner.personal(ownerId);
            case KINGDOM  -> dev.ankiesmp.dominium.core.claim.ClaimOwner.kingdom(ownerId);
            case ADMIN    -> dev.ankiesmp.dominium.core.claim.ClaimOwner.admin(ownerId);
        };
        ClaimRectangle bounds = new ClaimRectangle(rs.getInt(5), rs.getInt(6),
                rs.getInt(7), rs.getInt(8));
        return new Claim(id, world, bounds, owner, java.time.Instant.ofEpochMilli(rs.getLong(9)));
    }

    private static long overlapArea(ClaimRectangle a, ClaimRectangle b) {
        int ox = Math.max(0, Math.min(a.maxX(), b.maxX()) - Math.max(a.minX(), b.minX()) + 1);
        int oz = Math.max(0, Math.min(a.maxZ(), b.maxZ()) - Math.max(a.minZ(), b.minZ()) + 1);
        return (long) ox * oz;
    }

    // ---------- Results ----------

    public record ExpandResult(boolean ok, ClaimGeometry newGeometry, long extraCost,
                               PostingOutcome spend, String rejection) {
        public static ExpandResult ok(ClaimGeometry g, long ex, PostingOutcome sp) {
            return new ExpandResult(true, g, ex, sp, null);
        }
        public static ExpandResult rejected(String msg) {
            return new ExpandResult(false, null, 0, null, msg);
        }
    }

    public record MergeResult(boolean ok, UUID keepClaimId, ClaimGeometry newGeometry,
                              int removedClaims, long refund, long spend,
                              String rejection, List<UUID> touchedClaimIds) {
        public static MergeResult rejected(String msg) {
            return new MergeResult(false, null, null, 0, 0, 0, msg, List.of());
        }
    }

    /** Sentinel om binnen {@link Database#withTransaction} volledig te rollbacken. */
    private static final class RollbackSignal extends RuntimeException {
        private static final long serialVersionUID = 1L;
        RollbackSignal(String message) { super(message, null, false, false); }
    }

    /** Helper voor callers: na commit index/cache/guard bijwerken. */
    public void applyAfterMerge(MergeResult r, ClaimIndex index,
                                dev.ankiesmp.dominium.core.territory.TerritoryContextCache cache,
                                MutableClaimMutationGuard guard,
                                ClaimType type, UUID ownerId) {
        if (!r.ok()) return;
        for (UUID id : r.touchedClaimIds()) {
            if (!id.equals(r.keepClaimId())) index.remove(id);
            if (cache != null) cache.invalidateClaim(id);
        }
        index.replaceGeometry(r.keepClaimId(), r.newGeometry());
        if (cache != null) cache.invalidateClaim(r.keepClaimId());
        guard.unblock(type, ownerId);
    }
}
