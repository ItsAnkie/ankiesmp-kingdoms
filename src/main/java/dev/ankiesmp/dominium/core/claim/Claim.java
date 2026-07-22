package dev.ankiesmp.dominium.core.claim;

import dev.ankiesmp.dominium.core.common.WorldRef;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory representatie van een top-level claim. Immutable — een
 * resize/rename/geometry-mutatie levert een nieuwe {@code Claim} op.
 *
 * <p>Sinds fase 5 draagt iedere claim een authoritative {@link ClaimGeometry}
 * (één of meerdere edge-connected {@link ClaimRectangle}-regio's).
 * {@link #rect()} retourneert nu de gecachede bounding-box uit
 * {@code geometry.bounds()} — voor backward compat met code die op één
 * rechthoek werkte. Alle nieuwe code gebruikt {@link #geometry()}.
 */
public final class Claim {

    private final UUID id;
    private final WorldRef world;
    private final ClaimGeometry geometry;
    private final ClaimOwner owner;
    private final Instant createdAt;

    public Claim(UUID id, WorldRef world, ClaimRectangle rect, ClaimOwner owner, Instant createdAt) {
        this(id, world, ClaimGeometry.ofRectangle(Objects.requireNonNull(rect, "rect")),
                owner, createdAt);
    }

    public Claim(UUID id, WorldRef world, ClaimGeometry geometry, ClaimOwner owner, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.world = Objects.requireNonNull(world, "world");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID id() { return id; }
    public WorldRef world() { return world; }
    public ClaimGeometry geometry() { return geometry; }
    /** Bounding-box uit de geometry — cached view voor spatial buckets / legacy code. */
    public ClaimRectangle rect() { return geometry.bounds(); }
    public List<ClaimRectangle> regions() { return geometry.regions(); }
    public ClaimOwner owner() { return owner; }
    public Instant createdAt() { return createdAt; }

    public Claim withRect(ClaimRectangle newRect) {
        return new Claim(id, world, ClaimGeometry.ofRectangle(newRect), owner, createdAt);
    }

    public Claim withGeometry(ClaimGeometry newGeometry) {
        return new Claim(id, world, newGeometry, owner, createdAt);
    }
}
