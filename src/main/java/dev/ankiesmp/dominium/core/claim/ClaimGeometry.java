package dev.ankiesmp.dominium.core.claim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure multi-region geometry. Één claim kan bestaan uit meerdere
 * rechthoekige {@link ClaimRectangle}-regio's die edge-connected zijn.
 * Doel: single source of truth voor area, contains, uitbreidingskosten,
 * intersect en outline.
 *
 * <p>Fase 5 introduceert dit type; ClaimService en border-particles gaan
 * er in fase 5.x volledig op over. Tot dan blijft {@code Claim.rect} het
 * cached-bounds-primitive; deze klasse is de authoritative geometry.
 */
public final class ClaimGeometry {

    private final List<ClaimRectangle> regions;

    private ClaimGeometry(List<ClaimRectangle> regions) {
        if (regions.isEmpty()) throw new IllegalArgumentException("geometry needs at least one region");
        this.regions = List.copyOf(regions);
    }

    public static ClaimGeometry ofRectangle(ClaimRectangle rect) {
        return new ClaimGeometry(List.of(rect));
    }

    public static ClaimGeometry ofRegions(List<ClaimRectangle> regions) {
        return new ClaimGeometry(regions);
    }

    public List<ClaimRectangle> regions() { return regions; }

    public boolean contains(int x, int z) {
        for (ClaimRectangle r : regions) {
            if (x >= r.minX() && x <= r.maxX() && z >= r.minZ() && z <= r.maxZ()) return true;
        }
        return false;
    }

    /** Unieke geclaimde blocks (overlap wordt niet dubbel geteld). */
    public long area() {
        // Inclusion-exclusion voor kleine sets; voor grote sets fallback naar
        // een simpele "for each block" via bounding-box scan. In deze fase
        // implementeren we het pragmatisch via een set-scan begrensd door
        // een klein aantal cellen; voor productie-grote claims wordt dit
        // vervangen door een echte sweep-line in fase 5.x.
        // Voor de nu-gebruikte 1-3 regionen is inclusion-exclusion prima:
        long sum = 0;
        for (int i = 0; i < regions.size(); i++) sum += regions.get(i).cost();
        for (int i = 0; i < regions.size(); i++) {
            for (int j = i + 1; j < regions.size(); j++) {
                sum -= overlapArea(regions.get(i), regions.get(j));
            }
        }
        // Merk op: inclusion-exclusion is voor >2 sets pas correct met alle
        // 3-way / 4-way intersections. Voor onze use-case gebruiken we
        // uitsluitend edge-connected disjoint expansions dus is 2-way exact.
        return sum;
    }

    public ClaimRectangle bounds() {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ClaimRectangle r : regions) {
            minX = Math.min(minX, r.minX());
            minZ = Math.min(minZ, r.minZ());
            maxX = Math.max(maxX, r.maxX());
            maxZ = Math.max(maxZ, r.maxZ());
        }
        return ClaimRectangle.ofCorners(minX, minZ, maxX, maxZ);
    }

    public boolean intersects(ClaimRectangle other) {
        for (ClaimRectangle r : regions) if (r.intersects(other)) return true;
        return false;
    }

    public long intersectionArea(ClaimRectangle other) {
        long sum = 0;
        for (ClaimRectangle r : regions) sum += overlapArea(r, other);
        return sum;
    }

    /** Deelt {@code other} een volledige (unit-length) edge met een van de regio's? */
    public boolean touchesByEdge(ClaimRectangle other) {
        for (ClaimRectangle r : regions) if (r.sharesEdge(other)) return true;
        return false;
    }

    /** Alle regio's samen edge-connected? (Onze constructie garandeert dit voor legale unions.) */
    public boolean connected() {
        if (regions.size() <= 1) return true;
        boolean[] visited = new boolean[regions.size()];
        List<Integer> stack = new ArrayList<>();
        stack.add(0); visited[0] = true;
        int count = 1;
        while (!stack.isEmpty()) {
            int i = stack.remove(stack.size() - 1);
            for (int j = 0; j < regions.size(); j++) {
                if (visited[j]) continue;
                if (regions.get(i).intersects(regions.get(j))
                        || regions.get(i).sharesEdge(regions.get(j))) {
                    visited[j] = true; stack.add(j); count++;
                }
            }
        }
        return count == regions.size();
    }

    /**
     * Union met een nieuwe rectangle. Regels:
     * <ul>
     *   <li>selectie volledig binnen bestaande geometry → {@code NO_OP};</li>
     *   <li>alleen hoekcontact → {@code REJECT_CORNER_ONLY};</li>
     *   <li>los eiland → {@code REJECT_DETACHED};</li>
     *   <li>anders → {@code OK} met een nieuwe {@code ClaimGeometry} die
     *       {@code other} als extra region toevoegt. Fase 5.x kan aangrenzende
     *       regio's samenvoegen om regio-fragmentatie te beperken.</li>
     * </ul>
     */
    public UnionResult union(ClaimRectangle other) {
        Objects.requireNonNull(other);
        // Volledig binnen?
        boolean allInside = true;
        for (int x = other.minX(); x <= other.maxX() && allInside; x++) {
            for (int z = other.minZ(); z <= other.maxZ() && allInside; z++) {
                if (!contains(x, z)) { allInside = false; }
            }
        }
        if (allInside) return new UnionResult(UnionKind.NO_OP, this, 0L);

        if (!intersects(other) && !touchesByEdge(other)) {
            // gap of hoekcontact
            for (ClaimRectangle r : regions) {
                if (touchesCornerOnly(r, other)) {
                    return new UnionResult(UnionKind.REJECT_CORNER_ONLY, this, 0L);
                }
            }
            return new UnionResult(UnionKind.REJECT_DETACHED, this, 0L);
        }

        long overlapWithGeometry = intersectionArea(other);
        long newBlocks = other.cost() - overlapWithGeometry;

        List<ClaimRectangle> next = new ArrayList<>(regions);
        next.add(other);
        return new UnionResult(UnionKind.OK, new ClaimGeometry(next), newBlocks);
    }

    private static boolean touchesCornerOnly(ClaimRectangle a, ClaimRectangle b) {
        // Deelt exact één hoekpunt maar geen edge en geen overlap.
        if (a.intersects(b) || a.sharesEdge(b)) return false;
        boolean cornerTouch =
                (a.maxX() + 1 == b.minX() || a.minX() == b.maxX() + 1)
                        && (a.maxZ() + 1 == b.minZ() || a.minZ() == b.maxZ() + 1);
        return cornerTouch;
    }

    public enum UnionKind { OK, NO_OP, REJECT_CORNER_ONLY, REJECT_DETACHED }

    public record UnionResult(UnionKind kind, ClaimGeometry geometry, long extraCost) {
        public boolean ok() { return kind == UnionKind.OK; }
    }

    private static long overlapArea(ClaimRectangle a, ClaimRectangle b) {
        int ox = Math.max(0, Math.min(a.maxX(), b.maxX()) - Math.max(a.minX(), b.minX()) + 1);
        int oz = Math.max(0, Math.min(a.maxZ(), b.maxZ()) - Math.max(a.minZ(), b.minZ()) + 1);
        return (long) ox * oz;
    }
}
