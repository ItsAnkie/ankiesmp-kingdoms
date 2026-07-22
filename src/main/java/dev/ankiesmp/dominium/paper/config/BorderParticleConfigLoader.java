package dev.ankiesmp.dominium.paper.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Backward-compatible parser voor de {@code claim-border-particles}-sectie.
 *
 * <p>Ontbrekende keys → defaults uit {@link BorderParticleDefaults} +
 * write-back naar de config zodat het bestand meegroeit. Ongeldige maar
 * herstelbare waarden → warning met exact pad + waarde, valt terug op
 * default. Alleen bij combinaties die de record-invarianten niet meer
 * kunnen redden worden ze gerepareerd (bijv. {@code hide < trigger}
 * → hide = trigger + 1.5, {@code render < hide} → render = hide × 2).
 */
public final class BorderParticleConfigLoader {

    private static final String ROOT = "claim-border-particles";
    /** Actuele config-versie. Elke backward-incompatible key-shift bumpt dit. */
    public static final int CONFIG_VERSION = 3;
    /** Voor pre-v2 configs: reset {@code only-foreign-claims} van true → false. */
    private static final int ONLY_FOREIGN_FLIP_VERSION = 2;

    private BorderParticleConfigLoader() {}

    /** Log-target voor tests zonder Bukkit-logger. */
    public interface WarnSink { void warn(String message); }

    public static DominiumConfig.BorderParticleConfig load(FileConfiguration cfg,
                                                           Logger log,
                                                           Consumer<Runnable> writeBackHook) {
        WarnSink sink = log::warn;
        return load(cfg, sink, writeBackHook);
    }

    /**
     * @param writeBackHook wanneer non-null, wordt aangeroepen met een
     *  {@link Runnable} die {@code cfg} muteert om ontbrekende keys op
     *  hun default te zetten. De caller kan daarna {@code saveConfig()}
     *  aanroepen. In tests mag {@code null} worden meegegeven.
     */
    public static DominiumConfig.BorderParticleConfig load(FileConfiguration cfg,
                                                           WarnSink sink,
                                                           Consumer<Runnable> writeBackHook) {
        List<Runnable> pendingWrites = new ArrayList<>();

        // Migratie voor pre-v2 configs: fase 4.5 leverde `only-foreign-claims: true`
        // waardoor eigenaars hun eigen border niet zagen. Vanaf v2 is de default
        // false. Als de gebruiker geen expliciete config-version heeft (dus komt
        // van een oude bundle), forceren we de default-waarde éénmalig terug —
        // in-place op cfg zodat de rest van de loader de nieuwe waarde ziet.
        int existingVersion = cfg.isSet("config-version") ? cfg.getInt("config-version", 0) : 0;
        if (existingVersion < ONLY_FOREIGN_FLIP_VERSION) {
            String ofPath = ROOT + ".only-foreign-claims";
            if (cfg.isSet(ofPath) && cfg.getBoolean(ofPath)) {
                sink.warn(ofPath + " was true (legacy pre-v2 default) — resetting to false so "
                        + "owners can see their own claim border. Set it back to true manually if desired.");
                cfg.set(ofPath, false);
                pendingWrites.add(() -> { /* al gemuteerd in-place; write-back triggert saveConfig */ });
            }
        }
        if (existingVersion < CONFIG_VERSION) {
            cfg.set("config-version", CONFIG_VERSION);
            pendingWrites.add(() -> { /* triggert saveConfig */ });
        }

        boolean enabled = readBoolean(cfg, "enabled", BorderParticleDefaults.ENABLED, sink, pendingWrites);
        String particle = readString(cfg, "particle", BorderParticleDefaults.PARTICLE, sink, pendingWrites);
        int r = readIntClamped(cfg, "color.red",   BorderParticleDefaults.COLOR_R, 0, 255, sink, pendingWrites);
        int g = readIntClamped(cfg, "color.green", BorderParticleDefaults.COLOR_G, 0, 255, sink, pendingWrites);
        int b = readIntClamped(cfg, "color.blue",  BorderParticleDefaults.COLOR_B, 0, 255, sink, pendingWrites);
        float size = readFloatClamped(cfg, "size", BorderParticleDefaults.SIZE,
                BorderParticleDefaults.SIZE_MIN, BorderParticleDefaults.SIZE_MAX, sink, pendingWrites);

        double trigger = readDoublePositive(cfg, "trigger-distance",
                BorderParticleDefaults.TRIGGER_DISTANCE, sink, pendingWrites);
        double hide = readDoubleWithDefault(cfg, "hide-distance",
                BorderParticleDefaults.HIDE_DISTANCE, sink, pendingWrites);
        if (hide < trigger) {
            double fixed = Math.max(trigger + 1.5, BorderParticleDefaults.HIDE_DISTANCE);
            sink.warn(ROOT + ".hide-distance (" + hide + ") is below trigger-distance ("
                    + trigger + "); using " + fixed);
            hide = fixed;
        }
        double render = readDoubleWithDefault(cfg, "render-distance",
                BorderParticleDefaults.RENDER_DISTANCE, sink, pendingWrites);
        if (render < hide) {
            double fixed = Math.max(hide * 2.0, BorderParticleDefaults.RENDER_DISTANCE);
            sink.warn(ROOT + ".render-distance (" + render + ") is below hide-distance ("
                    + hide + "); using " + fixed);
            render = fixed;
        }
        double spacing = readDoublePositive(cfg, "spacing",
                BorderParticleDefaults.SPACING, sink, pendingWrites);
        long updateTicks = readLongPositive(cfg, "update-interval-ticks",
                BorderParticleDefaults.UPDATE_INTERVAL_TICKS, sink, pendingWrites);
        int budget = readIntPositive(cfg, "max-particles-per-player",
                BorderParticleDefaults.MAX_PARTICLES_PER_PLAYER, sink, pendingWrites);
        boolean onlyForeign = readBoolean(cfg, "only-foreign-claims",
                BorderParticleDefaults.ONLY_FOREIGN_CLAIMS, sink, pendingWrites);
        boolean terrain = readBoolean(cfg, "terrain-following",
                BorderParticleDefaults.TERRAIN_FOLLOWING, sink, pendingWrites);
        double vOffset = readDoubleWithDefault(cfg, "vertical-offset",
                BorderParticleDefaults.VERTICAL_OFFSET, sink, pendingWrites);
        boolean debug = readBoolean(cfg, "debug", BorderParticleDefaults.DEBUG, sink, pendingWrites);

        if (!pendingWrites.isEmpty() && writeBackHook != null) {
            writeBackHook.accept(() -> pendingWrites.forEach(Runnable::run));
        }
        return new DominiumConfig.BorderParticleConfig(enabled, particle,
                r, g, b, size, trigger, hide, render, spacing,
                updateTicks, budget, onlyForeign, terrain, vOffset, debug);
    }

    // ---- key readers ----

    private static boolean readBoolean(FileConfiguration cfg, String rel, boolean def,
                                       WarnSink sink, List<Runnable> pending) {
        String path = ROOT + "." + rel;
        if (!cfg.isSet(path)) { pending.add(() -> cfg.set(path, def)); return def; }
        try { return cfg.getBoolean(path, def); }
        catch (RuntimeException ex) {
            sink.warn(path + " invalid (" + ex.getMessage() + "); using default " + def);
            return def;
        }
    }

    private static String readString(FileConfiguration cfg, String rel, String def,
                                     WarnSink sink, List<Runnable> pending) {
        String path = ROOT + "." + rel;
        if (!cfg.isSet(path)) { pending.add(() -> cfg.set(path, def)); return def; }
        String v = cfg.getString(path, def);
        if (v == null || v.isBlank()) {
            sink.warn(path + " is blank; using default " + def);
            return def;
        }
        return v;
    }

    private static int readIntClamped(FileConfiguration cfg, String rel, int def, int min, int max,
                                      WarnSink sink, List<Runnable> pending) {
        String path = ROOT + "." + rel;
        if (!cfg.isSet(path)) { pending.add(() -> cfg.set(path, def)); return def; }
        int v = cfg.getInt(path, def);
        if (v < min || v > max) {
            sink.warn(path + " = " + v + " out of [" + min + "," + max + "]; using default " + def);
            return def;
        }
        return v;
    }

    private static int readIntPositive(FileConfiguration cfg, String rel, int def,
                                       WarnSink sink, List<Runnable> pending) {
        String path = ROOT + "." + rel;
        if (!cfg.isSet(path)) { pending.add(() -> cfg.set(path, def)); return def; }
        int v = cfg.getInt(path, def);
        if (v <= 0) {
            sink.warn(path + " = " + v + " must be > 0; using default " + def);
            return def;
        }
        return v;
    }

    private static long readLongPositive(FileConfiguration cfg, String rel, long def,
                                         WarnSink sink, List<Runnable> pending) {
        String path = ROOT + "." + rel;
        if (!cfg.isSet(path)) { pending.add(() -> cfg.set(path, def)); return def; }
        long v = cfg.getLong(path, def);
        if (v <= 0) {
            sink.warn(path + " = " + v + " must be > 0; using default " + def);
            return def;
        }
        return v;
    }

    private static double readDoublePositive(FileConfiguration cfg, String rel, double def,
                                             WarnSink sink, List<Runnable> pending) {
        String path = ROOT + "." + rel;
        if (!cfg.isSet(path)) { pending.add(() -> cfg.set(path, def)); return def; }
        double v = cfg.getDouble(path, def);
        if (!(v > 0)) {
            sink.warn(path + " = " + v + " must be > 0; using default " + def);
            return def;
        }
        return v;
    }

    private static double readDoubleWithDefault(FileConfiguration cfg, String rel, double def,
                                                WarnSink sink, List<Runnable> pending) {
        String path = ROOT + "." + rel;
        if (!cfg.isSet(path)) { pending.add(() -> cfg.set(path, def)); return def; }
        double v = cfg.getDouble(path, def);
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            sink.warn(path + " = " + v + " invalid; using default " + def);
            return def;
        }
        return v;
    }

    private static float readFloatClamped(FileConfiguration cfg, String rel, float def,
                                          float min, float max,
                                          WarnSink sink, List<Runnable> pending) {
        String path = ROOT + "." + rel;
        if (!cfg.isSet(path)) { pending.add(() -> cfg.set(path, def)); return def; }
        float v = (float) cfg.getDouble(path, def);
        if (v < min || v > max) {
            sink.warn(path + " = " + v + " out of [" + min + "," + max + "]; using default " + def);
            return def;
        }
        return v;
    }

    /** Voor tests: parse zonder write-back. */
    public static DominiumConfig.BorderParticleConfig loadForTest(FileConfiguration cfg,
                                                                  WarnSink sink) {
        return load(cfg, sink, null);
    }
}
