package dev.ankiesmp.dominium.core.claim;

import java.util.Objects;

/**
 * Pure regels voor het uitbreiden van een bestaande claim met een nieuwe
 * selectie. Sinds de "één claim per owner"-invariant wordt een tweede
 * selectie behandeld als resize wanneer union rechthoekig is.
 *
 * <p>Regels:
 * <ul>
 *   <li>selectie volledig binnen bestaande claim → NO_OP;</li>
 *   <li>selectie los van bestaande claim (met gap) → REJECT_DETACHED;</li>
 *   <li>selectie deelt alleen een hoek → REJECT_CORNER_ONLY;</li>
 *   <li>selectie grenst aan of overlapt, maar bounding-box zou een
 *       L-vorm dekken → REJECT_NOT_RECTANGULAR;</li>
 *   <li>selectie grenst aan of overlapt én de bounding-box is exact
 *       {@code existing ∪ selection} als rechthoek → OK met {@code newRect}
 *       = bounding-box.</li>
 * </ul>
 */
public final class ClaimExpansion {

    private ClaimExpansion() {}

    public enum Kind { OK, NO_OP, REJECT_DETACHED, REJECT_CORNER_ONLY, REJECT_NOT_RECTANGULAR }

    public record Result(Kind kind, ClaimRectangle newRect, String message) {
        public boolean isOk() { return kind == Kind.OK; }
    }

    public static Result plan(ClaimRectangle existing, ClaimRectangle selection) {
        Objects.requireNonNull(existing);
        Objects.requireNonNull(selection);

        if (contains(existing, selection)) {
            return new Result(Kind.NO_OP, existing,
                    "Selection lies entirely inside your existing claim.");
        }

        // Bounding-box van bestaande + selectie.
        ClaimRectangle bbox = ClaimRectangle.ofCorners(
                Math.min(existing.minX(), selection.minX()),
                Math.min(existing.minZ(), selection.minZ()),
                Math.max(existing.maxX(), selection.maxX()),
                Math.max(existing.maxZ(), selection.maxZ()));

        // Adjacency-check: overlap of gedeeld edge?
        boolean overlaps = existing.intersects(selection);
        boolean sharesEdge = existing.sharesEdge(selection);
        if (!overlaps && !sharesEdge) {
            // Chebyshev-gap 0 kan ook betekenen: alleen hoekcontact.
            long gap = existing.chebyshevGapTo(selection);
            if (gap == 0) {
                return new Result(Kind.REJECT_CORNER_ONLY, null,
                        "Selection only touches a corner; must share a full edge.");
            }
            return new Result(Kind.REJECT_DETACHED, null,
                    "Selection is not adjacent to your existing claim.");
        }

        // Bounding box moet exact bestaande + selectie dekken (geen L-vorm).
        long bboxArea = bbox.cost();
        long unionArea = existing.cost() + selection.cost()
                - overlapArea(existing, selection);
        if (bboxArea != unionArea) {
            return new Result(Kind.REJECT_NOT_RECTANGULAR, null,
                    "The result would not be a rectangle; expand along one full edge.");
        }
        return new Result(Kind.OK, bbox, null);
    }

    private static boolean contains(ClaimRectangle outer, ClaimRectangle inner) {
        return inner.minX() >= outer.minX() && inner.maxX() <= outer.maxX()
                && inner.minZ() >= outer.minZ() && inner.maxZ() <= outer.maxZ();
    }

    private static long overlapArea(ClaimRectangle a, ClaimRectangle b) {
        int ox = Math.max(0, Math.min(a.maxX(), b.maxX()) - Math.max(a.minX(), b.minX()) + 1);
        int oz = Math.max(0, Math.min(a.maxZ(), b.maxZ()) - Math.max(a.minZ(), b.minZ()) + 1);
        return (long) ox * oz;
    }
}
