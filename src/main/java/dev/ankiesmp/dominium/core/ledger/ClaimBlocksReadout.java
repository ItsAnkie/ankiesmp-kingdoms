package dev.ankiesmp.dominium.core.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure formatter voor {@code /claim blocks}. Testbaar zonder Bukkit/Adventure.
 */
public final class ClaimBlocksReadout {

    private ClaimBlocksReadout() {}

    public static List<String> linesFor(BalanceSnapshot snapshot) {
        return linesFor(snapshot, 0L, 0L, false);
    }

    public static List<String> linesFor(BalanceSnapshot snapshot,
                                        long earnedTodayViaActivePlay,
                                        long dailyCapRemaining,
                                        boolean earningEnabled) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<String> out = new ArrayList<>();
        out.add("Claim block ledger");
        out.add("  Available:    " + snapshot.balance());
        out.add("  Total earned: " + snapshot.totalEarned());
        out.add("  Total spent:  " + snapshot.totalSpent());
        if (earningEnabled) {
            out.add("  Today via active play: " + earnedTodayViaActivePlay);
            out.add("  Daily cap remaining:   " + dailyCapRemaining);
        }
        return List.copyOf(out);
    }
}
