package dev.ankiesmp.dominium.core.claim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaimExpansionTest {

    private final ClaimRectangle base = ClaimRectangle.ofCorners(0, 0, 9, 9);

    // CE-001 — selectie geheel binnen bestaande claim → NO_OP.
    @Test
    void selectionInsideExistingIsNoOp() {
        var sel = ClaimRectangle.ofCorners(2, 2, 5, 5);
        var r = ClaimExpansion.plan(base, sel);
        assertEquals(ClaimExpansion.Kind.NO_OP, r.kind());
    }

    // CE-002 — selectie los van claim → REJECT_DETACHED.
    @Test
    void detachedIsRejected() {
        var sel = ClaimRectangle.ofCorners(20, 20, 25, 25);
        assertEquals(ClaimExpansion.Kind.REJECT_DETACHED,
                ClaimExpansion.plan(base, sel).kind());
    }

    // CE-003 — alleen hoekcontact → REJECT_CORNER_ONLY.
    @Test
    void cornerOnlyIsRejected() {
        // Claim is (0..9,0..9); een claim (10..15,10..15) deelt alleen de hoek.
        var sel = ClaimRectangle.ofCorners(10, 10, 15, 15);
        assertEquals(ClaimExpansion.Kind.REJECT_CORNER_ONLY,
                ClaimExpansion.plan(base, sel).kind());
    }

    // CE-004 — edge-adjacency + rechthoekige union → OK.
    @Test
    void edgeAdjacencyProducesUnion() {
        // Uitbreiding aan oostzijde: (10..19, 0..9).
        var sel = ClaimRectangle.ofCorners(10, 0, 19, 9);
        var r = ClaimExpansion.plan(base, sel);
        assertEquals(ClaimExpansion.Kind.OK, r.kind());
        assertEquals(0, r.newRect().minX());
        assertEquals(19, r.newRect().maxX());
        assertEquals(0, r.newRect().minZ());
        assertEquals(9, r.newRect().maxZ());
    }

    // CE-005 — adjacency maar bounding-box zou L-vorm dekken → NOT_RECTANGULAR.
    @Test
    void lShapeRejected() {
        // Uitbreiding oost, maar korter dan de bestaande diepte → L-vorm.
        var sel = ClaimRectangle.ofCorners(10, 0, 15, 4);
        assertEquals(ClaimExpansion.Kind.REJECT_NOT_RECTANGULAR,
                ClaimExpansion.plan(base, sel).kind());
    }

    // CE-006 — overlap + rechthoekige union → OK.
    @Test
    void overlappingRectangularUnion() {
        var sel = ClaimRectangle.ofCorners(5, 0, 14, 9);
        var r = ClaimExpansion.plan(base, sel);
        assertEquals(ClaimExpansion.Kind.OK, r.kind());
        assertEquals(14, r.newRect().maxX());
    }
}
