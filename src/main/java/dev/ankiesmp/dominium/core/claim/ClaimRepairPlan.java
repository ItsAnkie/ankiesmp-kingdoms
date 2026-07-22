package dev.ankiesmp.dominium.core.claim;

import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure regels voor duplicate-claim repair. Bepaalt of een safe merge
 * mogelijk is, en zo ja: welke rectangle + kostendelta.
 *
 * <p>Merge is <b>alleen</b> veilig wanneer:
 * <ul>
 *   <li>alle conflict-claims in dezelfde wereld liggen;</li>
 *   <li>de bounding-box gelijk is aan de vereniging van alle claim-oppervlaktes
 *       (dus geen L-vorm / geen enorm tussengebied);</li>
 *   <li>de bounding-box in de {@link ClaimIndex} niet overlapt met claims van
 *       andere owners;</li>
 *   <li>de holder voldoende saldo heeft voor de extra kosten (bounding-area
 *       minus som van bestaande gebieden).</li>
 * </ul>
 */
public final class ClaimRepairPlan {

    private ClaimRepairPlan() {}

    public enum Kind { SAFE_MERGE, UNSAFE_DIFFERENT_WORLDS, UNSAFE_NOT_RECTANGULAR,
                       UNSAFE_OVERLAP_OTHER, INSUFFICIENT_BALANCE, TRIVIAL_DUPLICATE }

    public record Plan(Kind kind, ClaimRectangle union, long extraCost, long currentBalance,
                       String message) {
        public boolean safe() { return kind == Kind.SAFE_MERGE || kind == Kind.TRIVIAL_DUPLICATE; }
    }

    /**
     * @param claims alle conflict-claims voor deze owner (minstens 2).
     * @param currentBalance huidige balans van de owner-holder (voor extra cost).
     * @param index de bestaande claim-index (voor overlap-check).
     */
    public static Plan analyse(List<Claim> claims, long currentBalance, ClaimIndex index) {
        Objects.requireNonNull(claims);
        Objects.requireNonNull(index);
        if (claims.size() < 2) {
            throw new IllegalArgumentException("need at least 2 claims to analyse");
        }
        // Alle in dezelfde wereld?
        UUID world = claims.get(0).world().id();
        for (Claim c : claims) {
            if (!c.world().id().equals(world)) {
                return new Plan(Kind.UNSAFE_DIFFERENT_WORLDS, null, 0, currentBalance,
                        "Claims are in different worlds and cannot be merged.");
            }
        }
        // Trivial duplicate: alle claims hebben identieke geometry → merge = één ervan.
        boolean allSame = true;
        ClaimRectangle first = claims.get(0).rect();
        for (int i = 1; i < claims.size(); i++) {
            ClaimRectangle r = claims.get(i).rect();
            if (r.minX() != first.minX() || r.maxX() != first.maxX()
                    || r.minZ() != first.minZ() || r.maxZ() != first.maxZ()) {
                allSame = false; break;
            }
        }
        if (allSame) {
            return new Plan(Kind.TRIVIAL_DUPLICATE, first, 0, currentBalance,
                    "All conflict claims cover the same area; keeping one and deleting the others is safe.");
        }
        // Bounding box.
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        long sumArea = 0;
        for (Claim c : claims) {
            var r = c.rect();
            minX = Math.min(minX, r.minX());
            minZ = Math.min(minZ, r.minZ());
            maxX = Math.max(maxX, r.maxX());
            maxZ = Math.max(maxZ, r.maxZ());
            sumArea += r.cost();
        }
        ClaimRectangle bbox = ClaimRectangle.ofCorners(minX, minZ, maxX, maxZ);
        long bboxArea = bbox.cost();

        // Overlap tussen conflict-claims tellen (paarsgewijs).
        long overlapArea = 0;
        for (int i = 0; i < claims.size(); i++) {
            for (int j = i + 1; j < claims.size(); j++) {
                overlapArea += overlapArea(claims.get(i).rect(), claims.get(j).rect());
            }
        }
        long unionArea = sumArea - overlapArea;
        if (bboxArea != unionArea) {
            return new Plan(Kind.UNSAFE_NOT_RECTANGULAR, bbox, 0, currentBalance,
                    "The bounding rectangle would enclose extra empty space; a safe merge is not possible.");
        }
        // Overlap-check tegen andere owners.
        var ownConflictIds = claims.stream().map(Claim::id).collect(java.util.stream.Collectors.toSet());
        var candidates = index.overlapping(claims.get(0).world(), bbox);
        for (Claim other : candidates) {
            if (ownConflictIds.contains(other.id())) continue;
            return new Plan(Kind.UNSAFE_OVERLAP_OTHER, bbox, 0, currentBalance,
                    "Bounding rectangle overlaps another claim (" + other.id() + "); cannot merge safely.");
        }
        long extraCost = bboxArea - unionArea; // = 0 als exacte union
        if (extraCost > currentBalance) {
            return new Plan(Kind.INSUFFICIENT_BALANCE, bbox, extraCost, currentBalance,
                    "Insufficient claim blocks: need " + extraCost + " extra, have " + currentBalance + ".");
        }
        return new Plan(Kind.SAFE_MERGE, bbox, extraCost, currentBalance,
                "Safe merge: " + bboxArea + " total blocks (" + extraCost + " extra).");
    }

    private static long overlapArea(ClaimRectangle a, ClaimRectangle b) {
        int ox = Math.max(0, Math.min(a.maxX(), b.maxX()) - Math.max(a.minX(), b.minX()) + 1);
        int oz = Math.max(0, Math.min(a.maxZ(), b.maxZ()) - Math.max(a.minZ(), b.minZ()) + 1);
        return (long) ox * oz;
    }
}
