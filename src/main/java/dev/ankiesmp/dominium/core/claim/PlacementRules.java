package dev.ankiesmp.dominium.core.claim;

import java.util.Objects;

/**
 * Vaste, configureerbare placement-regels voor top-level claims.
 * Kingdom-specifieke adjacency- en outpost-regels komen in fase 4/5.
 */
public final class PlacementRules {

    private final int minSideLength;
    private final long minArea;
    private final int minBufferChebyshev;

    public PlacementRules(int minSideLength, long minArea, int minBufferChebyshev) {
        if (minSideLength <= 0) throw new IllegalArgumentException("minSideLength must be positive");
        if (minArea <= 0) throw new IllegalArgumentException("minArea must be positive");
        if (minBufferChebyshev < 0) throw new IllegalArgumentException("minBufferChebyshev must be >= 0");
        this.minSideLength = minSideLength;
        this.minArea = minArea;
        this.minBufferChebyshev = minBufferChebyshev;
    }

    public static PlacementRules defaults() {
        return new PlacementRules(10, 100L, 0);
    }

    public int minSideLength() { return minSideLength; }
    public long minArea() { return minArea; }
    public int minBufferChebyshev() { return minBufferChebyshev; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlacementRules p)) return false;
        return minSideLength == p.minSideLength && minArea == p.minArea
                && minBufferChebyshev == p.minBufferChebyshev;
    }

    @Override
    public int hashCode() { return Objects.hash(minSideLength, minArea, minBufferChebyshev); }
}
