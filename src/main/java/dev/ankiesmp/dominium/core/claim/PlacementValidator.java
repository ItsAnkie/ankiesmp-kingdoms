package dev.ankiesmp.dominium.core.claim;

import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Valideert of een gegeven rechthoek onder de geldende
 * {@link PlacementRules} als nieuwe claim kan worden aangemaakt of dat
 * een bestaande claim ernaar kan worden geresized. Levert eventueel de
 * benodigde ledger-delta.
 */
public final class PlacementValidator {

    private final ClaimIndex index;
    private final PlacementRules rules;

    public PlacementValidator(ClaimIndex index, PlacementRules rules) {
        this.index = Objects.requireNonNull(index, "index");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public PlacementResult validateCreate(WorldRef world, ClaimRectangle rect, long availableClaimBlocks) {
        PlacementResult sanity = validateSize(rect);
        if (sanity != null) return sanity;

        List<Claim> overlaps = new ArrayList<>(index.overlapping(world, rect));
        if (!overlaps.isEmpty()) return PlacementResult.overlap(overlaps);

        if (rules.minBufferChebyshev() > 0) {
            List<Claim> nearby = collectBufferViolations(world, rect, null);
            if (!nearby.isEmpty()) return PlacementResult.bufferViolation(nearby);
        }

        long required = rect.cost();
        if (availableClaimBlocks < required) {
            return PlacementResult.insufficientClaimBlocks(required, availableClaimBlocks);
        }
        return PlacementResult.ok(required);
    }

    public PlacementResult validateResize(UUID existingId, ClaimRectangle newRect, long availableClaimBlocks) {
        Claim existing = index.get(existingId)
                .orElseThrow(() -> new IllegalArgumentException("unknown claim " + existingId));

        PlacementResult sanity = validateSize(newRect);
        if (sanity != null) return sanity;

        List<Claim> overlaps = new ArrayList<>();
        for (Claim c : index.overlapping(existing.world(), newRect)) {
            if (!c.id().equals(existingId)) overlaps.add(c);
        }
        if (!overlaps.isEmpty()) return PlacementResult.overlap(overlaps);

        if (rules.minBufferChebyshev() > 0) {
            List<Claim> nearby = collectBufferViolations(existing.world(), newRect, existingId);
            if (!nearby.isEmpty()) return PlacementResult.bufferViolation(nearby);
        }

        long delta = existing.rect().resizeCostDelta(newRect);
        if (delta > 0 && availableClaimBlocks < delta) {
            return PlacementResult.insufficientClaimBlocks(delta, availableClaimBlocks);
        }
        return PlacementResult.ok(delta);
    }

    private PlacementResult validateSize(ClaimRectangle rect) {
        if (rect.width() < rules.minSideLength() || rect.depth() < rules.minSideLength()) {
            return PlacementResult.tooSmallSide(
                    "min side length is " + rules.minSideLength()
                            + " but got " + rect.width() + "x" + rect.depth());
        }
        if (rect.area() < rules.minArea()) {
            return PlacementResult.tooSmallArea(
                    "min area is " + rules.minArea() + " but got " + rect.area());
        }
        return null;
    }

    private List<Claim> collectBufferViolations(WorldRef world, ClaimRectangle rect, UUID excludeId) {
        int buf = rules.minBufferChebyshev();
        ClaimRectangle expanded = new ClaimRectangle(
                rect.minX() - buf, rect.minZ() - buf,
                rect.maxX() + buf, rect.maxZ() + buf);
        List<Claim> out = new ArrayList<>();
        for (Claim c : index.overlapping(world, expanded)) {
            if (excludeId != null && c.id().equals(excludeId)) continue;
            if (!c.rect().intersects(rect) && c.rect().chebyshevGapTo(rect) < buf) {
                out.add(c);
            }
        }
        return out;
    }
}
