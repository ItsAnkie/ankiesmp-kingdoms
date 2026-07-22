package dev.ankiesmp.dominium.core.bank;

/**
 * Utility voor minor-unit arithmetic. Alle interne bank-berekeningen
 * gebruiken {@code long} minor units (bijv. eurocenten). Alleen de
 * Vault-adapter converteert naar {@code double}.
 */
public final class Money {

    public static final long MINOR_UNITS_PER_MAJOR = 100L;

    private Money() {}

    public static long majorToMinor(double major) {
        if (Double.isNaN(major) || Double.isInfinite(major)) {
            throw new IllegalArgumentException("amount is NaN/Infinite");
        }
        if (major < 0) throw new IllegalArgumentException("amount must be > 0");
        // Bewuste rounding half-up.
        double scaled = Math.round(major * MINOR_UNITS_PER_MAJOR);
        if (scaled > Long.MAX_VALUE || scaled < 0) {
            throw new IllegalArgumentException("amount overflow");
        }
        return (long) scaled;
    }

    public static double minorToMajor(long minor) {
        return minor / (double) MINOR_UNITS_PER_MAJOR;
    }

    public static long requirePositive(long minor) {
        if (minor <= 0) throw new IllegalArgumentException("amount must be > 0");
        return minor;
    }

    public static long addExact(long a, long b) {
        return Math.addExact(a, b);
    }
}
