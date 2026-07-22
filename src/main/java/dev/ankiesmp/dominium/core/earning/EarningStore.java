package dev.ankiesmp.dominium.core.earning;

/**
 * Persistente daily-cap-state. Boekt zelf géén ledgermutaties — dat
 * doet {@link ActivePlayEarner} via de {@link
 * dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger}.
 */
public interface EarningStore {

    /**
     * Reserveert atomair maximaal {@code requested} blocks voor
     * {@code (playerUuid, activeDay)}. Retourneert het daadwerkelijk
     * toegekende aantal — 0 als de dagcap al is bereikt.
     *
     * <p>Atomair via {@code INSERT ... ON CONFLICT DO UPDATE ... WHERE},
     * dus twee gelijktijdige earner-runs kunnen samen nooit meer dan
     * {@code dailyCap} boeken op één dag.
     */
    long reserveDailyEarning(java.util.UUID playerUuid, long activeDay,
                             long requested, long dailyCap, long updatedAtEpochMillis);

    long earnedToday(java.util.UUID playerUuid, long activeDay);
}
