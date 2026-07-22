package dev.ankiesmp.dominium.paper.config;

/**
 * Single source of truth voor de {@code claim-border-particles} defaults.
 * Zowel de bundled {@code config.yml} als {@link DominiumConfig#fromBukkit}
 * gebruiken exact dezelfde waarden.
 */
public final class BorderParticleDefaults {

    public static final boolean ENABLED = true;
    public static final String  PARTICLE = "DUST";
    public static final int     COLOR_R = 50;
    public static final int     COLOR_G = 255;
    public static final int     COLOR_B = 50;
    public static final float   SIZE = 1.0f;
    public static final double  TRIGGER_DISTANCE = 5.0;
    public static final double  HIDE_DISTANCE = 6.5;
    public static final double  RENDER_DISTANCE = 11.0;
    public static final double  SPACING = 0.9;
    public static final long    UPDATE_INTERVAL_TICKS = 6L;
    public static final int     MAX_PARTICLES_PER_PLAYER = 60;
    public static final boolean ONLY_FOREIGN_CLAIMS = false;
    public static final boolean TERRAIN_FOLLOWING = true;
    public static final double  VERTICAL_OFFSET = 0.25;
    public static final boolean DEBUG = false;

    public static final float SIZE_MIN = 0.1f;
    public static final float SIZE_MAX = 4.0f;

    private BorderParticleDefaults() {}
}
