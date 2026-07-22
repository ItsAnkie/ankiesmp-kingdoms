package dev.ankiesmp.dominium.paper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backward-compat regressietests: oude {@code config.yml}-varianten mogen
 * NIET meer resulteren in {@code IllegalStateException: invalid border-particle config}.
 */
class BorderParticleConfigLoaderTest {

    private final List<String> warnings = new ArrayList<>();
    private final BorderParticleConfigLoader.WarnSink sink = warnings::add;

    /** MemoryConfiguration wrapped als FileConfiguration voor de loader-API. */
    private static FileConfiguration blank() {
        return new YamlConfiguration();
    }

    // BC-001 — oude config zonder claim-border-particles-sectie helemaal → defaults.
    @Test
    void missingSectionUsesDefaults() {
        FileConfiguration cfg = blank();
        var out = BorderParticleConfigLoader.loadForTest(cfg, sink);
        assertEquals(BorderParticleDefaults.TRIGGER_DISTANCE, out.triggerDistance());
        assertEquals(BorderParticleDefaults.HIDE_DISTANCE, out.hideDistance());
        assertEquals(BorderParticleDefaults.RENDER_DISTANCE, out.renderDistance());
        assertEquals(BorderParticleDefaults.SPACING, out.spacing());
        assertEquals(BorderParticleDefaults.MAX_PARTICLES_PER_PLAYER, out.maxParticlesPerPlayer());
        assertEquals(BorderParticleDefaults.COLOR_R, out.colorRed());
        assertEquals(BorderParticleDefaults.SIZE, out.size(), 1e-6);
        assertTrue(warnings.isEmpty(), "geen warnings bij volledig missende sectie");
    }

    // BC-002 — oude config met trigger-distance=10 en géén hide-distance:
    //          moet NIET crashen (dat was de live-bug); hide wordt gecorrigeerd.
    @Test
    void oldTriggerWithoutHideDistanceRepairs() {
        FileConfiguration cfg = blank();
        cfg.set("claim-border-particles.trigger-distance", 10.0);
        var out = BorderParticleConfigLoader.loadForTest(cfg, sink);
        assertEquals(10.0, out.triggerDistance());
        assertTrue(out.hideDistance() >= out.triggerDistance(),
                "hide moet >= trigger zijn na repair");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("hide-distance")),
                "warning over hide-distance repair verwacht");
    }

    // BC-003 — gedeeltelijke config: alleen enabled+particle gezet.
    @Test
    void partialConfigFillsRest() {
        FileConfiguration cfg = blank();
        cfg.set("claim-border-particles.enabled", false);
        cfg.set("claim-border-particles.particle", "DUST");
        var out = BorderParticleConfigLoader.loadForTest(cfg, sink);
        assertFalse(out.enabled());
        assertEquals("DUST", out.particle());
        assertEquals(BorderParticleDefaults.HIDE_DISTANCE, out.hideDistance());
    }

    // BC-004 — RGB buiten range → default + warning.
    @Test
    void rgbOutOfRangeFallsBackToDefault() {
        FileConfiguration cfg = blank();
        cfg.set("claim-border-particles.color.red", 999);
        cfg.set("claim-border-particles.color.green", -1);
        var out = BorderParticleConfigLoader.loadForTest(cfg, sink);
        assertEquals(BorderParticleDefaults.COLOR_R, out.colorRed());
        assertEquals(BorderParticleDefaults.COLOR_G, out.colorGreen());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("color.red")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("color.green")));
    }

    // BC-005 — size buiten [0.1, 4.0] → default + warning.
    @Test
    void sizeOutOfRangeFallsBackToDefault() {
        FileConfiguration cfg = blank();
        cfg.set("claim-border-particles.size", 10.0);
        var out = BorderParticleConfigLoader.loadForTest(cfg, sink);
        assertEquals(BorderParticleDefaults.SIZE, out.size(), 1e-6);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("size")));
    }

    // BC-006 — negatieve/0 waarden voor spacing/interval/budget → default + warning.
    @Test
    void nonPositiveNumericValuesFallBack() {
        FileConfiguration cfg = blank();
        cfg.set("claim-border-particles.spacing", 0);
        cfg.set("claim-border-particles.update-interval-ticks", 0);
        cfg.set("claim-border-particles.max-particles-per-player", 0);
        var out = BorderParticleConfigLoader.loadForTest(cfg, sink);
        assertEquals(BorderParticleDefaults.SPACING, out.spacing());
        assertEquals(BorderParticleDefaults.UPDATE_INTERVAL_TICKS, out.updateIntervalTicks());
        assertEquals(BorderParticleDefaults.MAX_PARTICLES_PER_PLAYER, out.maxParticlesPerPlayer());
        assertTrue(warnings.size() >= 3);
    }

    // BC-007 — actuele geldige config wordt zonder repairs geladen.
    @Test
    void currentValidConfigLoadsUnchanged() {
        FileConfiguration cfg = blank();
        cfg.set("claim-border-particles.enabled", true);
        cfg.set("claim-border-particles.particle", "DUST");
        cfg.set("claim-border-particles.color.red", 40);
        cfg.set("claim-border-particles.color.green", 200);
        cfg.set("claim-border-particles.color.blue", 40);
        cfg.set("claim-border-particles.size", 1.2);
        cfg.set("claim-border-particles.trigger-distance", 5.0);
        cfg.set("claim-border-particles.hide-distance", 6.5);
        cfg.set("claim-border-particles.render-distance", 11.0);
        cfg.set("claim-border-particles.spacing", 0.9);
        cfg.set("claim-border-particles.update-interval-ticks", 6);
        cfg.set("claim-border-particles.max-particles-per-player", 60);
        var out = BorderParticleConfigLoader.loadForTest(cfg, sink);
        assertEquals(40, out.colorRed());
        assertEquals(1.2f, out.size(), 1e-6);
        assertEquals(5.0, out.triggerDistance());
        assertEquals(6.5, out.hideDistance());
        assertTrue(warnings.isEmpty(), "geen warnings bij geldige config");
    }

    // BC-008 — write-back-hook wordt getriggerd bij missende keys.
    @Test
    void writeBackHookFiresForMissingKeys() {
        FileConfiguration cfg = blank();
        cfg.set("claim-border-particles.enabled", true); // laat de rest missing
        boolean[] called = {false};
        var out = BorderParticleConfigLoader.load(cfg, sink, mutation -> {
            called[0] = true;
            mutation.run();
        });
        assertNotNull(out);
        assertTrue(called[0], "write-back hook moet getriggerd zijn");
        assertTrue(cfg.isSet("claim-border-particles.hide-distance"),
                "missende key moet zijn geschreven na hook.run()");
    }

    // BC-009 — combinatie oud + custom render-distance kleiner dan hide → repair.
    @Test
    void inconsistentRenderRepaired() {
        FileConfiguration cfg = blank();
        cfg.set("claim-border-particles.trigger-distance", 5.0);
        cfg.set("claim-border-particles.hide-distance", 8.0);
        cfg.set("claim-border-particles.render-distance", 3.0);
        var out = BorderParticleConfigLoader.loadForTest(cfg, sink);
        assertTrue(out.renderDistance() >= out.hideDistance());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("render-distance")));
    }
}
