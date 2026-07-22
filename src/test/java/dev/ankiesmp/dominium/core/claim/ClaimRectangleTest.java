package dev.ankiesmp.dominium.core.claim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaimRectangleTest {

    @Test
    void areaEqualsWidthTimesDepthInclusive() {
        ClaimRectangle rect = ClaimRectangle.ofCorners(0, 0, 9, 24); // 10 x 25
        assertEquals(10, rect.width());
        assertEquals(25, rect.depth());
        assertEquals(250L, rect.area());
        assertEquals(250L, rect.cost());
    }

    @Test
    void ofCornersOrdersMinMax() {
        ClaimRectangle rect = ClaimRectangle.ofCorners(9, 24, 0, 0);
        assertEquals(0, rect.minX());
        assertEquals(0, rect.minZ());
        assertEquals(9, rect.maxX());
        assertEquals(24, rect.maxZ());
    }

    @Test
    void resizeDeltaHandlesExpandAndShrink() {
        ClaimRectangle a = ClaimRectangle.ofCorners(0, 0, 9, 9); // 10x10 = 100
        ClaimRectangle bigger = ClaimRectangle.ofCorners(0, 0, 14, 9); // 15x10 = 150
        ClaimRectangle smaller = ClaimRectangle.ofCorners(0, 0, 7, 9); // 8x10 = 80

        assertEquals(50L, a.resizeCostDelta(bigger));
        assertEquals(-20L, a.resizeCostDelta(smaller));
    }

    @Test
    void intersectsDetectsOverlap() {
        ClaimRectangle a = ClaimRectangle.ofCorners(0, 0, 9, 9);
        ClaimRectangle b = ClaimRectangle.ofCorners(9, 9, 20, 20);   // touches at corner
        ClaimRectangle c = ClaimRectangle.ofCorners(10, 10, 20, 20); // adjacent (no overlap)
        assertTrue(a.intersects(b));
        assertFalse(a.intersects(c));
    }

    @Test
    void chebyshevGapMatchesExpectation() {
        ClaimRectangle a = ClaimRectangle.ofCorners(0, 0, 9, 9);
        ClaimRectangle b = ClaimRectangle.ofCorners(15, 5, 20, 8);   // 5 blocks away on X
        ClaimRectangle c = ClaimRectangle.ofCorners(0, 15, 9, 20);   // 5 blocks away on Z
        assertEquals(5, a.chebyshevGapTo(b));
        assertEquals(5, a.chebyshevGapTo(c));
    }

    @Test
    void sharesEdgeTrueWhenExactlyAdjacent() {
        ClaimRectangle a = ClaimRectangle.ofCorners(0, 0, 9, 9);
        ClaimRectangle right = ClaimRectangle.ofCorners(10, 0, 20, 9);
        ClaimRectangle apart = ClaimRectangle.ofCorners(11, 0, 20, 9);
        assertTrue(a.sharesEdge(right));
        assertFalse(a.sharesEdge(apart));
    }

    @Test
    void rejectsInvalidRectangle() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClaimRectangle(5, 0, 4, 0));
    }
}
