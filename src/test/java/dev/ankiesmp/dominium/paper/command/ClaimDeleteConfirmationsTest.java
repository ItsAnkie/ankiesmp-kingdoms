package dev.ankiesmp.dominium.paper.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimDeleteConfirmationsTest {

    private long[] now = { 0L };
    private ClaimDeleteConfirmations store() {
        return new ClaimDeleteConfirmations(30L, () -> now[0]);
    }

    @Test
    void armThenConsumeMatchesActorAndClaim() {
        var s = store();
        String actor = "admin";
        UUID cid = UUID.randomUUID();
        s.arm(actor, cid);
        assertTrue(s.consume(actor, cid));
        assertFalse(s.consume(actor, cid), "één keer per arm");
    }

    @Test
    void mismatchDoesNotConsume() {
        var s = store();
        String actor = "admin";
        UUID cid = UUID.randomUUID();
        s.arm(actor, cid);
        assertFalse(s.consume(actor, UUID.randomUUID()));
        // Pending blijft, dus correcte confirm werkt nog.
        assertTrue(s.consume(actor, cid));
    }

    @Test
    void expiryDropsPending() {
        var s = store();
        String actor = "admin";
        UUID cid = UUID.randomUUID();
        s.arm(actor, cid);
        now[0] += 40_000; // > 30s
        assertFalse(s.consume(actor, cid));
    }
}
