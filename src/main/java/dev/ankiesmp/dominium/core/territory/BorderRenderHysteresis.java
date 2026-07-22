package dev.ankiesmp.dominium.core.territory;

import java.util.UUID;

/**
 * Pure state-machine voor de render-hysteresis van de particle-task.
 * <ul>
 *   <li>OFF, dist &lt;= trigger → ON;</li>
 *   <li>OFF, dist &gt; trigger  → OFF (blijft off, geen emit);</li>
 *   <li>ON,  dist &lt;= hide    → ON (emit);</li>
 *   <li>ON,  dist &gt; hide     → OFF (clear state, geen final emit).</li>
 * </ul>
 */
public final class BorderRenderHysteresis {

    private BorderRenderHysteresis() {}

    public enum Transition { STAY_OFF, ACTIVATE, STAY_ON, DEACTIVATE, SWITCH_CLAIM_ACTIVATE }

    /**
     * @param current huidige claim-id die wordt gerenderd, of {@code null} als er niet gerenderd wordt.
     * @param candidate dichtstbijzijnde claim, of {@code null}.
     * @param distance loodrechte afstand tot border van candidate.
     * @param triggerDistance activatie-drempel.
     * @param hideDistance deactivatie-drempel (moet ≥ trigger zijn).
     */
    public static Transition decide(UUID current, UUID candidate, double distance,
                                    double triggerDistance, double hideDistance) {
        if (candidate == null) {
            return current == null ? Transition.STAY_OFF : Transition.DEACTIVATE;
        }
        if (current == null) {
            return distance <= triggerDistance ? Transition.ACTIVATE : Transition.STAY_OFF;
        }
        if (!current.equals(candidate)) {
            return distance <= triggerDistance ? Transition.SWITCH_CLAIM_ACTIVATE
                    : Transition.DEACTIVATE;
        }
        return distance <= hideDistance ? Transition.STAY_ON : Transition.DEACTIVATE;
    }

    public static boolean emits(Transition t) {
        return t == Transition.ACTIVATE || t == Transition.STAY_ON || t == Transition.SWITCH_CLAIM_ACTIVATE;
    }
}
