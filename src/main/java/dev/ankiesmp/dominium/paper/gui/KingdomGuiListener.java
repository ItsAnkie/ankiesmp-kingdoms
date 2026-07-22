package dev.ankiesmp.dominium.paper.gui;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ontvangt clicks in de Dominium-GUI. Herkent GUI's uitsluitend via
 * {@link KingdomGuiHolder}; canceld standaard alle mutaties, en dispatcht
 * de PDC-gebonden action naar {@link KingdomGuiService#handleAction}.
 *
 * <p>Anti-dubbelklik: iedere GUI-item heeft een unieke operation-ID
 * (PDC). We houden een per-viewer set van al-geconsumeerde IDs bij
 * (memory-bound door {@link KingdomGuiHolder#bumpGeneration}). Ook
 * checken we of de holder-generation nog matcht met de huidige — clicks
 * in een verouderde GUI (bijv. dubbele opening) worden geweigerd.
 */
public final class KingdomGuiListener implements Listener {

    public static final NamespacedKey OP_ID_KEY = new NamespacedKey("dominium", "gui_op_id");

    private final KingdomGuiService gui;
    private final ConcurrentHashMap<UUID, Set<String>> consumedByViewer = new ConcurrentHashMap<>();

    public KingdomGuiListener(KingdomGuiService gui) {
        this.gui = Objects.requireNonNull(gui);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KingdomGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID viewer = player.getUniqueId();
        // Viewer-mismatch: iemand anders klikt in een geopende GUI van deze viewer.
        if (!viewer.equals(holder.viewerId())) return;
        // Stale GUI: generation is achterhaald door een refresh/bank-op.
        if (holder.generation() != KingdomGuiHolder.currentGeneration(viewer)) return;
        var clicked = event.getCurrentItem();
        if (clicked == null) return;
        // Anti-dubbelklik: per unieke op-ID max één executie.
        String opId = clicked.hasItemMeta()
                ? clicked.getItemMeta().getPersistentDataContainer()
                    .get(OP_ID_KEY, PersistentDataType.STRING)
                : null;
        if (opId != null) {
            Set<String> consumed = consumedByViewer.computeIfAbsent(viewer,
                    k -> ConcurrentHashMap.newKeySet());
            if (!consumed.add(opId)) return;
        }
        KingdomGuiService.actionOf(clicked)
                .ifPresent(action -> gui.handleAction(player, action));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof KingdomGuiHolder) {
            event.setCancelled(true);
        }
    }

    /** Wist per-viewer state (aan te roepen bij quit/kick). */
    public void clear(UUID viewer) {
        consumedByViewer.remove(viewer);
    }

    /** Voor tests: geeft aan of een op-ID al is geconsumeerd. */
    boolean isConsumed(UUID viewer, String opId) {
        Set<String> c = consumedByViewer.get(viewer);
        return c != null && c.contains(opId);
    }

    /** Voor tests: reset consumed-set voor een viewer. */
    void resetConsumed(UUID viewer) {
        consumedByViewer.remove(viewer);
    }

    /** Voor tests: markeer een op-ID als geconsumeerd. */
    Set<String> consumedFor(UUID viewer) {
        return consumedByViewer.getOrDefault(viewer, new HashSet<>());
    }
}
