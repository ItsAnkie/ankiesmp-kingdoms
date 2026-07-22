package dev.ankiesmp.dominium.paper.gui;

import dev.ankiesmp.dominium.core.bank.BankOperation;
import dev.ankiesmp.dominium.core.bank.BankStore;
import dev.ankiesmp.dominium.core.bank.KingdomBankService;
import dev.ankiesmp.dominium.core.bank.KingdomClaimBlockPoolService;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.kingdom.Kingdom;
import dev.ankiesmp.dominium.core.kingdom.KingdomCapability;
import dev.ankiesmp.dominium.core.kingdom.KingdomInviteService;
import dev.ankiesmp.dominium.core.kingdom.KingdomMember;
import dev.ankiesmp.dominium.core.kingdom.KingdomMembershipService;
import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.core.kingdom.KingdomVisitorService;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Open- en klikafhandeling voor de kingdom-GUI. GUI-clickhandlers roepen
 * dezelfde core-services aan als de commands — geen duplicaat businesslogica.
 *
 * <p>Bank/claim-block panel (Phase 5): toont saldo, kingdom-claimblocks,
 * persoonlijke claimblocks, gebruikte area en recente bank-ops; met
 * preset-knoppen voor deposit/withdraw/contribute/buy. Iedere klik
 * revalideert viewer-UUID, kingdom-bestaan, membership, capability en
 * balance <b>op het moment van uitvoeren</b> (niet op basis van item-state);
 * de generation-check en per-op-ID-consumptie voorkomen dubbelklik en
 * clicks in verouderde GUI's.
 */
public final class KingdomGuiService {

    public static final NamespacedKey ACTION_KEY_KEY = new NamespacedKey("dominium", "gui_action");

    private final Plugin plugin;
    private final KingdomService kingdomService;
    private final KingdomMembershipService membershipService;
    private final KingdomInviteService inviteService;
    private final KingdomVisitorService visitorService;
    private final KingdomBankService bankService;
    private final KingdomClaimBlockPoolService poolService;
    private final BankStore bankStore;
    private final ClaimBlockLedger ledger;
    private final ClaimIndex claimIndex;
    private final Executor dbExecutor;

    /** Legacy 6-arg ctor voor bootstraps zonder bank/pool (behouden zodat
     *  bestaande tests niet stukgaan). Bank-panel opent dan een lege state. */
    public KingdomGuiService(Plugin plugin,
                             KingdomService kingdomService,
                             KingdomMembershipService membershipService,
                             KingdomInviteService inviteService,
                             KingdomVisitorService visitorService,
                             Executor dbExecutor) {
        this(plugin, kingdomService, membershipService, inviteService, visitorService,
                null, null, null, null, null, dbExecutor);
    }

    public KingdomGuiService(Plugin plugin,
                             KingdomService kingdomService,
                             KingdomMembershipService membershipService,
                             KingdomInviteService inviteService,
                             KingdomVisitorService visitorService,
                             KingdomBankService bankService,
                             KingdomClaimBlockPoolService poolService,
                             BankStore bankStore,
                             ClaimBlockLedger ledger,
                             ClaimIndex claimIndex,
                             Executor dbExecutor) {
        this.plugin = Objects.requireNonNull(plugin);
        this.kingdomService = Objects.requireNonNull(kingdomService);
        this.membershipService = Objects.requireNonNull(membershipService);
        this.inviteService = Objects.requireNonNull(inviteService);
        this.visitorService = Objects.requireNonNull(visitorService);
        this.bankService = bankService;
        this.poolService = poolService;
        this.bankStore = bankStore;
        this.ledger = ledger;
        this.claimIndex = claimIndex;
        this.dbExecutor = Objects.requireNonNull(dbExecutor);
    }

    public void open(Player player) {
        UUID actor = player.getUniqueId();
        dbExecutor.execute(() -> {
            var membership = kingdomService.membershipFor(actor);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (membership.isEmpty()) {
                    player.openInventory(buildNoKingdom(actor));
                } else {
                    var kingdom = kingdomService.findById(membership.get().kingdomId())
                            .orElse(null);
                    var members = kingdom == null ? List.<KingdomMember>of()
                            : kingdomService.listMembers(kingdom.id());
                    long visitorCount = kingdom == null ? 0
                            : visitorService.list(kingdom.id()).size();
                    player.openInventory(buildMainWithKingdom(actor, kingdom,
                            membership.get(), members, visitorCount));
                }
            });
        });
    }

    // ---- click handling ----

    public void handleAction(Player player, KingdomGuiAction action) {
        switch (action) {
            case CLOSE -> player.closeInventory();
            case CREATE_HINT -> player.sendMessage(Component.text(
                    "Use /kingdom create <name> to create your kingdom.",
                    NamedTextColor.YELLOW));
            case LEAVE -> {
                UUID actor = player.getUniqueId();
                dbExecutor.execute(() -> {
                    var r = membershipService.leave(actor);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (r.isOk()) {
                            player.sendMessage(Component.text("You left the kingdom.",
                                    NamedTextColor.YELLOW));
                        } else {
                            player.sendMessage(Component.text(r.message(), NamedTextColor.RED));
                        }
                        player.closeInventory();
                    });
                });
            }
            case DISBAND -> player.sendMessage(Component.text(
                    "Use /kingdom disband then /kingdom confirm to disband.",
                    NamedTextColor.YELLOW));
            case MEMBERS_PANEL -> openMembersPanel(player);
            case VISITORS_PANEL -> openVisitorsPanel(player);
            case INVITES_PANEL -> openInvitesPanel(player);
            case BANK_PANEL -> openBankPanel(player);
            case BANK_REFRESH -> refreshBankPanel(player);
            case BANK_DEPOSIT_10, BANK_DEPOSIT_100, BANK_DEPOSIT_1000 ->
                    executeBankOp(player, action, BankOp.DEPOSIT);
            case BANK_WITHDRAW_10, BANK_WITHDRAW_100, BANK_WITHDRAW_1000 ->
                    executeBankOp(player, action, BankOp.WITHDRAW);
            case POOL_CONTRIBUTE_10, POOL_CONTRIBUTE_100, POOL_CONTRIBUTE_1000 ->
                    executePoolOp(player, action, BankOp.CONTRIBUTE);
            case POOL_BUY_10, POOL_BUY_100, POOL_BUY_1000 ->
                    executePoolOp(player, action, BankOp.BUY);
            case NOOP -> { /* no-op */ }
        }
    }

    private enum BankOp { DEPOSIT, WITHDRAW, CONTRIBUTE, BUY }

    // ---- bank panel ----

    private void openBankPanel(Player player) {
        UUID actor = player.getUniqueId();
        dbExecutor.execute(() -> {
            var snap = loadBankSnapshot(actor);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                Inventory inv = buildBankPanel(actor, snap);
                player.openInventory(inv);
            });
        });
    }

    private void refreshBankPanel(Player player) {
        KingdomGuiHolder.bumpGeneration(player.getUniqueId());
        openBankPanel(player);
    }

    private BankSnapshot loadBankSnapshot(UUID actor) {
        var membership = kingdomService.membershipFor(actor);
        if (membership.isEmpty()) return BankSnapshot.notMember();
        UUID kingdomId = membership.get().kingdomId();
        Kingdom kingdom = kingdomService.findById(kingdomId).orElse(null);
        if (kingdom == null) return BankSnapshot.notMember();
        long bank = bankService == null ? 0L : bankService.balance(kingdomId);
        long kingdomBlocks = ledger == null ? 0L
                : ledger.balanceOrZero(HolderKey.kingdom(kingdomId)).balance();
        long personalBlocks = ledger == null ? 0L
                : ledger.balanceOrZero(HolderKey.player(actor)).balance();
        long usedArea = 0L;
        if (claimIndex != null) {
            for (var c : claimIndex.all()) {
                if (c.owner().type() == dev.ankiesmp.dominium.core.claim.ClaimType.KINGDOM
                        && c.owner().id().equals(kingdomId)) {
                    usedArea += c.geometry().area();
                }
            }
        }
        List<BankOperation> recent = bankStore == null ? List.of()
                : bankStore.listForKingdom(kingdomId, 5);
        return new BankSnapshot(kingdom, membership.get(), bank,
                kingdomBlocks, personalBlocks, usedArea, recent);
    }

    private Inventory buildBankPanel(UUID actor, BankSnapshot s) {
        long gen = KingdomGuiHolder.bumpGeneration(actor);
        Inventory inv = Bukkit.createInventory(
                new KingdomGuiHolder(KingdomGuiHolder.View.BANK_PANEL, actor, gen),
                54, MiniMessage.miniMessage().deserialize("<gold>Kingdom Bank"));
        if (!s.isMember()) {
            inv.setItem(22, named(Material.BARRIER, "Not in a kingdom",
                    "Join or create a kingdom first.", KingdomGuiAction.CLOSE));
            return inv;
        }
        var role = s.member.role();
        // Info row 0..8
        inv.setItem(0, named(Material.EMERALD, "Bank Balance",
                s.bank + " minor (~" + (s.bank / 100L) + " major)", KingdomGuiAction.NOOP));
        inv.setItem(1, named(Material.DIAMOND, "Kingdom Claimblocks",
                String.valueOf(s.kingdomBlocks), KingdomGuiAction.NOOP));
        inv.setItem(2, named(Material.LAPIS_LAZULI, "Your Claimblocks",
                String.valueOf(s.personalBlocks), KingdomGuiAction.NOOP));
        inv.setItem(3, named(Material.GRASS_BLOCK, "Used Area",
                s.usedArea + " blocks", KingdomGuiAction.NOOP));
        inv.setItem(4, named(Material.PAPER, "You: " + role, "", KingdomGuiAction.NOOP));
        inv.setItem(7, named(Material.CLOCK, "Refresh",
                "Reload balances + recent ops", KingdomGuiAction.BANK_REFRESH));
        inv.setItem(8, named(Material.BARRIER, "Close", "", KingdomGuiAction.CLOSE));

        // Deposit row 9..12 (Vault major-units)
        boolean canDeposit = KingdomCapability.allowed(role, KingdomCapability.DEPOSIT_BANK);
        inv.setItem(9,  presetItem(Material.GOLD_INGOT, "Deposit 10",
                canDeposit, KingdomGuiAction.BANK_DEPOSIT_10));
        inv.setItem(10, presetItem(Material.GOLD_INGOT, "Deposit 100",
                canDeposit, KingdomGuiAction.BANK_DEPOSIT_100));
        inv.setItem(11, presetItem(Material.GOLD_INGOT, "Deposit 1000",
                canDeposit, KingdomGuiAction.BANK_DEPOSIT_1000));

        // Withdraw row 18..20
        boolean canWithdraw = KingdomCapability.allowed(role, KingdomCapability.WITHDRAW_BANK);
        inv.setItem(18, presetItem(Material.GOLD_NUGGET, "Withdraw 10",
                canWithdraw, KingdomGuiAction.BANK_WITHDRAW_10));
        inv.setItem(19, presetItem(Material.GOLD_NUGGET, "Withdraw 100",
                canWithdraw, KingdomGuiAction.BANK_WITHDRAW_100));
        inv.setItem(20, presetItem(Material.GOLD_NUGGET, "Withdraw 1000",
                canWithdraw, KingdomGuiAction.BANK_WITHDRAW_1000));

        // Contribute row 27..29 (personal → kingdom claimblocks)
        boolean canContribute = true;  // altijd toegestaan: eigen blocks doneren
        inv.setItem(27, presetItem(Material.DIAMOND, "Contribute 10",
                canContribute, KingdomGuiAction.POOL_CONTRIBUTE_10));
        inv.setItem(28, presetItem(Material.DIAMOND, "Contribute 100",
                canContribute, KingdomGuiAction.POOL_CONTRIBUTE_100));
        inv.setItem(29, presetItem(Material.DIAMOND, "Contribute 1000",
                canContribute, KingdomGuiAction.POOL_CONTRIBUTE_1000));

        // Buy row 36..38 (bank → kingdom claimblocks)
        boolean canBuy = KingdomCapability.allowed(role, KingdomCapability.WITHDRAW_BANK);
        inv.setItem(36, presetItem(Material.EMERALD_BLOCK, "Buy 10 blocks",
                canBuy, KingdomGuiAction.POOL_BUY_10));
        inv.setItem(37, presetItem(Material.EMERALD_BLOCK, "Buy 100 blocks",
                canBuy, KingdomGuiAction.POOL_BUY_100));
        inv.setItem(38, presetItem(Material.EMERALD_BLOCK, "Buy 1000 blocks",
                canBuy, KingdomGuiAction.POOL_BUY_1000));

        // Recent ops row 45..49 (max 5)
        int slot = 45;
        for (BankOperation op : s.recent) {
            if (slot > 49) break;
            String label = op.kind() + " " + op.amountMinor() + " min";
            String lore = op.state() + " | " + op.correlationId().toString().substring(0, 8);
            inv.setItem(slot++, named(Material.BOOK, label, lore, KingdomGuiAction.NOOP));
        }
        return inv;
    }

    private ItemStack presetItem(Material mat, String title, boolean allowed,
                                 KingdomGuiAction action) {
        if (!allowed) {
            return named(Material.BARRIER, title + " (no permission)",
                    "Your role cannot perform this action.", KingdomGuiAction.NOOP);
        }
        return withOpId(named(mat, title, "Click to execute", action));
    }

    private ItemStack withOpId(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KingdomGuiListener.OP_ID_KEY,
                PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
        return item;
    }

    private void executeBankOp(Player player, KingdomGuiAction action, BankOp op) {
        if (bankService == null) {
            player.sendMessage(Component.text("Bank is not available on this server.",
                    NamedTextColor.RED));
            return;
        }
        long major = op == BankOp.DEPOSIT
                ? BankPresets.depositMajor(action)
                : BankPresets.withdrawMajor(action);
        if (major <= 0) return;
        long minor = BankPresets.minorUnits(major);
        UUID actor = player.getUniqueId();
        dbExecutor.execute(() -> {
            // Re-validate membership & capability op moment van uitvoeren.
            var m = kingdomService.membershipFor(actor);
            if (m.isEmpty()) {
                syncMessage(player, "You are no longer in a kingdom.", NamedTextColor.RED);
                return;
            }
            var cap = op == BankOp.DEPOSIT
                    ? KingdomCapability.DEPOSIT_BANK
                    : KingdomCapability.WITHDRAW_BANK;
            if (!KingdomCapability.allowed(m.get().role(), cap)) {
                syncMessage(player, "Your role cannot " + op + ".", NamedTextColor.RED);
                return;
            }
            var res = op == BankOp.DEPOSIT
                    ? bankService.deposit(actor, minor)
                    : bankService.withdraw(actor, minor);
            String msg = res.kind() == KingdomBankService.Kind.OK
                    ? op + " " + major + " ok. New bank balance: "
                        + res.newBalanceMinor() + " minor."
                    : op + " refused: " + res.kind() + " (" + res.message() + ")";
            var color = res.kind() == KingdomBankService.Kind.OK
                    ? NamedTextColor.GREEN : NamedTextColor.RED;
            syncMessage(player, msg, color);
            // Refresh panel op success — bumpt generation zodat verouderde clicks weg vallen.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) refreshBankPanel(player);
            });
        });
    }

    private void executePoolOp(Player player, KingdomGuiAction action, BankOp op) {
        if (poolService == null) {
            player.sendMessage(Component.text("Claim-block pool not available.",
                    NamedTextColor.RED));
            return;
        }
        long blocks = op == BankOp.CONTRIBUTE
                ? BankPresets.contributeBlocks(action)
                : BankPresets.buyBlocks(action);
        if (blocks <= 0) return;
        UUID actor = player.getUniqueId();
        dbExecutor.execute(() -> {
            var m = kingdomService.membershipFor(actor);
            if (m.isEmpty()) {
                syncMessage(player, "You are no longer in a kingdom.", NamedTextColor.RED);
                return;
            }
            var res = op == BankOp.CONTRIBUTE
                    ? poolService.contribute(actor, blocks)
                    : poolService.buy(actor, blocks);
            boolean ok = res.kind() == KingdomClaimBlockPoolService.Kind.OK;
            var color = ok ? NamedTextColor.GREEN : NamedTextColor.RED;
            syncMessage(player, op + " " + blocks + " blocks: "
                    + (ok ? "success (kingdom=" + res.kingdomBalance() + ")" : res.message()),
                    color);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) refreshBankPanel(player);
            });
        });
    }

    private void syncMessage(Player p, String msg, NamedTextColor color) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) p.sendMessage(Component.text(msg, color));
        });
    }

    // ---- panels (bestaand) ----

    private void openMembersPanel(Player player) {
        UUID actor = player.getUniqueId();
        dbExecutor.execute(() -> {
            var membership = kingdomService.membershipFor(actor);
            if (membership.isEmpty()) return;
            var members = kingdomService.listMembers(membership.get().kingdomId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                Inventory inv = Bukkit.createInventory(
                        new KingdomGuiHolder(KingdomGuiHolder.View.MAIN_WITH_KINGDOM, actor),
                        54, Component.text("Kingdom Members"));
                int slot = 0;
                for (KingdomMember m : members) {
                    if (slot >= 54) break;
                    inv.setItem(slot++, memberItem(m));
                }
                player.openInventory(inv);
            });
        });
    }

    private void openVisitorsPanel(Player player) {
        UUID actor = player.getUniqueId();
        dbExecutor.execute(() -> {
            var membership = kingdomService.membershipFor(actor);
            if (membership.isEmpty()) return;
            var list = visitorService.list(membership.get().kingdomId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                Inventory inv = Bukkit.createInventory(
                        new KingdomGuiHolder(KingdomGuiHolder.View.MAIN_WITH_KINGDOM, actor),
                        54, Component.text("Kingdom Visitors"));
                int slot = 0;
                for (var v : list) {
                    if (slot >= 54) break;
                    ItemStack it = named(Material.LEATHER_HELMET, "Visitor " + v.playerUuid(),
                            "Added by " + v.addedBy(), KingdomGuiAction.NOOP);
                    inv.setItem(slot++, it);
                }
                player.openInventory(inv);
            });
        });
    }

    private void openInvitesPanel(Player player) {
        UUID actor = player.getUniqueId();
        dbExecutor.execute(() -> {
            var list = inviteService.invitesFor(actor);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                Inventory inv = Bukkit.createInventory(
                        new KingdomGuiHolder(KingdomGuiHolder.View.MAIN_NO_KINGDOM, actor),
                        27, Component.text("Kingdom Invites"));
                int slot = 0;
                for (var invite : list) {
                    if (slot >= 27) break;
                    var k = kingdomService.findById(invite.kingdomId());
                    ItemStack it = named(Material.PAPER,
                            "Invite: " + k.map(Kingdom::displayName).orElse(invite.kingdomId().toString()),
                            "Use /kingdom accept " + k.map(Kingdom::displayName).orElse(""),
                            KingdomGuiAction.NOOP);
                    inv.setItem(slot++, it);
                }
                player.openInventory(inv);
            });
        });
    }

    // ---- builders ----

    private Inventory buildNoKingdom(UUID actor) {
        Inventory inv = Bukkit.createInventory(
                new KingdomGuiHolder(KingdomGuiHolder.View.MAIN_NO_KINGDOM, actor),
                27, MiniMessage.miniMessage().deserialize("<dark_green>Kingdom"));
        inv.setItem(11, named(Material.WRITABLE_BOOK, "About Kingdoms",
                "Kingdoms are extended teams. Not political.",
                KingdomGuiAction.NOOP));
        inv.setItem(13, named(Material.NETHER_STAR, "Create Kingdom",
                "Use /kingdom create <name>",
                KingdomGuiAction.CREATE_HINT));
        inv.setItem(15, named(Material.PAPER, "Open Invites",
                "See pending kingdom invites",
                KingdomGuiAction.INVITES_PANEL));
        inv.setItem(26, named(Material.BARRIER, "Close", "Close menu", KingdomGuiAction.CLOSE));
        return inv;
    }

    private Inventory buildMainWithKingdom(UUID actor, Kingdom kingdom,
                                           KingdomMember me, List<KingdomMember> members,
                                           long visitorCount) {
        Inventory inv = Bukkit.createInventory(
                new KingdomGuiHolder(KingdomGuiHolder.View.MAIN_WITH_KINGDOM, actor),
                45, MiniMessage.miniMessage().deserialize("<dark_green>Kingdom"));

        String leaderId = members.stream()
                .filter(m -> m.role() == dev.ankiesmp.dominium.core.kingdom.KingdomRole.LEADER)
                .findFirst().map(m -> m.playerUuid().toString().substring(0, 8)).orElse("?");

        inv.setItem(4, named(Material.GOLDEN_HELMET,
                kingdom == null ? "Kingdom" : kingdom.displayName(),
                "Leader: " + leaderId + " | Members: " + members.size()
                        + " | Visitors: " + visitorCount
                        + " | You: " + me.role(),
                KingdomGuiAction.NOOP));

        inv.setItem(20, named(Material.PLAYER_HEAD, "Members",
                "View kingdom members", KingdomGuiAction.MEMBERS_PANEL));
        inv.setItem(22, named(Material.LEATHER_HELMET, "Visitors",
                "Manage kingdom visitors", KingdomGuiAction.VISITORS_PANEL));
        inv.setItem(24, named(Material.PAPER, "Invites",
                "Pending invites", KingdomGuiAction.INVITES_PANEL));
        inv.setItem(30, named(Material.EMERALD, "Bank / Claimblocks",
                "Balance, deposit, withdraw, buy blocks", KingdomGuiAction.BANK_PANEL));

        if (me.role() != dev.ankiesmp.dominium.core.kingdom.KingdomRole.LEADER) {
            inv.setItem(36, named(Material.OAK_DOOR, "Leave Kingdom",
                    "Members and co-leaders can leave.", KingdomGuiAction.LEAVE));
        } else {
            inv.setItem(36, named(Material.TNT, "Disband",
                    "Use /kingdom disband then /kingdom confirm",
                    KingdomGuiAction.DISBAND));
        }
        inv.setItem(44, named(Material.BARRIER, "Close", "", KingdomGuiAction.CLOSE));
        return inv;
    }

    private static ItemStack named(Material mat, String title, String lore,
                                   KingdomGuiAction action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        if (lore != null && !lore.isBlank()) {
            meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ACTION_KEY_KEY, PersistentDataType.STRING, action.name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack memberItem(KingdomMember m) {
        return named(Material.PLAYER_HEAD,
                m.role() + ": " + m.playerUuid().toString().substring(0, 8),
                "Joined " + m.joinedAt(), KingdomGuiAction.NOOP);
    }

    public static Optional<KingdomGuiAction> actionOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(ACTION_KEY_KEY, PersistentDataType.STRING);
        if (raw == null) return Optional.empty();
        try { return Optional.of(KingdomGuiAction.valueOf(raw)); }
        catch (IllegalArgumentException ex) { return Optional.empty(); }
    }

    /** Snapshot van bank/pool-state; opgebouwd op de dbExecutor thread. */
    private record BankSnapshot(Kingdom kingdom, KingdomMember member,
                                long bank, long kingdomBlocks, long personalBlocks,
                                long usedArea, List<BankOperation> recent) {
        boolean isMember() { return kingdom != null && member != null; }
        static BankSnapshot notMember() {
            return new BankSnapshot(null, null, 0, 0, 0, 0, new ArrayList<>());
        }
    }
}
