package dev.ankiesmp.dominium.core.claim;

/**
 * Inclusieve rechthoek in Minecraft-blokcoördinaten. Vertegenwoordigt een
 * volledige X/Z-kolom over de wereldhoogte — Dominium heeft geen 3D-
 * subclaims.
 *
 * <p>Alle rekenwerk gebeurt in {@code long} om off-by-one- en overflow-
 * bugs te vermijden.
 */
public record ClaimRectangle(int minX, int minZ, int maxX, int maxZ) {

    public ClaimRectangle {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "invalid rectangle: (" + minX + "," + minZ + ") .. (" + maxX + "," + maxZ + ")");
        }
    }

    /** Bouw uit twee willekeurige hoeken (bijv. shovel A/B); ordent zelf. */
    public static ClaimRectangle ofCorners(int x1, int z1, int x2, int z2) {
        return new ClaimRectangle(
                Math.min(x1, x2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(z1, z2));
    }

    public long width()  { return (long) maxX - (long) minX + 1L; }
    public long depth()  { return (long) maxZ - (long) minZ + 1L; }
    public long area()   { return Math.multiplyExact(width(), depth()); }

    /** Kosten in claim blocks bij create; kosten = area(). */
    public long cost() { return area(); }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean intersects(ClaimRectangle o) {
        return this.minX <= o.maxX && this.maxX >= o.minX
                && this.minZ <= o.maxZ && this.maxZ >= o.minZ;
    }

    /**
     * Manhattan-achtige gap tussen twee rechthoeken op X/Z (chebyshev):
     * 0 als ze elkaar raken of overlappen, anders het aantal blokken
     * tussenruimte in de dichtstbijzijnde richting.
     */
    public int chebyshevGapTo(ClaimRectangle o) {
        int dx = 0;
        if (o.minX > this.maxX) dx = o.minX - this.maxX - 1;
        else if (o.maxX < this.minX) dx = this.minX - o.maxX - 1;
        int dz = 0;
        if (o.minZ > this.maxZ) dz = o.minZ - this.maxZ - 1;
        else if (o.maxZ < this.minZ) dz = this.minZ - o.maxZ - 1;
        return Math.max(dx, dz);
    }

    /**
     * Delen twee rechthoeken minstens één randblok? Nuttig voor
     * kingdom-adjacency-eisen (§7.2). Dit betekent: er is een rand van
     * blokken waar de X-projectie én de Z-projectie samenvallen tot
     * exact 1 blok afstand op één as en overlap op de andere.
     */
    public boolean sharesEdge(ClaimRectangle o) {
        boolean touchOnX = (this.maxX + 1 == o.minX) || (o.maxX + 1 == this.minX);
        boolean overlapZ = this.minZ <= o.maxZ && this.maxZ >= o.minZ;
        if (touchOnX && overlapZ) return true;

        boolean touchOnZ = (this.maxZ + 1 == o.minZ) || (o.maxZ + 1 == this.minZ);
        boolean overlapX = this.minX <= o.maxX && this.maxX >= o.minX;
        return touchOnZ && overlapX;
    }

    /**
     * Verschil in claim-blocks bij een resize van {@code this} naar
     * {@code target}. Positief = extra blocks nodig; negatief = refund.
     */
    public long resizeCostDelta(ClaimRectangle target) {
        return Math.subtractExact(target.area(), this.area());
    }
}
