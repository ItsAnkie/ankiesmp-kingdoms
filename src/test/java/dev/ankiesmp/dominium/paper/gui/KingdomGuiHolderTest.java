package dev.ankiesmp.dominium.paper.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests op de per-viewer generation-teller die de GUI-listener gebruikt
 * om verouderde clicks te weigeren.
 */
class KingdomGuiHolderTest {

    @Test
    void bumpingIncrementsGeneration() {
        UUID viewer = UUID.randomUUID();
        KingdomGuiHolder.clear(viewer);
        long g0 = KingdomGuiHolder.currentGeneration(viewer);
        long g1 = KingdomGuiHolder.bumpGeneration(viewer);
        assertEquals(g0 + 1, g1);
        assertEquals(g1, KingdomGuiHolder.currentGeneration(viewer));
    }

    @Test
    void oldHolderGenerationIsStaleAfterBump() {
        UUID viewer = UUID.randomUUID();
        KingdomGuiHolder.clear(viewer);
        var oldHolder = new KingdomGuiHolder(KingdomGuiHolder.View.BANK_PANEL, viewer);
        long oldGen = oldHolder.generation();
        KingdomGuiHolder.bumpGeneration(viewer);
        var newHolder = new KingdomGuiHolder(KingdomGuiHolder.View.BANK_PANEL, viewer);
        assertNotEquals(oldGen, newHolder.generation(),
                "after bump, new holder must not match the old generation");
        assertEquals(KingdomGuiHolder.currentGeneration(viewer), newHolder.generation());
        // The listener uses `holder.generation() != currentGeneration(viewer)`
        // to reject stale clicks; assert that predicate holds for the old holder.
        assertNotEquals(oldHolder.generation(), KingdomGuiHolder.currentGeneration(viewer));
    }

    @Test
    void differentViewersHaveIndependentGenerations() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        KingdomGuiHolder.clear(a);
        KingdomGuiHolder.clear(b);
        long a0 = KingdomGuiHolder.currentGeneration(a);
        long b0 = KingdomGuiHolder.currentGeneration(b);
        KingdomGuiHolder.bumpGeneration(a);
        KingdomGuiHolder.bumpGeneration(a);
        assertEquals(a0 + 2, KingdomGuiHolder.currentGeneration(a));
        assertEquals(b0, KingdomGuiHolder.currentGeneration(b),
                "bumping A must not affect B's generation");
    }

    @Test
    void clearResetsGeneration() {
        UUID viewer = UUID.randomUUID();
        KingdomGuiHolder.bumpGeneration(viewer);
        KingdomGuiHolder.bumpGeneration(viewer);
        assertTrue(KingdomGuiHolder.currentGeneration(viewer) > 1L);
        KingdomGuiHolder.clear(viewer);
        assertEquals(1L, KingdomGuiHolder.currentGeneration(viewer),
                "cleared viewer resets to 1");
    }
}
