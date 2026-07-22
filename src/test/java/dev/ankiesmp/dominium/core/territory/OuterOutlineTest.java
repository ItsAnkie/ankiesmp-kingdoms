package dev.ankiesmp.dominium.core.territory;

import dev.ankiesmp.dominium.core.claim.ClaimGeometry;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OuterOutlineTest {

    // OO-001 — single rectangle: alle 4 zijden zijn buitenoutline.
    @Test
    void singleRectangleFullPerimeter() {
        var g = ClaimGeometry.ofRectangle(ClaimRectangle.ofCorners(0, 0, 2, 2));
        var segs = OuterOutline.segments(g);
        // 3x3 rect → perimeter = 4 * 3 = 12 unit-segments.
        assertEquals(12, segs.size());
    }

    // OO-002 — L-vorm: gedeelde interne edge wordt NIET getekend.
    @Test
    void lShapeSkipsInternalSeam() {
        // (0..9, 0..9) + (10..14, 0..4) delen X=10 op z=0..4 (5 blocks).
        var g = ClaimGeometry.ofRegions(List.of(
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimRectangle.ofCorners(10, 0, 14, 4)));
        var segs = OuterOutline.segments(g);
        // Perimeter van de L-vereniging: 15 + 5 + 5 + 5 + 10 + 10 = 50 unit segments.
        assertEquals(50, segs.size());
        // Geen segment mag PRECIES op de interne seam liggen: alle segmenten
        // met x1==10 && x2==10 moeten óf z in (0..4) NIET zijn (want daar is
        // de seam), óf onderdeel van de externe oostrand (z 0..4) van linkse
        // rectangle. Interne seam = X=10, z in [0,4]. In outline mag zo'n
        // segment niet voorkomen.
        for (var s : segs) {
            if (s.x1() == 10 && s.x2() == 10) {
                double zMid = (s.z1() + s.z2()) / 2.0;
                assertFalse(zMid >= 0 && zMid <= 5,
                        "interne seam segment gevonden: " + s);
            }
        }
    }

    // OO-003 — T-vorm: buitenoutline heeft geen seams tussen de armen.
    @Test
    void tShapePerimeter() {
        // Horizontale balk (0..14, 0..4) + verticale arm (5..9, 5..14).
        var g = ClaimGeometry.ofRegions(List.of(
                ClaimRectangle.ofCorners(0, 0, 14, 4),
                ClaimRectangle.ofCorners(5, 5, 9, 14)));
        long area = g.area();
        assertEquals(15 * 5 + 5 * 10, area);
        // Buitenperimeter van T = 15 + 5 + 5 + 10 + 5 + 10 + 5 + 5 = 60
        var segs = OuterOutline.segments(g);
        assertEquals(60, segs.size());
    }

    // OO-004 — samplepunten dicht bij speler; punten uniek en binnen renderDistance.
    @Test
    void pointsNearPlayerRespectRenderDistance() {
        var g = ClaimGeometry.ofRegions(List.of(
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimRectangle.ofCorners(10, 0, 14, 4)));
        var cfg = new ClaimBorderGeometry.Config(5.0, 11.0, 0.9, 200);
        var pts = OuterOutline.pointsNearPlayer(g, -2, 5, cfg);
        for (var p : pts) {
            assertTrue(Math.hypot(p.x() + 2, p.z() - 5) <= 11.0 + 1e-6);
        }
        // Alle punten uniek.
        for (int i = 0; i < pts.size(); i++)
            for (int j = i + 1; j < pts.size(); j++)
                assertFalse(Math.abs(pts.get(i).x() - pts.get(j).x()) < 1e-9
                        && Math.abs(pts.get(i).z() - pts.get(j).z()) < 1e-9);
    }
}
