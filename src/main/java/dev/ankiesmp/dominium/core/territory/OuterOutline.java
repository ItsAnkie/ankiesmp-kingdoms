package dev.ankiesmp.dominium.core.territory;

import dev.ankiesmp.dominium.core.claim.ClaimGeometry;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Bepaalt de buitenomtrek van een multi-region {@link ClaimGeometry}
 * zonder interne seams. Per regio's 4 zijden: elk unit-segment is deel
 * van de buitenoutline exact wanneer de buurcel (aan de "buiten"-kant)
 * NIET in de geometry ligt.
 */
public final class OuterOutline {

    private OuterOutline() {}

    public record Segment(double x1, double z1, double x2, double z2) {}

    /** Retourneert de buitensegmenten in blok-coördinaten (unit-length). */
    public static List<Segment> segments(ClaimGeometry geometry) {
        List<Segment> out = new ArrayList<>();
        for (ClaimRectangle r : geometry.regions()) {
            // South-edge Z = minZ; naar buiten is Z-1.
            for (int x = r.minX(); x <= r.maxX(); x++) {
                if (!geometry.contains(x, r.minZ() - 1)) {
                    out.add(new Segment(x, r.minZ(), x + 1, r.minZ()));
                }
            }
            // North-edge Z = maxZ+1; naar buiten is Z+1.
            for (int x = r.minX(); x <= r.maxX(); x++) {
                if (!geometry.contains(x, r.maxZ() + 1)) {
                    out.add(new Segment(x, r.maxZ() + 1, x + 1, r.maxZ() + 1));
                }
            }
            // West-edge X = minX; naar buiten is X-1.
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!geometry.contains(r.minX() - 1, z)) {
                    out.add(new Segment(r.minX(), z, r.minX(), z + 1));
                }
            }
            // East-edge X = maxX+1; naar buiten is X+1.
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!geometry.contains(r.maxX() + 1, z)) {
                    out.add(new Segment(r.maxX() + 1, z, r.maxX() + 1, z + 1));
                }
            }
        }
        return out;
    }

    /**
     * Genereert een dichte set outline-samplepunten (op spacing) voor
     * particle-rendering. Filtert op maximum {@code renderDistance} tot de
     * speler en cap op {@code maxPoints}.
     */
    public static List<ClaimBorderGeometry.Point> pointsNearPlayer(
            ClaimGeometry geometry, double px, double pz, ClaimBorderGeometry.Config cfg) {
        List<Segment> segs = segments(geometry);
        List<ClaimBorderGeometry.Point> out = new ArrayList<>();
        double render = cfg.renderDistance();
        double spacing = Math.max(0.1, cfg.spacing());
        for (Segment s : segs) {
            double dx = s.x2 - s.x1, dz = s.z2 - s.z1;
            double len = Math.hypot(dx, dz);
            if (len < 1e-9) continue;
            int steps = Math.max(1, (int) Math.ceil(len / spacing));
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / (double) steps;
                double x = s.x1 + dx * t;
                double z = s.z1 + dz * t;
                if (Math.hypot(x - px, z - pz) <= render) {
                    out.add(new ClaimBorderGeometry.Point(x, z));
                }
            }
        }
        if (out.size() > cfg.maxPoints()) {
            out.sort((a, b) -> Double.compare(
                    dist2(a, px, pz), dist2(b, px, pz)));
            return new ArrayList<>(out.subList(0, cfg.maxPoints()));
        }
        return dedupe(out);
    }

    private static double dist2(ClaimBorderGeometry.Point p, double px, double pz) {
        double dx = p.x() - px, dz = p.z() - pz;
        return dx * dx + dz * dz;
    }

    private static List<ClaimBorderGeometry.Point> dedupe(List<ClaimBorderGeometry.Point> in) {
        List<ClaimBorderGeometry.Point> out = new ArrayList<>(in.size());
        for (ClaimBorderGeometry.Point p : in) {
            boolean dup = false;
            for (ClaimBorderGeometry.Point q : out) {
                if (Math.abs(p.x() - q.x()) < 1e-9 && Math.abs(p.z() - q.z()) < 1e-9) {
                    dup = true; break;
                }
            }
            if (!dup) out.add(p);
        }
        return out;
    }
}
