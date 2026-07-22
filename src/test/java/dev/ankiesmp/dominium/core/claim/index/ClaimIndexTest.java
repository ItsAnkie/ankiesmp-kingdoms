package dev.ankiesmp.dominium.core.claim.index;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.common.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimIndexTest {

    private ClaimIndex index;
    private WorldRef world;

    @BeforeEach
    void setUp() {
        index = new ClaimIndex();
        world = new WorldRef(UUID.randomUUID());
    }

    @Test
    void containingHitsSingleClaim() {
        Claim c = claim(0, 0, 15, 15);
        index.add(c);
        assertEquals(c, index.containing(world, 5, 5).orElseThrow());
        assertTrue(index.containing(world, 100, 100).isEmpty());
    }

    @Test
    void overlappingUsesBucketSet() {
        Claim a = claim(0, 0, 15, 15);
        Claim b = claim(20, 20, 40, 40);
        index.add(a); index.add(b);
        assertEquals(1, index.overlapping(world, new ClaimRectangle(10, 10, 12, 12)).size());
        assertEquals(2, index.overlapping(world, new ClaimRectangle(10, 10, 25, 25)).size());
    }

    @Test
    void replaceReindexesGeometry() {
        Claim c = claim(0, 0, 15, 15);
        index.add(c);
        Claim resized = index.replace(c.id(), new ClaimRectangle(0, 0, 31, 31));
        assertTrue(index.containing(world, 30, 30).isPresent());
        assertEquals(resized.id(), c.id());
    }

    @Test
    void removeDropsAllBuckets() {
        Claim c = claim(0, 0, 63, 63); // spans several chunks
        index.add(c);
        index.remove(c.id());
        assertTrue(index.containing(world, 10, 10).isEmpty());
        assertTrue(index.get(c.id()).isEmpty());
    }

    private Claim claim(int minX, int minZ, int maxX, int maxZ) {
        return new Claim(UUID.randomUUID(), world,
                new ClaimRectangle(minX, minZ, maxX, maxZ),
                ClaimOwner.personal(UUID.randomUUID()),
                Instant.now());
    }
}
