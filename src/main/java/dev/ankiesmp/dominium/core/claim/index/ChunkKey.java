package dev.ankiesmp.dominium.core.claim.index;

/**
 * Utility voor het packen van een Minecraft-chunk (cx, cz) in één long.
 * Wordt <b>uitsluitend</b> gebruikt als spatial-indexbucket — een chunk
 * is nooit de eigendoms- of rekeneenheid van een claim (§6.3).
 */
public final class ChunkKey {

    public static final int CHUNK_SHIFT = 4;
    public static final int CHUNK_SIZE  = 1 << CHUNK_SHIFT;

    private ChunkKey() {}

    public static int toChunk(int block) {
        return block >> CHUNK_SHIFT;
    }

    public static long pack(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    public static int unpackX(long key) { return (int) (key >> 32); }
    public static int unpackZ(long key) { return (int) key; }
}
