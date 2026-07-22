package dev.ankiesmp.dominium.core.common;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable verwijzing naar een Minecraft-wereld. Bewust gescheiden van
 * Bukkit's {@code World} zodat pure core geen serverdependency krijgt.
 */
public final class WorldRef {

    private final UUID id;

    public WorldRef(UUID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public UUID id() { return id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorldRef other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return "world:" + id; }
}
