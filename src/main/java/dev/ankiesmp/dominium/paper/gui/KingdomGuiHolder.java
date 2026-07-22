package dev.ankiesmp.dominium.paper.gui;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom {@link InventoryHolder}: stabiele GUI-identity zodat click-listeners
 * hun eigen GUI onmiskenbaar herkennen (nooit op basis van titel/displayname).
 *
 * <p>Elke holder draagt een <b>generation</b>. Wanneer een panel opnieuw
 * wordt gebouwd (bijv. na een bank-operatie) bumpt de service de generation
 * voor die viewer. Clicks in oudere holders (open in een tweede inventaris)
 * matchen de per-viewer huidige generation niet meer en worden geweigerd —
 * dit voorkomt dubbel-executie na een refresh.
 */
public final class KingdomGuiHolder implements InventoryHolder {

    public enum View { MAIN_NO_KINGDOM, MAIN_WITH_KINGDOM, BANK_PANEL }

    private static final ConcurrentHashMap<UUID, AtomicLong> GENERATIONS = new ConcurrentHashMap<>();

    private final View view;
    private final UUID viewerId;
    private final long generation;
    private Inventory inventory;

    public KingdomGuiHolder(View view, UUID viewerId) {
        this(view, viewerId, currentGeneration(viewerId));
    }

    public KingdomGuiHolder(View view, UUID viewerId, long generation) {
        this.view = Objects.requireNonNull(view);
        this.viewerId = Objects.requireNonNull(viewerId);
        this.generation = generation;
    }

    public View view() { return view; }
    public UUID viewerId() { return viewerId; }
    public long generation() { return generation; }

    void setInventory(Inventory inv) { this.inventory = inv; }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("inventory not attached");
        }
        return inventory;
    }

    @SuppressWarnings("unused")
    private InventoryType typeGuard() { return InventoryType.CHEST; }

    // ---- generation helpers ----

    public static long currentGeneration(UUID viewerId) {
        return GENERATIONS.computeIfAbsent(viewerId, k -> new AtomicLong(1L)).get();
    }

    /** Bumpt de generation zodat oude open GUI's ongeldig zijn voor clicks. */
    public static long bumpGeneration(UUID viewerId) {
        return GENERATIONS.computeIfAbsent(viewerId, k -> new AtomicLong(1L)).incrementAndGet();
    }

    /** Wist per-viewer state; gebruiken bij quit/kick zodat de map niet groeit. */
    public static void clear(UUID viewerId) {
        GENERATIONS.remove(viewerId);
    }
}
