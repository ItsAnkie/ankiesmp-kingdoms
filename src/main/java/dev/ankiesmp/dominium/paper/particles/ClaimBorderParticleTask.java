package dev.ankiesmp.dominium.paper.particles;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.territory.ClaimBorderGeometry;
import dev.ankiesmp.dominium.core.territory.ClaimBorderSelector;
import dev.ankiesmp.dominium.paper.config.DominiumConfig;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compacte, per-speler border-render met hysteresis. Één claim tegelijk,
 * alleen lokale zijdes; DUST-particle met configureerbare kleur.
 * Stopt onmiddellijk met emissions zodra de speler buiten
 * {@code hideDistance} komt — geen laatste update, wacht niet op TTL.
 */
public final class ClaimBorderParticleTask implements Runnable {

    private final Server server;
    private final ClaimBorderSelector selector;
    private final DominiumConfig.BorderParticleConfig cfg;
    private final Logger log;
    private final Particle particle;
    private final Particle.DustOptions dustOptions;
    private final ClaimBorderGeometry.Config geoConfig;
    private final Map<UUID, PlayerRenderState> state = new ConcurrentHashMap<>();
    private long tickCounter = 0L;

    public ClaimBorderParticleTask(Server server, ClaimBorderSelector selector,
                                   DominiumConfig.BorderParticleConfig cfg, Logger log) {
        this.server = Objects.requireNonNull(server);
        this.selector = Objects.requireNonNull(selector);
        this.cfg = Objects.requireNonNull(cfg);
        this.log = Objects.requireNonNull(log);
        this.particle = resolveParticle(cfg.particle(), log);
        this.dustOptions = particle == Particle.DUST
                ? new Particle.DustOptions(
                        Color.fromRGB(cfg.colorRed(), cfg.colorGreen(), cfg.colorBlue()),
                        cfg.size())
                : null;
        this.geoConfig = new ClaimBorderGeometry.Config(
                cfg.triggerDistance(), cfg.renderDistance(),
                cfg.spacing(), cfg.maxParticlesPerPlayer());
        log.info("Border-particle task ready: particle={} rgb=({},{},{}) size={} "
                        + "trigger={} hide={} render={} spacing={} budget={} "
                        + "onlyForeign={} terrainFollowing={} debug={}",
                particle, cfg.colorRed(), cfg.colorGreen(), cfg.colorBlue(), cfg.size(),
                cfg.triggerDistance(), cfg.hideDistance(), cfg.renderDistance(),
                cfg.spacing(), cfg.maxParticlesPerPlayer(),
                cfg.onlyForeignClaims(), cfg.terrainFollowing(), cfg.debug());
    }

    @Override
    public void run() {
        if (!cfg.enabled() || particle == null) return;
        tickCounter++;
        if (cfg.debug() && (tickCounter % 20 == 1)) {
            log.info("[border-particles] tick={} tracked={}", tickCounter, state.size());
        }
        for (Player player : server.getOnlinePlayers()) {
            try {
                renderOne(player);
            } catch (RuntimeException ex) {
                log.warn("Border-particle render for {} failed: {}",
                        player.getUniqueId(), ex.toString());
            }
        }
    }

    private void renderOne(Player player) {
        UUID id = player.getUniqueId();
        World world = player.getWorld();
        WorldRef worldRef = new WorldRef(world.getUID());
        double px = player.getLocation().getX();
        double pz = player.getLocation().getZ();

        PlayerRenderState st = state.get(id);
        Claim closest = pickClosest(worldRef, px, pz, id);

        // Hysteresis:
        //  - niet actief én binnen trigger-distance → activeer;
        //  - actief én buiten hide-distance → deactiveer, geen final emit.
        if (closest == null) {
            if (st != null) {
                state.remove(id);
                debug("player={} left range (no candidate) → cleared render state", player.getName());
            }
            return;
        }
        double dist = Math.abs(ClaimBorderGeometry.distanceToBorder(closest.rect(), px, pz));
        if (st == null) {
            if (dist > cfg.triggerDistance()) return;
            st = new PlayerRenderState(closest.id());
            state.put(id, st);
            debug("player={} entered range dist={} claim={}", player.getName(), dist, closest.id());
        } else {
            if (!st.claimId.equals(closest.id())) {
                if (dist > cfg.triggerDistance()) {
                    state.remove(id);
                    return;
                }
                st = new PlayerRenderState(closest.id());
                state.put(id, st);
            }
            if (dist > cfg.hideDistance()) {
                state.remove(id);
                debug("player={} left hide-distance ({}) → cleared, no emit", player.getName(), dist);
                return;
            }
        }

        List<ClaimBorderGeometry.Point> points = closest.geometry().regions().size() > 1
                ? dev.ankiesmp.dominium.core.territory.OuterOutline.pointsNearPlayer(
                        closest.geometry(), px, pz, geoConfig)
                : ClaimBorderGeometry.localPointsNearPlayer(closest.rect(), px, pz, geoConfig);
        int budgetLeft = cfg.maxParticlesPerPlayer();
        int rendered = 0;
        for (ClaimBorderGeometry.Point p : points) {
            if (budgetLeft-- <= 0) break;
            int bx = (int) Math.floor(p.x());
            int bz = (int) Math.floor(p.z());
            double y = terrainY(world, bx, bz, player.getLocation().getY());
            spawn(player, p.x(), y + cfg.verticalOffset(), p.z());
            rendered++;
        }
        debug("player={} claim={} rendered={} budgetLeft={}",
                player.getName(), closest.id(), rendered, budgetLeft);
    }

    private void spawn(Player player, double x, double y, double z) {
        if (particle == Particle.DUST && dustOptions != null) {
            player.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0, dustOptions);
        } else {
            player.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private Claim pickClosest(WorldRef world, double px, double pz, UUID playerId) {
        List<Claim> nearby = selector.select(world, px, pz, playerId,
                cfg.renderDistance(), cfg.onlyForeignClaims());
        if (nearby.isEmpty()) return null;
        Claim best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Claim c : nearby) {
            double d = Math.abs(ClaimBorderGeometry.distanceToBorder(c.rect(), px, pz));
            if (d < bestDist) { bestDist = d; best = c; }
        }
        return best;
    }

    private double terrainY(World world, int bx, int bz, double fallback) {
        if (!cfg.terrainFollowing()) return fallback;
        int chunkX = bx >> 4, chunkZ = bz >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return fallback;
        try {
            int y = world.getHighestBlockYAt(bx, bz);
            return y + 1.0;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    /** Wist render-state voor een speler (quit/teleport/world change). */
    public void clearPlayer(UUID playerId) {
        state.remove(playerId);
    }

    /** Wist render-state voor een claim (resize/delete). */
    public void clearClaim(UUID claimId) {
        state.entrySet().removeIf(e -> e.getValue().claimId.equals(claimId));
    }

    public void clearAll() {
        state.clear();
    }

    /** Voor tests. */
    public boolean isRendering(UUID playerId) { return state.containsKey(playerId); }
    public int trackedPlayerCount() { return state.size(); }

    private void debug(String fmt, Object... args) {
        if (cfg.debug()) log.info("[border-particles] " + fmt, args);
    }

    private static Particle resolveParticle(String name, Logger log) {
        if (name == null || name.isBlank() || name.equalsIgnoreCase("DUST")) {
            return Particle.DUST;
        }
        if (name.equalsIgnoreCase("HAPPY_VILLAGER")) return Particle.HAPPY_VILLAGER;
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown particle '{}', falling back to DUST.", name);
            return Particle.DUST;
        }
    }

    private static final class PlayerRenderState {
        final UUID claimId;
        PlayerRenderState(UUID claimId) { this.claimId = claimId; }
    }
}
