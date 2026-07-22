package dev.ankiesmp.dominium.core.claim;

import java.util.List;
import java.util.Objects;

/**
 * Uitkomst van een placement-validatie voor create of resize.
 */
public final class PlacementResult {

    public enum Kind {
        OK,
        TOO_SMALL_SIDE,
        TOO_SMALL_AREA,
        OVERLAP,
        BUFFER_VIOLATION,
        INSUFFICIENT_CLAIM_BLOCKS,
        INVALID_GEOMETRY,
        DUPLICATE_OWNER,
        BLOCKED
    }

    private final Kind kind;
    private final String message;
    private final List<Claim> conflicts;
    private final long claimBlockDelta;

    private PlacementResult(Kind kind, String message, List<Claim> conflicts, long claimBlockDelta) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.message = message;
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        this.claimBlockDelta = claimBlockDelta;
    }

    public static PlacementResult ok(long claimBlockDelta) {
        return new PlacementResult(Kind.OK, null, null, claimBlockDelta);
    }

    public static PlacementResult tooSmallSide(String message) {
        return new PlacementResult(Kind.TOO_SMALL_SIDE, message, null, 0);
    }

    public static PlacementResult tooSmallArea(String message) {
        return new PlacementResult(Kind.TOO_SMALL_AREA, message, null, 0);
    }

    public static PlacementResult overlap(List<Claim> conflicts) {
        return new PlacementResult(Kind.OVERLAP, "overlap with existing claim(s)", conflicts, 0);
    }

    public static PlacementResult bufferViolation(List<Claim> nearby) {
        return new PlacementResult(Kind.BUFFER_VIOLATION, "too close to existing claim(s)", nearby, 0);
    }

    public static PlacementResult insufficientClaimBlocks(long required, long available) {
        return new PlacementResult(Kind.INSUFFICIENT_CLAIM_BLOCKS,
                "need " + required + " blocks, have " + available, null, required);
    }

    public static PlacementResult invalidGeometry(String message) {
        return new PlacementResult(Kind.INVALID_GEOMETRY, message, null, 0);
    }

    public static PlacementResult duplicateOwner() {
        return new PlacementResult(Kind.DUPLICATE_OWNER,
                "You already own a claim. Expand your existing claim instead.", null, 0);
    }

    public static PlacementResult blocked(String message) {
        return new PlacementResult(Kind.BLOCKED, message, null, 0);
    }

    public Kind kind() { return kind; }
    public String message() { return message; }
    public List<Claim> conflicts() { return conflicts; }
    public long claimBlockDelta() { return claimBlockDelta; }
    public boolean isOk() { return kind == Kind.OK; }
}
