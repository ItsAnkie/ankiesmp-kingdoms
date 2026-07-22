package dev.ankiesmp.dominium.paper.tool;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Bouwer/detector voor de gemarkeerde Dominium claim shovel. Alleen items
 * met de bijbehorende PDC-tag worden door listeners geïntercepteerd —
 * gewone golden shovels blijven vanilla werken.
 */
public final class ClaimTool {

    public static final String NAMESPACE = "dominium";
    public static final String TOOL_TAG_KEY = "claim_tool";
    public static final String MODE_KEY = "claim_tool_mode";

    private final NamespacedKey toolTag;
    private final NamespacedKey modeTag;

    public ClaimTool(Plugin plugin) {
        this.toolTag = new NamespacedKey(plugin, TOOL_TAG_KEY);
        this.modeTag = new NamespacedKey(plugin, MODE_KEY);
    }

    public NamespacedKey toolTag() { return toolTag; }
    public NamespacedKey modeTag() { return modeTag; }

    public ItemStack create(ShovelMode initialMode) {
        ItemStack shovel = new ItemStack(Material.GOLDEN_SHOVEL);
        ItemMeta meta = shovel.getItemMeta();
        meta.displayName(Component.text("Dominium Claim Shovel", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(defaultLore(initialMode));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(toolTag, PersistentDataType.BYTE, (byte) 1);
        pdc.set(modeTag, PersistentDataType.STRING, initialMode.name());
        shovel.setItemMeta(meta);
        return shovel;
    }

    public boolean isTool(ItemStack stack) {
        if (stack == null || stack.getType() != Material.GOLDEN_SHOVEL || !stack.hasItemMeta()) return false;
        Byte marker = stack.getItemMeta().getPersistentDataContainer()
                .get(toolTag, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public ShovelMode readMode(ItemStack stack) {
        if (!isTool(stack)) return ShovelMode.INSPECT;
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(modeTag, PersistentDataType.STRING);
        if (raw == null) return ShovelMode.INSPECT;
        try {
            return ShovelMode.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return ShovelMode.INSPECT;
        }
    }

    public void writeMode(ItemStack stack, ShovelMode mode) {
        if (!isTool(stack)) return;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(modeTag, PersistentDataType.STRING, mode.name());
        meta.lore(defaultLore(mode));
        stack.setItemMeta(meta);
    }

    private static List<Component> defaultLore(ShovelMode mode) {
        return List.of(
                Component.text("Mode: ", NamedTextColor.GRAY)
                        .append(Component.text(mode.name(), NamedTextColor.YELLOW))
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Right-click: set corner A / B", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Left-click:  inspect", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Sneak+Right: cycle mode", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Sneak+Left:  cancel selection", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        );
    }

    public ShovelMode nextMode(ShovelMode mode) {
        return switch (mode) {
            case PERSONAL_CLAIM -> ShovelMode.INSPECT;
            case INSPECT -> ShovelMode.KINGDOM_CLAIM;
            case KINGDOM_CLAIM -> ShovelMode.PERSONAL_CLAIM;
        };
    }
}
