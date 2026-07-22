package dev.ankiesmp.dominium.core.claim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClaimGeometryTest {

    // CG-001 — rechthoek: area = w*d.
    @Test
    void rectangleArea() {
        var g = ClaimGeometry.ofRectangle(ClaimRectangle.ofCorners(0, 0, 9, 9));
        assertEquals(100L, g.area());
    }

    // CG-002 — L-vorm: twee edge-adjacent rectangles, area = som.
    @Test
    void lShapeAreaSum() {
        var g = ClaimGeometry.ofRegions(List.of(
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimRectangle.ofCorners(10, 0, 14, 4)));
        // 100 + 25 = 125
        assertEquals(125L, g.area());
        assertTrue(g.connected());
    }

    // CG-003 — contains werkt per region.
    @Test
    void containsPerRegion() {
        var g = ClaimGeometry.ofRegions(List.of(
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimRectangle.ofCorners(10, 0, 14, 4)));
        assertTrue(g.contains(5, 5));
        assertTrue(g.contains(12, 2));
        assertFalse(g.contains(12, 8), "gap tussen regions is niet geclaimd");
        assertFalse(g.contains(20, 20));
    }

    // CG-004 — union met adjacent rect → OK, extraCost = alleen nieuwe unieke blocks.
    @Test
    void unionAddsAdjacentRect() {
        var g = ClaimGeometry.ofRectangle(ClaimRectangle.ofCorners(0, 0, 9, 9));
        var res = g.union(ClaimRectangle.ofCorners(10, 0, 19, 9));
        assertEquals(ClaimGeometry.UnionKind.OK, res.kind());
        assertEquals(100L, res.extraCost(), "100 nieuwe blocks");
        assertEquals(200L, res.geometry().area());
    }

    // CG-005 — union met overlappende selectie → alleen unieke blocks tellen als extra.
    @Test
    void unionOverlapCostsOnlyUniqueBlocks() {
        var g = ClaimGeometry.ofRectangle(ClaimRectangle.ofCorners(0, 0, 9, 9));
        // Selectie 5..14, dus overlap = 5..9 (5 wide × 10 deep = 50), nieuw = 100-50 = 50
        var res = g.union(ClaimRectangle.ofCorners(5, 0, 14, 9));
        assertEquals(ClaimGeometry.UnionKind.OK, res.kind());
        assertEquals(50L, res.extraCost());
    }

    // CG-006 — union met selectie geheel binnen → NO_OP.
    @Test
    void unionInsideIsNoOp() {
        var g = ClaimGeometry.ofRectangle(ClaimRectangle.ofCorners(0, 0, 9, 9));
        var res = g.union(ClaimRectangle.ofCorners(2, 2, 5, 5));
        assertEquals(ClaimGeometry.UnionKind.NO_OP, res.kind());
    }

    // CG-007 — union met detached selectie → REJECT_DETACHED.
    @Test
    void unionDetachedRejected() {
        var g = ClaimGeometry.ofRectangle(ClaimRectangle.ofCorners(0, 0, 9, 9));
        var res = g.union(ClaimRectangle.ofCorners(100, 100, 109, 109));
        assertEquals(ClaimGeometry.UnionKind.REJECT_DETACHED, res.kind());
    }

    // CG-008 — union met alleen hoekcontact → REJECT_CORNER_ONLY.
    @Test
    void unionCornerOnlyRejected() {
        var g = ClaimGeometry.ofRectangle(ClaimRectangle.ofCorners(0, 0, 9, 9));
        // (10..14, 10..14): hoekcontact op (10,10) tegen (9,9).
        var res = g.union(ClaimRectangle.ofCorners(10, 10, 14, 14));
        assertEquals(ClaimGeometry.UnionKind.REJECT_CORNER_ONLY, res.kind());
    }

    // CG-009 — bounds klopt.
    @Test
    void boundsAcrossRegions() {
        var g = ClaimGeometry.ofRegions(List.of(
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimRectangle.ofCorners(10, 0, 14, 4)));
        var b = g.bounds();
        assertEquals(0, b.minX());
        assertEquals(14, b.maxX());
        assertEquals(0, b.minZ());
        assertEquals(9, b.maxZ());
    }

    // CG-010 — trap-vorm: drie edge-connected rectangles.
    @Test
    void trapShapeConnected() {
        var g = ClaimGeometry.ofRegions(List.of(
                ClaimRectangle.ofCorners(0, 0, 4, 4),
                ClaimRectangle.ofCorners(5, 0, 9, 4),
                ClaimRectangle.ofCorners(10, 0, 14, 4)));
        assertTrue(g.connected());
        assertEquals(75L, g.area());
    }
}
