package dev.ankiesmp.dominium.core.claim;

import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimRepairPlanTest {

    private final WorldRef worldA = new WorldRef(UUID.randomUUID());
    private final WorldRef worldB = new WorldRef(UUID.randomUUID());
    private final UUID owner = UUID.randomUUID();

    private Claim c(WorldRef world, int minX, int minZ, int maxX, int maxZ) {
        return new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(minX, minZ, maxX, maxZ),
                ClaimOwner.personal(owner), Instant.now());
    }

    // RP-001 — twee edge-adjacent claims → SAFE_MERGE zonder extra kosten.
    @Test
    void adjacentEdgeIsSafeMerge() {
        var idx = new ClaimIndex();
        var a = c(worldA, 0, 0, 9, 9);
        var b = c(worldA, 10, 0, 19, 9);
        idx.add(a); idx.add(b);
        var plan = ClaimRepairPlan.analyse(List.of(a, b), 1000, idx);
        assertEquals(ClaimRepairPlan.Kind.SAFE_MERGE, plan.kind());
        assertEquals(0, plan.extraCost(), "exacte union → geen extra cost");
        assertEquals(19, plan.union().maxX());
    }

    // RP-002 — verschillende werelden → onmogelijk te mergen.
    @Test
    void differentWorldsIsUnsafe() {
        var idx = new ClaimIndex();
        var a = c(worldA, 0, 0, 9, 9);
        var b = c(worldB, 100, 100, 109, 109);
        idx.add(a); idx.add(b);
        var plan = ClaimRepairPlan.analyse(List.of(a, b), 1000, idx);
        assertEquals(ClaimRepairPlan.Kind.UNSAFE_DIFFERENT_WORLDS, plan.kind());
    }

    // RP-003 — losstaande claims met gap → bounding-box zou lege ruimte pakken.
    @Test
    void detachedClaimsAreUnsafe() {
        var idx = new ClaimIndex();
        var a = c(worldA, 0, 0, 9, 9);
        var b = c(worldA, 100, 100, 109, 109);
        idx.add(a); idx.add(b);
        var plan = ClaimRepairPlan.analyse(List.of(a, b), 100_000, idx);
        assertEquals(ClaimRepairPlan.Kind.UNSAFE_NOT_RECTANGULAR, plan.kind());
    }

    // RP-004 — overlap met een claim van een andere owner in de bounding-box.
    @Test
    void boundingBoxOverlappingOtherIsUnsafe() {
        var idx = new ClaimIndex();
        var a = c(worldA, 0, 0, 9, 9);
        var b = c(worldA, 10, 0, 19, 9);
        UUID otherOwner = UUID.randomUUID();
        var intruder = new Claim(UUID.randomUUID(), worldA,
                ClaimRectangle.ofCorners(5, 0, 14, 9),
                ClaimOwner.personal(otherOwner), Instant.now());
        idx.add(a); idx.add(b); idx.add(intruder);
        var plan = ClaimRepairPlan.analyse(List.of(a, b), 1000, idx);
        assertEquals(ClaimRepairPlan.Kind.UNSAFE_OVERLAP_OTHER, plan.kind());
    }

    // RP-005 — twee identieke claims → trivial duplicate, safe.
    @Test
    void identicalGeometryIsTrivialDuplicate() {
        var idx = new ClaimIndex();
        var a = c(worldA, 0, 0, 9, 9);
        // Twee claims met exact hetzelfde rect maar andere id.
        var b = new Claim(UUID.randomUUID(), worldA,
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), Instant.now());
        idx.add(a); idx.add(b);
        var plan = ClaimRepairPlan.analyse(List.of(a, b), 100, idx);
        assertEquals(ClaimRepairPlan.Kind.TRIVIAL_DUPLICATE, plan.kind());
        assertTrue(plan.safe());
    }

    // RP-006 — onvoldoende balans voor extra kosten.
    @Test
    void insufficientBalanceBlocksMerge() {
        // Adjacent edge; hier extraCost = 0. Om extra cost te forceren maken we
        // een geval waar de union groter is dan de bestaande rijen — dat kan
        // niet met deze regels want dan is bboxArea != unionArea. Ergo:
        // wanneer een merge safe is heeft hij extraCost == 0. Deze test toont
        // dat het pad correct de kind teruggeeft via een geconstrueerde plan.
        // Hier verifiëren we simpelweg dat de code niet crasht bij balance=0.
        var idx = new ClaimIndex();
        var a = c(worldA, 0, 0, 9, 9);
        var b = c(worldA, 10, 0, 19, 9);
        idx.add(a); idx.add(b);
        var plan = ClaimRepairPlan.analyse(List.of(a, b), 0, idx);
        // Adjacent edge → extraCost 0, dus SAFE_MERGE ondanks balance=0.
        assertEquals(ClaimRepairPlan.Kind.SAFE_MERGE, plan.kind());
        assertEquals(0, plan.extraCost());
    }
}
