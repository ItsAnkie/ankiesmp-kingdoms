package dev.ankiesmp.dominium.paper.tool;

import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.common.WorldRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Volgt lopende shovel-selecties per speler. State leeft alleen in
 * memory; bij quit/logout/wereldwissel wordt de sessie vergeten.
 */
public final class SelectionState {

    public static final class Selection {
        private final WorldRef world;
        private final ShovelMode mode;
        private final int firstX;
        private final int firstZ;
        private Integer secondX;
        private Integer secondZ;

        private Selection(WorldRef world, ShovelMode mode, int firstX, int firstZ) {
            this.world = Objects.requireNonNull(world);
            this.mode = Objects.requireNonNull(mode);
            this.firstX = firstX;
            this.firstZ = firstZ;
        }

        public WorldRef world() { return world; }
        public ShovelMode mode() { return mode; }
        public int firstX() { return firstX; }
        public int firstZ() { return firstZ; }
        public Optional<int[]> second() {
            return secondX == null ? Optional.empty() : Optional.of(new int[]{secondX, secondZ});
        }

        public Optional<ClaimRectangle> asRectangle() {
            if (secondX == null) return Optional.empty();
            return Optional.of(ClaimRectangle.ofCorners(firstX, firstZ, secondX, secondZ));
        }

        void setSecond(int x, int z) { this.secondX = x; this.secondZ = z; }
    }

    private final Map<UUID, Selection> selections = new HashMap<>();

    public Selection beginOrReplace(UUID playerId, WorldRef world, ShovelMode mode, int x, int z) {
        Selection s = new Selection(world, mode, x, z);
        selections.put(playerId, s);
        return s;
    }

    public Optional<Selection> setSecond(UUID playerId, WorldRef world, int x, int z) {
        Selection s = selections.get(playerId);
        if (s == null || !s.world().equals(world)) return Optional.empty();
        s.setSecond(x, z);
        return Optional.of(s);
    }

    public Optional<Selection> current(UUID playerId) {
        return Optional.ofNullable(selections.get(playerId));
    }

    public void clear(UUID playerId) {
        selections.remove(playerId);
    }
}
