package dev.ankiesmp.dominium.core.ledger;

import dev.ankiesmp.dominium.core.common.HolderKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bewaakt de exacte output-tekst van {@code /claim blocks}. Als deze
 * regels veranderen moet de test bewust worden bijgewerkt, en de
 * documentatie in {@code docs/TEST_PLAN.md} ook.
 */
class ClaimBlocksReadoutTest {

    // CBR-001
    @Test
    void showsAllThreeCountersFromBalanceSnapshot() {
        var snapshot = new BalanceSnapshot(
                HolderKey.player(UUID.randomUUID()),
                750L, 1000L, 250L, Instant.EPOCH);

        List<String> lines = ClaimBlocksReadout.linesFor(snapshot);

        assertEquals(4, lines.size());
        assertEquals("Claim block ledger", lines.get(0));
        assertEquals("  Available:    750", lines.get(1));
        assertEquals("  Total earned: 1000", lines.get(2));
        assertEquals("  Total spent:  250", lines.get(3));
    }

    // CBR-002 — zero-state (verse speler zonder grant nog).
    @Test
    void zeroBalanceRendersZeros() {
        var snapshot = new BalanceSnapshot(
                HolderKey.player(UUID.randomUUID()),
                0L, 0L, 0L, Instant.EPOCH);

        List<String> lines = ClaimBlocksReadout.linesFor(snapshot);
        assertEquals("  Available:    0", lines.get(1));
        assertEquals("  Total earned: 0", lines.get(2));
        assertEquals("  Total spent:  0", lines.get(3));
    }

    // CBR-003
    @Test
    void nullSnapshotRejected() {
        assertThrows(NullPointerException.class, () -> ClaimBlocksReadout.linesFor(null));
    }

    // CBR-004 — earning-uitbreiding toont today en cap-remaining.
    @Test
    void withEarningEnabledShowsExtraLines() {
        var snapshot = new BalanceSnapshot(
                dev.ankiesmp.dominium.core.common.HolderKey.player(UUID.randomUUID()),
                800L, 1000L, 200L, Instant.EPOCH);
        List<String> lines = ClaimBlocksReadout.linesFor(snapshot, 40L, 460L, true);
        assertEquals(6, lines.size());
        assertEquals("  Today via active play: 40", lines.get(4));
        assertEquals("  Daily cap remaining:   460", lines.get(5));
    }
}
