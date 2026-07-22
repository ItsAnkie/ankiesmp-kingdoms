package dev.ankiesmp.dominium.core.territory;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BorderRenderHysteresisTest {

    private final UUID CLAIM = UUID.randomUUID();
    private final double TRIGGER = 5.0;
    private final double HIDE = 6.5;

    // BH-001 — off + geen kandidaat → stay off.
    @Test
    void offWithNoCandidate() {
        var t = BorderRenderHysteresis.decide(null, null, 10.0, TRIGGER, HIDE);
        assertEquals(BorderRenderHysteresis.Transition.STAY_OFF, t);
        assertFalse(BorderRenderHysteresis.emits(t));
    }

    // BH-002 — off + kandidaat buiten trigger → stay off, geen emit.
    @Test
    void offOutsideTrigger() {
        var t = BorderRenderHysteresis.decide(null, CLAIM, 7.0, TRIGGER, HIDE);
        assertEquals(BorderRenderHysteresis.Transition.STAY_OFF, t);
        assertFalse(BorderRenderHysteresis.emits(t));
    }

    // BH-003 — off + kandidaat binnen trigger → activate + emit.
    @Test
    void offInsideTriggerActivates() {
        var t = BorderRenderHysteresis.decide(null, CLAIM, 4.5, TRIGGER, HIDE);
        assertEquals(BorderRenderHysteresis.Transition.ACTIVATE, t);
        assertTrue(BorderRenderHysteresis.emits(t));
    }

    // BH-004 — on + tussen trigger en hide (hysteresis-band) → stay on + emit.
    @Test
    void onInsideHysteresisBandStaysOn() {
        var t = BorderRenderHysteresis.decide(CLAIM, CLAIM, 6.0, TRIGGER, HIDE);
        assertEquals(BorderRenderHysteresis.Transition.STAY_ON, t);
        assertTrue(BorderRenderHysteresis.emits(t));
    }

    // BH-005 — on + buiten hide → deactivate, GEEN emit.
    @Test
    void onBeyondHideDeactivates() {
        var t = BorderRenderHysteresis.decide(CLAIM, CLAIM, 7.0, TRIGGER, HIDE);
        assertEquals(BorderRenderHysteresis.Transition.DEACTIVATE, t);
        assertFalse(BorderRenderHysteresis.emits(t),
                "geen laatste emit buiten hide-distance");
    }

    // BH-006 — on + kandidaat = null → deactivate.
    @Test
    void onWithNoCandidateDeactivates() {
        var t = BorderRenderHysteresis.decide(CLAIM, null, 0.0, TRIGGER, HIDE);
        assertEquals(BorderRenderHysteresis.Transition.DEACTIVATE, t);
        assertFalse(BorderRenderHysteresis.emits(t));
    }

    // BH-007 — on + ander kandidaat binnen trigger → switch + emit.
    @Test
    void onSwitchesClaimWhenInsideTrigger() {
        var other = UUID.randomUUID();
        var t = BorderRenderHysteresis.decide(CLAIM, other, 4.0, TRIGGER, HIDE);
        assertEquals(BorderRenderHysteresis.Transition.SWITCH_CLAIM_ACTIVATE, t);
        assertTrue(BorderRenderHysteresis.emits(t));
    }

    // BH-008 — on + ander kandidaat buiten trigger → deactivate.
    @Test
    void onSwitchesClaimOutsideTriggerDeactivates() {
        var other = UUID.randomUUID();
        var t = BorderRenderHysteresis.decide(CLAIM, other, 7.0, TRIGGER, HIDE);
        assertEquals(BorderRenderHysteresis.Transition.DEACTIVATE, t);
        assertFalse(BorderRenderHysteresis.emits(t));
    }
}
