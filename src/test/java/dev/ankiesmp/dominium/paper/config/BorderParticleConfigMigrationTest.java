package dev.ankiesmp.dominium.paper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressie voor de "eigenaar ziet eigen border niet" live-bug: oude
 * configs uit fase 4.5 hadden {@code only-foreign-claims: true}; de
 * pre-v2 → v2 migratie moet die naar {@code false} zetten en de config-
 * version bumpen zodat gebruikers hun eigen border weer zien.
 */
class BorderParticleConfigMigrationTest {

    private final List<String> warnings = new ArrayList<>();
    private final BorderParticleConfigLoader.WarnSink sink = warnings::add;

    // MG-201 — legacy config met onlyForeign=true en geen version → wordt naar false gemigreerd.
    @Test
    void legacyConfigMigratesOnlyForeignToFalse() {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("claim-border-particles.only-foreign-claims", true);
        // Geen config-version = pre-v2.
        var out = BorderParticleConfigLoader.loadForTest(cfg, sink);
        assertFalse(out.onlyForeignClaims(),
                "pre-v2 config met true moet naar false worden gemigreerd");
        assertEquals(BorderParticleConfigLoader.CONFIG_VERSION,
                cfg.getInt("config-version"),
                "config-version moet naar de actuele versie gezet zijn");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("only-foreign-claims")),
                "migration warning verwacht");
    }

    // MG-202 — configs met version=2 preserveren gebruikerskeuze.
    @Test
    void v2ConfigPreservesUserChoice() {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("config-version", 2);
        cfg.set("claim-border-particles.only-foreign-claims", true);
        var out = BorderParticleConfigLoader.loadForTest(cfg, sink);
        assertTrue(out.onlyForeignClaims(),
                "v2-config: gebruikersvoorkeur true moet behouden blijven");
    }

    // MG-203 — configs met onlyForeign=false pre-v2 blijven false + bump version.
    @Test
    void legacyConfigWithFalseStaysFalseAndBumpsVersion() {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("claim-border-particles.only-foreign-claims", false);
        var out = BorderParticleConfigLoader.loadForTest(cfg, sink);
        assertFalse(out.onlyForeignClaims());
        assertEquals(BorderParticleConfigLoader.CONFIG_VERSION,
                cfg.getInt("config-version"));
    }
}
