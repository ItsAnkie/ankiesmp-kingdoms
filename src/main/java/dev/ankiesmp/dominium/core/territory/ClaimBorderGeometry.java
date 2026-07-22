package dev.ankiesmp.dominium.core.territory;

import dev.ankiesmp.dominium.core.claim.ClaimRectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure geometrie voor de claim-border-particles. Werkt uitsluitend met
 * X/Z-int-coördinaten en doubles — geen Bukkit-types.
 *
 * <p>Genereert punten langs de vier zijden van een rechthoek, met een
 * configureerbare {@code spacing}. Alleen segmenten binnen
 * {@code renderDistance} van de speler tellen mee; wanneer een zijde geheel
 * te ver is, wordt hij overgeslagen. Punten worden gededupliceerd op
 * hoekcoördinaten. Het totaalaantal punten wordt begrensd door
 * {@code maxPoints}.
 */
public final class ClaimBorderGeometry {

    private ClaimBorderGeometry() {}

    public record Point(double x, double z) {}

    public record Config(double triggerDistance, double renderDistance,
                         double spacing, int maxPoints) {
        public Config {
            if (triggerDistance <= 0 || renderDistance <= 0
                    || spacing <= 0 || maxPoints <= 0) {
                throw new IllegalArgumentException("border geometry values must be positive");
            }
            if (renderDistance < triggerDistance) {
                throw new IllegalArgumentException(
                        "renderDistance must be >= triggerDistance");
            }
        }
    }

    /**
     * @return afstand van {@code (px, pz)} tot de rand van {@code rect}.
     *         Negatief binnen de claim (verder van de rand → grotere absolute
     *         waarde), 0 op de rand, positief buiten.
     */
    public static double distanceToBorder(ClaimRectangle rect, double px, double pz) {
        double minX = rect.minX();
        double maxX = rect.maxX() + 1.0;
        double minZ = rect.minZ();
        double maxZ = rect.maxZ() + 1.0;
        boolean inside = px >= minX && px <= maxX && pz >= minZ && pz <= maxZ;
        if (inside) {
            double dx = Math.min(px - minX, maxX - px);
            double dz = Math.min(pz - minZ, maxZ - pz);
            return -Math.min(dx, dz);
        }
        double cx = Math.max(minX, Math.min(maxX, px));
        double cz = Math.max(minZ, Math.min(maxZ, pz));
        double dx = px - cx;
        double dz = pz - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** {@code true} als de speler binnen {@code triggerDistance} van de rand ligt. */
    public static boolean inTriggerRange(ClaimRectangle rect,
                                         double px, double pz,
                                         double triggerDistance) {
        return Math.abs(distanceToBorder(rect, px, pz)) <= triggerDistance;
    }

    /**
     * Genereert alleen de <b>lokale</b> perimeter: de dichtstbijzijnde
     * zijde (of, in de buurt van een hoek, hoogstens twee zijdes).
     * Punten verder dan {@code renderDistance} van de speler worden
     * weggelaten; totaal beperkt tot {@code maxPoints}. Nooit de volledige
     * perimeter van een grote claim.
     */
    public static List<Point> localPointsNearPlayer(ClaimRectangle rect,
                                                    double px, double pz,
                                                    Config cfg) {
        Objects.requireNonNull(rect);
        Objects.requireNonNull(cfg);
        double minX = rect.minX();
        double maxX = rect.maxX() + 1.0;
        double minZ = rect.minZ();
        double maxZ = rect.maxZ() + 1.0;

        // Zijde-afstanden (loodrecht, unclamped): 0=south (Z=minZ),
        // 1=north (Z=maxZ), 2=west (X=minX), 3=east (X=maxX).
        double[] dists = {
                Math.abs(pz - minZ), Math.abs(pz - maxZ),
                Math.abs(px - minX), Math.abs(px - maxX)
        };
        int nearest = 0;
        for (int i = 1; i < 4; i++) if (dists[i] < dists[nearest]) nearest = i;

        // Tweede zijde alleen als hij loodrecht óók binnen renderDistance ligt
        // (typisch: hoek-scenario). Anders tekenen we één zijde.
        int second = -1;
        double bestSecond = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            if (i == nearest) continue;
            if (dists[i] < bestSecond) { bestSecond = dists[i]; second = i; }
        }
        boolean includeSecond = second >= 0 && bestSecond <= cfg.renderDistance();

        List<Point> out = new ArrayList<>();
        drawSide(out, nearest, minX, maxX, minZ, maxZ, px, pz, cfg);
        if (includeSecond) drawSide(out, second, minX, maxX, minZ, maxZ, px, pz, cfg);

        if (out.size() > cfg.maxPoints()) {
            out.sort((a, b) -> Double.compare(dist2(a, px, pz), dist2(b, px, pz)));
            return new ArrayList<>(out.subList(0, cfg.maxPoints()));
        }
        return dedupe(out);
    }

    private static void drawSide(List<Point> out, int sideIdx,
                                 double minX, double maxX, double minZ, double maxZ,
                                 double px, double pz, Config cfg) {
        switch (sideIdx) {
            case 0 -> addHorizontalSide(out, minX, maxX, minZ, px, pz, cfg);
            case 1 -> addHorizontalSide(out, minX, maxX, maxZ, px, pz, cfg);
            case 2 -> addVerticalSide  (out, minZ, maxZ, minX, px, pz, cfg);
            case 3 -> addVerticalSide  (out, minZ, maxZ, maxX, px, pz, cfg);
            default -> { /* niets */ }
        }
    }

    private static List<Point> dedupe(List<Point> in) {
        List<Point> out = new ArrayList<>(in.size());
        for (Point p : in) {
            boolean dup = false;
            for (Point q : out) {
                if (Math.abs(p.x() - q.x()) < 1e-9 && Math.abs(p.z() - q.z()) < 1e-9) {
                    dup = true; break;
                }
            }
            if (!dup) out.add(p);
        }
        return out;
    }

    /**
     * Legacy: full-perimeter render. Bewaard voor bestaande tests; de
     * productie-task gebruikt nu {@link #localPointsNearPlayer}.
     */
    public static List<Point> pointsNearPlayer(ClaimRectangle rect,
                                               double px, double pz,
                                               Config cfg) {
        Objects.requireNonNull(rect);
        Objects.requireNonNull(cfg);
        double minX = rect.minX();
        double maxX = rect.maxX() + 1.0;
        double minZ = rect.minZ();
        double maxZ = rect.maxZ() + 1.0;
        List<Point> out = new ArrayList<>();

        // Twee horizontale (Z = minZ, Z = maxZ) en twee verticale (X = minX, X = maxX).
        addHorizontalSide(out, minX, maxX, minZ, px, pz, cfg);
        addHorizontalSide(out, minX, maxX, maxZ, px, pz, cfg);
        addVerticalSide  (out, minZ, maxZ, minX, px, pz, cfg);
        addVerticalSide  (out, minZ, maxZ, maxX, px, pz, cfg);

        if (out.size() > cfg.maxPoints()) {
            // Sorteer op afstand tot speler en behoud alleen de dichtstbijzijnde N.
            out.sort((a, b) -> Double.compare(
                    dist2(a, px, pz), dist2(b, px, pz)));
            return new ArrayList<>(out.subList(0, cfg.maxPoints()));
        }
        return out;
    }

    private static void addHorizontalSide(List<Point> out,
                                          double x0, double x1, double zFixed,
                                          double px, double pz, Config cfg) {
        double from = Math.max(x0, px - cfg.renderDistance());
        double to   = Math.min(x1, px + cfg.renderDistance());
        if (from > to) return;
        // Begin op de eerste sample die >= from is, in het originele grid.
        double first = x0 + Math.ceil((from - x0) / cfg.spacing()) * cfg.spacing();
        for (double x = first; x <= to + 1e-9; x += cfg.spacing()) {
            double clamped = Math.min(x1, Math.max(x0, x));
            double d = Math.hypot(clamped - px, zFixed - pz);
            if (d <= cfg.renderDistance()) out.add(new Point(clamped, zFixed));
        }
        // Zorg dat de eindpunten (hoeken) verschijnen als ze binnen bereik zijn.
        maybeAddCorner(out, x0, zFixed, px, pz, cfg);
        maybeAddCorner(out, x1, zFixed, px, pz, cfg);
    }

    private static void addVerticalSide(List<Point> out,
                                        double z0, double z1, double xFixed,
                                        double px, double pz, Config cfg) {
        double from = Math.max(z0, pz - cfg.renderDistance());
        double to   = Math.min(z1, pz + cfg.renderDistance());
        if (from > to) return;
        // Skip de hoeken hier — die zijn al door de horizontal-side toegevoegd.
        double first = z0 + Math.ceil((from - z0) / cfg.spacing()) * cfg.spacing();
        for (double z = first; z <= to + 1e-9; z += cfg.spacing()) {
            if (Math.abs(z - z0) < 1e-9 || Math.abs(z - z1) < 1e-9) continue;
            double clamped = Math.min(z1, Math.max(z0, z));
            double d = Math.hypot(xFixed - px, clamped - pz);
            if (d <= cfg.renderDistance()) out.add(new Point(xFixed, clamped));
        }
    }

    private static void maybeAddCorner(List<Point> out,
                                       double x, double z,
                                       double px, double pz, Config cfg) {
        double d = Math.hypot(x - px, z - pz);
        if (d > cfg.renderDistance()) return;
        for (Point p : out) {
            if (Math.abs(p.x() - x) < 1e-9 && Math.abs(p.z() - z) < 1e-9) return;
        }
        out.add(new Point(x, z));
    }

    private static double dist2(Point p, double px, double pz) {
        double dx = p.x() - px, dz = p.z() - pz;
        return dx * dx + dz * dz;
    }
}
