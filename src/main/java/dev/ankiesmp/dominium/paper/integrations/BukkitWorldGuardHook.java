package dev.ankiesmp.dominium.paper.integrations;

import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.integrations.WorldGuardHook;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reflective WorldGuard-hook. Ontwijkt compile-time dependency op WorldGuard/
 * WorldEdit door de RegionContainer lazy via reflection te resolven. Zonder
 * WorldGuard levert {@link #detect} de {@link WorldGuardHook#NO_OP}-variant op.
 */
public final class BukkitWorldGuardHook implements WorldGuardHook {

    private final Object regionContainer;
    private final Method getMethod;         // RegionContainer#get(WE-world)
    private final Method wrapWorldMethod;   // BukkitAdapter.adapt(World) → WE-world
    private final Method getApplicableRegionsMethod; // RegionManager#getApplicableRegions(ProtectedRegion) → ApplicableRegionSet
    private final Method applicableSizeMethod;       // ApplicableRegionSet#size()
    private final Method applicableIteratorMethod;   // ApplicableRegionSet#iterator()
    private final Method regionIdMethod;             // ProtectedRegion#getId()
    private final java.lang.reflect.Constructor<?> cuboidCtor;   // ProtectedCuboidRegion(String, BlockVector3, BlockVector3)
    private final Method blockVectorAtMethod;        // BlockVector3.at(int,int,int)

    private BukkitWorldGuardHook(Object rc, Method get, Method wrap, Method getApp,
                                 Method appSize, Method appIter, Method id,
                                 java.lang.reflect.Constructor<?> cuboidCtor,
                                 Method bvAt) {
        this.regionContainer = rc;
        this.getMethod = get;
        this.wrapWorldMethod = wrap;
        this.getApplicableRegionsMethod = getApp;
        this.applicableSizeMethod = appSize;
        this.applicableIteratorMethod = appIter;
        this.regionIdMethod = id;
        this.cuboidCtor = cuboidCtor;
        this.blockVectorAtMethod = bvAt;
    }

    public static WorldGuardHook detect(Logger log) {
        try {
            Class<?> wg = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object inst = wg.getMethod("getInstance").invoke(null);
            Object platform = wg.getMethod("getPlatform").invoke(inst);
            Object regionContainer = platform.getClass()
                    .getMethod("getRegionContainer").invoke(platform);
            Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
            Method get = regionContainer.getClass().getMethod("get", weWorldClass);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method wrap = adapter.getMethod("adapt", World.class);
            Class<?> rm = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
            Class<?> pr = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            Method getApp = rm.getMethod("getApplicableRegions", pr);
            Class<?> ars = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet");
            Method appSize = ars.getMethod("size");
            Method appIter = ars.getMethod("iterator");
            Method id = pr.getMethod("getId");
            Class<?> cuboid = Class.forName(
                    "com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion");
            Class<?> bv3 = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            java.lang.reflect.Constructor<?> ctor = cuboid.getConstructor(String.class, bv3, bv3);
            Method bvAt = bv3.getMethod("at", int.class, int.class, int.class);
            log.info("WorldGuard hook active: exact ApplicableRegionSet check via ProtectedCuboidRegion.");
            return new BukkitWorldGuardHook(regionContainer, get, wrap, getApp,
                    appSize, appIter, id, ctor, bvAt);
        } catch (ClassNotFoundException ex) {
            log.info("WorldGuard not installed — using NO_OP hook.");
            return WorldGuardHook.NO_OP;
        } catch (ReflectiveOperationException ex) {
            log.warn("WorldGuard present but reflective binding failed ({}): using NO_OP hook.",
                    ex.toString());
            return WorldGuardHook.NO_OP;
        } catch (RuntimeException ex) {
            log.warn("WorldGuard detection failed: {} — using NO_OP hook.", ex.toString());
            return WorldGuardHook.NO_OP;
        }
    }

    @Override public boolean available() { return true; }

    @Override
    public Optional<String> firstBlockingRegion(WorldRef world, List<ClaimRectangle> proposed,
                                                boolean ignoreGlobalRegion) {
        World bukkitWorld = Bukkit.getWorld(world.id());
        if (bukkitWorld == null) return Optional.empty();
        try {
            Object weWorld = wrapWorldMethod.invoke(null, bukkitWorld);
            Object mgr = getMethod.invoke(regionContainer, weWorld);
            if (mgr == null) return Optional.empty();
            int minY = bukkitWorld.getMinHeight();
            int maxY = bukkitWorld.getMaxHeight() - 1;
            for (ClaimRectangle rect : proposed) {
                // Bouw ProtectedCuboidRegion voor de volledige XZ×wereldkolom.
                Object minBv = blockVectorAtMethod.invoke(null, rect.minX(), minY, rect.minZ());
                Object maxBv = blockVectorAtMethod.invoke(null, rect.maxX(), maxY, rect.maxZ());
                Object probe = cuboidCtor.newInstance("__dominium_probe__", minBv, maxBv);
                Object applicable = getApplicableRegionsMethod.invoke(mgr, probe);
                int size = (int) applicableSizeMethod.invoke(applicable);
                if (size == 0) continue;
                var it = (java.util.Iterator<?>) applicableIteratorMethod.invoke(applicable);
                while (it.hasNext()) {
                    Object region = it.next();
                    String id = (String) regionIdMethod.invoke(region);
                    if (ignoreGlobalRegion && "__global__".equals(id)) continue;
                    return Optional.of(id);
                }
            }
        } catch (ReflectiveOperationException ex) {
            // Fail-safe: bij reflectiefout blokkeer bij mogelijke overlap — dat
            // voorkomt dat een gebroken WG-integratie toestaat wat hij zou
            // moeten weigeren.
            return Optional.of("<WORLDGUARD_REFLECTION_ERROR:" + ex.getClass().getSimpleName() + ">");
        }
        return Optional.empty();
    }
}
