package dev.ankiesmp.dominium.core.claim;

import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlacementValidatorTest {

    private ClaimIndex index;
    private PlacementValidator validator;
    private WorldRef world;

    @BeforeEach
    void setUp() {
        index = new ClaimIndex();
        validator = new PlacementValidator(index, new PlacementRules(10, 100, 2));
        world = new WorldRef(UUID.randomUUID());
    }

    @Test
    void rejectsTooSmallSide() {
        PlacementResult r = validator.validateCreate(world,
                new ClaimRectangle(0, 0, 8, 20), 10_000);
        assertEquals(PlacementResult.Kind.TOO_SMALL_SIDE, r.kind());
    }

    @Test
    void rejectsOverlap() {
        Claim existing = new Claim(UUID.randomUUID(), world,
                new ClaimRectangle(0, 0, 20, 20),
                ClaimOwner.personal(UUID.randomUUID()), Instant.now());
        index.add(existing);
        PlacementResult r = validator.validateCreate(world,
                new ClaimRectangle(10, 10, 30, 30), 10_000);
        assertEquals(PlacementResult.Kind.OVERLAP, r.kind());
    }

    @Test
    void rejectsBufferViolation() {
        Claim existing = new Claim(UUID.randomUUID(), world,
                new ClaimRectangle(0, 0, 20, 20),
                ClaimOwner.personal(UUID.randomUUID()), Instant.now());
        index.add(existing);
        // gap of exactly 1 block, below buffer of 2
        PlacementResult r = validator.validateCreate(world,
                new ClaimRectangle(22, 0, 40, 20), 10_000);
        assertEquals(PlacementResult.Kind.BUFFER_VIOLATION, r.kind());
    }

    @Test
    void rejectsInsufficientClaimBlocks() {
        PlacementResult r = validator.validateCreate(world,
                new ClaimRectangle(0, 0, 19, 19), 50);
        assertEquals(PlacementResult.Kind.INSUFFICIENT_CLAIM_BLOCKS, r.kind());
        assertEquals(400L, r.claimBlockDelta());
    }

    @Test
    void acceptsValidCreate() {
        PlacementResult r = validator.validateCreate(world,
                new ClaimRectangle(0, 0, 19, 19), 10_000);
        assertTrue(r.isOk());
        assertEquals(400L, r.claimBlockDelta());
    }

    @Test
    void resizeIgnoresOwnClaimOverlap() {
        Claim existing = new Claim(UUID.randomUUID(), world,
                new ClaimRectangle(0, 0, 20, 20),
                ClaimOwner.personal(UUID.randomUUID()), Instant.now());
        index.add(existing);
        PlacementResult r = validator.validateResize(existing.id(),
                new ClaimRectangle(0, 0, 29, 20), 10_000);
        assertTrue(r.isOk(), () -> "expected OK but got " + r.kind());
        assertEquals(189L, r.claimBlockDelta()); // 30*21 - 21*21 = 630 - 441 = 189
    }
}
