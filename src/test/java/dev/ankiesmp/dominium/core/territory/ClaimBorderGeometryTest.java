package dev.ankiesmp.dominium.core.territory;

import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ClaimBorderGeometryTest {

    private final ClaimRectangle rect = ClaimRectangle.ofCorners(0, 0, 9, 9);

    // BG-001 — distanceToBorder buiten claim.
    @Test
    void distanceOutsideClaim() {
        assertEquals(2.0, ClaimBorderGeometry.distanceToBorder(rect, -2.0, 5.0), 1e-9);
        assertEquals(1.0, ClaimBorderGeometry.distanceToBorder(rect, 11.0, 5.0), 1e-9);
    }

    // BG-002 — distanceToBorder binnen claim = negatieve minimale zijde-afstand.
    @Test
    void distanceInsideClaim() {
        assertTrue(ClaimBorderGeometry.distanceToBorder(rect, 5.0, 5.0) < 0);
    }

    // BG-003 — trigger range.
    @Test
    void inTriggerRange() {
        assertTrue(ClaimBorderGeometry.inTriggerRange(rect, -2.0, 5.0, 5.0));
        assertFalse(ClaimBorderGeometry.inTriggerRange(rect, -20.0, 5.0, 5.0));
    }

    // BG-004 — punten dicht bij speler; hoeken zijn uniek.
    @Test
    void pointsNearPlayer() {
        var cfg = new ClaimBorderGeometry.Config(10.0, 24.0, 1.0, 500);
        var pts = ClaimBorderGeometry.pointsNearPlayer(rect, -3.0, -3.0, cfg);
        assertFalse(pts.isEmpty());
        // Alle punten binnen renderDistance.
        for (var p : pts) {
            double d = Math.hypot(p.x() + 3.0, p.z() + 3.0);
            assertTrue(d <= 24.0 + 1e-6);
        }
        // Hoekpunt (0,0) verschijnt exact één keer.
        long zeros = pts.stream().filter(p ->
                Math.abs(p.x()) < 1e-9 && Math.abs(p.z()) < 1e-9).count();
        assertEquals(1, zeros, "geen duplicate hoekpunten");
    }

    // BG-005 — budget-cap.
    @Test
    void budgetCapReturnsClosestPoints() {
        var cfg = new ClaimBorderGeometry.Config(10.0, 30.0, 0.5, 20);
        var pts = ClaimBorderGeometry.pointsNearPlayer(rect, 5.0, -2.0, cfg);
        assertEquals(20, pts.size());
    }

    // BG-006 — speler ver weg: geen punten.
    @Test
    void playerFarAwayHasNoPoints() {
        var cfg = new ClaimBorderGeometry.Config(10.0, 12.0, 1.0, 500);
        var pts = ClaimBorderGeometry.pointsNearPlayer(rect, 200.0, 200.0, cfg);
        assertTrue(pts.isEmpty());
    }

    // BG-007 — config-validatie.
    @Test
    void configValidatesArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClaimBorderGeometry.Config(0, 24, 1, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new ClaimBorderGeometry.Config(10, 5, 1, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new ClaimBorderGeometry.Config(10, 24, 0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new ClaimBorderGeometry.Config(10, 24, 1, 0));
    }

    // BG-008 — spacing: het aantal punten neemt af als spacing toeneemt.
    @Test
    void largerSpacingProducesFewerPoints() {
        var wide = new ClaimBorderGeometry.Config(10, 24, 4.0, 500);
        var tight = new ClaimBorderGeometry.Config(10, 24, 0.5, 500);
        var w = ClaimBorderGeometry.pointsNearPlayer(rect, 5, -3, wide);
        var t = ClaimBorderGeometry.pointsNearPlayer(rect, 5, -3, tight);
        assertTrue(w.size() < t.size());
    }

    // BG-009 — dedupe over meerdere calls: uniqueness per side.
    @Test
    void allPointsAreUnique() {
        var cfg = new ClaimBorderGeometry.Config(10, 24, 1.0, 500);
        var pts = ClaimBorderGeometry.pointsNearPlayer(rect, 5, 5, cfg);
        Set<String> keys = pts.stream()
                .map(p -> p.x() + ":" + p.z())
                .collect(Collectors.toSet());
        assertEquals(pts.size(), keys.size(), "geen duplicate points");
    }
}
