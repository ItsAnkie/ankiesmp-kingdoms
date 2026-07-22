package dev.ankiesmp.dominium.core.kingdom;

import java.util.Objects;

/** Fase 5 fijnmazige capabilities. Vervangt verspreide role-comparisons. */
public enum KingdomCapability {
    VIEW_BANK,
    DEPOSIT_BANK,
    WITHDRAW_BANK,
    CONTRIBUTE_CLAIMBLOCKS,
    BUY_CLAIMBLOCKS,
    MANAGE_KINGDOM_CLAIMS;

    /** Vaste mapping. */
    public static boolean allowed(KingdomRole role, KingdomCapability cap) {
        Objects.requireNonNull(role);
        Objects.requireNonNull(cap);
        return switch (cap) {
            case VIEW_BANK, DEPOSIT_BANK, CONTRIBUTE_CLAIMBLOCKS ->
                    role == KingdomRole.LEADER || role == KingdomRole.CO_LEADER
                            || role == KingdomRole.MEMBER;
            case WITHDRAW_BANK, BUY_CLAIMBLOCKS, MANAGE_KINGDOM_CLAIMS ->
                    role == KingdomRole.LEADER || role == KingdomRole.CO_LEADER;
        };
    }
}
