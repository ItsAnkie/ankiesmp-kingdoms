package dev.ankiesmp.dominium.paper.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmationStoreTest {

    private long[] now = { 0L };
    private ConfirmationStore store() {
        return new ConfirmationStore(10, () -> now[0]);
    }

    @Test
    void armThenConsumeMatchesActionAndKingdom() {
        var s = store();
        UUID actor = UUID.randomUUID(), kingdom = UUID.randomUUID();
        s.arm(actor, ConfirmationStore.Action.DISBAND, kingdom, null);
        var p = s.consume(actor, ConfirmationStore.Action.DISBAND, kingdom);
        assertTrue(p.isPresent());
        // Second consume: gone.
        assertTrue(s.consume(actor, ConfirmationStore.Action.DISBAND, kingdom).isEmpty());
    }

    @Test
    void mismatchDoesNotConsume() {
        var s = store();
        UUID actor = UUID.randomUUID(), kingdom = UUID.randomUUID();
        s.arm(actor, ConfirmationStore.Action.DISBAND, kingdom, null);
        assertTrue(s.consume(actor, ConfirmationStore.Action.TRANSFER, kingdom).isEmpty(),
                "wrong action");
        assertTrue(s.consume(actor, ConfirmationStore.Action.DISBAND, UUID.randomUUID()).isEmpty(),
                "wrong kingdom");
    }

    @Test
    void expiredConfirmationIsGone() {
        var s = store();
        UUID actor = UUID.randomUUID(), kingdom = UUID.randomUUID();
        s.arm(actor, ConfirmationStore.Action.DISBAND, kingdom, null);
        now[0] += 20_000; // > 10s ttl
        assertTrue(s.consume(actor, ConfirmationStore.Action.DISBAND, kingdom).isEmpty());
    }

    @Test
    void clearDropsPending() {
        var s = store();
        UUID actor = UUID.randomUUID();
        s.arm(actor, ConfirmationStore.Action.DISBAND, UUID.randomUUID(), null);
        s.clear(actor);
        assertTrue(s.consume(actor, ConfirmationStore.Action.DISBAND, UUID.randomUUID()).isEmpty());
    }
}
