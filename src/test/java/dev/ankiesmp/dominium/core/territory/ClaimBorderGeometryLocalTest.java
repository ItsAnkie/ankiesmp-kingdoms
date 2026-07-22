package dev.ankiesmp.dominium.core.territory;

import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifieert het compacte "local segments"-render-pad. */
class ClaimBorderGeometryLocalTest {

    private final ClaimRectangle bigClaim = ClaimRectangle.ofCorners(0, 0, 99, 99);

    // LG-001 — speler dicht bij één zijde → alleen die zijde wordt gerenderd.
    @Test
    void nearOneSideRendersOnlyThatSide() {
        var cfg = new ClaimBorderGeometry.Config(5.0, 11.0, 0.9, 60);
        var pts = ClaimBorderGeometry.localPointsNearPlayer(bigClaim, -2.0, 50.0, cfg);
        assertFalse(pts.isEmpty());
        // Alle punten moeten op X=minX (=0) liggen — dat is de west-zijde;
        // niet op de oost, noord of zuidzijde van deze grote claim.
        for (var p : pts) {
            assertEquals(0.0, p.x(), 1e-9,
                    "verwacht alleen west-zijde punten, kreeg " + p);
        }
    }

    // LG-002 — hoek-scenario: hoogstens twee zijdes gerenderd.
    @Test
    void nearCornerRendersAtMostTwoSides() {
        var cfg = new ClaimBorderGeometry.Config(5.0, 11.0, 0.9, 60);
        var pts = ClaimBorderGeometry.localPointsNearPlayer(bigClaim, -2.0, -2.0, cfg);
        // Alle punten liggen op X=0 (west) of Z=0 (zuid).
        for (var p : pts) {
            boolean onWest = Math.abs(p.x()) < 1e-9;
            boolean onSouth = Math.abs(p.z()) < 1e-9;
            assertTrue(onWest || onSouth, "point " + p + " should lie on west or south side");
        }
    }

    // LG-003 — max-budget respecteert.
    @Test
    void respectsMaxBudget() {
        var cfg = new ClaimBorderGeometry.Config(5.0, 11.0, 0.5, 12);
        var pts = ClaimBorderGeometry.localPointsNearPlayer(bigClaim, -2.0, 50.0, cfg);
        assertTrue(pts.size() <= 12);
    }

    // LG-004 — dedupe: elk punt uniek.
    @Test
    void pointsAreUnique() {
        var cfg = new ClaimBorderGeometry.Config(5.0, 11.0, 0.9, 60);
        var pts = ClaimBorderGeometry.localPointsNearPlayer(bigClaim, -2.0, -2.0, cfg);
        for (int i = 0; i < pts.size(); i++) {
            for (int j = i + 1; j < pts.size(); j++) {
                var a = pts.get(i);
                var b = pts.get(j);
                assertFalse(Math.abs(a.x() - b.x()) < 1e-9 && Math.abs(a.z() - b.z()) < 1e-9,
                        "duplicate points at " + a);
            }
        }
    }

    // LG-005 — speler ver van elke zijde → geen tweede-zijde meegenomen.
    @Test
    void farFromSecondSideDoesNotAddIt() {
        // 100-wide claim; speler bij (-2, 50) is dicht bij west maar 50+50 blocks
        // van noord/zuid. Alleen west zou renderen.
        var cfg = new ClaimBorderGeometry.Config(5.0, 11.0, 0.9, 60);
        var pts = ClaimBorderGeometry.localPointsNearPlayer(bigClaim, -2.0, 50.0, cfg);
        boolean anySouth = pts.stream().anyMatch(p -> Math.abs(p.z()) < 1e-9);
        boolean anyNorth = pts.stream().anyMatch(p -> Math.abs(p.z() - 100.0) < 1e-9);
        boolean anyEast  = pts.stream().anyMatch(p -> Math.abs(p.x() - 100.0) < 1e-9);
        assertFalse(anySouth, "should not include south side");
        assertFalse(anyNorth, "should not include north side");
        assertFalse(anyEast,  "should not include east side");
    }
}
