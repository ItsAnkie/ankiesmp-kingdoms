package dev.ankiesmp.dominium.paper.command;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.ClaimRepairPlan;
import dev.ankiesmp.dominium.core.claim.ClaimService;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.claim.MutableClaimMutationGuard;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.core.ledger.AdminGrantAction;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.KingdomClaimBlockAdminOps;
import dev.ankiesmp.dominium.core.ledger.PostingOutcome;
import dev.ankiesmp.dominium.core.player.PlayerTargetResolver;
import dev.ankiesmp.dominium.core.player.ResolvedPlayer;
import dev.ankiesmp.dominium.core.territory.TerritoryContextCache;
import dev.ankiesmp.dominium.paper.tool.ClaimTool;
import dev.ankiesmp.dominium.paper.tool.ShovelMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Top-level {@code /dominium}-command. Ondersteunt:
 * <ul>
 *   <li>{@code /dominium version} — pluginversie tonen.</li>
 *   <li>{@code /dominium tool} — golden shovel geven.</li>
 *   <li>{@code /dominium claimblocks grant &lt;player&gt; &lt;amount&gt;} —
 *       admin-grant, achter permissie {@code dominium.claimblocks.admin}.
 *       Target wordt gevalideerd via {@link PlayerTargetResolver}:
 *       alleen online spelers of spelers met {@code hasPlayedBefore()}
 *       worden geaccepteerd. Nooit {@code Bukkit.getOfflinePlayer(...)}
 *       zonder existentiecheck.</li>
 * </ul>
 */
public final class DominiumCommand implements CommandExecutor, TabCompleter {

    public static final String PERM_CLAIMBLOCKS_ADMIN = "dominium.claimblocks.admin";
    public static final String PERM_CLAIMS_ADMIN      = "dominium.claims.admin";

    private final ClaimTool tool;
    private final AdminGrantAction adminGrantAction;
    private final PlayerTargetResolver targetResolver;
    private final KingdomClaimBlockAdminOps kingdomAdminOps;
    private final KingdomService kingdomService;
    private final ClaimService claimService;
    private final ClaimIndex claimIndex;
    private final TerritoryContextCache territoryCache;
    private final ClaimDeleteConfirmations claimDeleteConfirmations;
    private final MutableClaimMutationGuard mutationGuard;
    private final ClaimBlockLedger ledger;
    private final dev.ankiesmp.dominium.core.territory.ClaimBorderSelector borderSelector;
    private final dev.ankiesmp.dominium.paper.config.DominiumConfig.BorderParticleConfig borderCfg;
    private final dev.ankiesmp.dominium.core.bank.BankStore bankStore;
    private final dev.ankiesmp.dominium.storage.claim.AtomicClaimOps atomicClaimOps;
    private final dev.ankiesmp.dominium.core.integrations.WorldGuardHook worldGuardHook;
    private final dev.ankiesmp.dominium.paper.config.DominiumConfig.WorldGuardIntegrationConfig wgConfig;
    private final Executor dbExecutor;
    private final String pluginVersion;

    public DominiumCommand(ClaimTool tool,
                           AdminGrantAction adminGrantAction,
                           PlayerTargetResolver targetResolver,
                           KingdomClaimBlockAdminOps kingdomAdminOps,
                           KingdomService kingdomService,
                           ClaimService claimService,
                           ClaimIndex claimIndex,
                           TerritoryContextCache territoryCache,
                           ClaimDeleteConfirmations claimDeleteConfirmations,
                           MutableClaimMutationGuard mutationGuard,
                           ClaimBlockLedger ledger,
                           dev.ankiesmp.dominium.core.territory.ClaimBorderSelector borderSelector,
                           dev.ankiesmp.dominium.paper.config.DominiumConfig.BorderParticleConfig borderCfg,
                           dev.ankiesmp.dominium.core.bank.BankStore bankStore,
                           dev.ankiesmp.dominium.storage.claim.AtomicClaimOps atomicClaimOps,
                           dev.ankiesmp.dominium.core.integrations.WorldGuardHook worldGuardHook,
                           dev.ankiesmp.dominium.paper.config.DominiumConfig.WorldGuardIntegrationConfig wgConfig,
                           Executor dbExecutor,
                           String pluginVersion) {
        this.tool = Objects.requireNonNull(tool, "tool");
        this.adminGrantAction = Objects.requireNonNull(adminGrantAction, "adminGrantAction");
        this.targetResolver = Objects.requireNonNull(targetResolver, "targetResolver");
        this.kingdomAdminOps = Objects.requireNonNull(kingdomAdminOps);
        this.kingdomService = Objects.requireNonNull(kingdomService);
        this.claimService = Objects.requireNonNull(claimService);
        this.claimIndex = Objects.requireNonNull(claimIndex);
        this.territoryCache = Objects.requireNonNull(territoryCache);
        this.claimDeleteConfirmations = Objects.requireNonNull(claimDeleteConfirmations);
        this.mutationGuard = Objects.requireNonNull(mutationGuard);
        this.ledger = Objects.requireNonNull(ledger);
        this.borderSelector = Objects.requireNonNull(borderSelector);
        this.borderCfg = Objects.requireNonNull(borderCfg);
        this.bankStore = Objects.requireNonNull(bankStore);
        this.atomicClaimOps = Objects.requireNonNull(atomicClaimOps, "atomicClaimOps");
        this.worldGuardHook = Objects.requireNonNull(worldGuardHook, "worldGuardHook");
        this.wgConfig = Objects.requireNonNull(wgConfig, "wgConfig");
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "version" -> sender.sendMessage(Component.text(
                    "Dominium v" + pluginVersion, NamedTextColor.GOLD));
            case "tool" -> giveTool(sender);
            case "claimblocks" -> handleClaimBlocks(sender, args);
            case "kingdomclaimblocks" -> handleKingdomClaimBlocks(sender, args);
            case "claims" -> handleClaims(sender, args);
            case "border" -> handleBorder(sender, args);
            case "bank" -> handleBankAdmin(sender, args);
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
                sendUsage(sender);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("version", "tool", "claimblocks",
                    "kingdomclaimblocks", "claims", "border"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("border")) {
            return filterPrefix(List.of("debug"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("claimblocks")) {
            return filterPrefix(List.of("grant"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kingdomclaimblocks")) {
            return filterPrefix(List.of("grant"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("claims")) {
            return filterPrefix(List.of("inspect", "delete", "transfer", "repair"), args[1]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("claims")
                && args[1].equalsIgnoreCase("repair")) {
            return filterPrefix(List.of("merge", "auto", "delete"), args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("claimblocks")
                && args[1].equalsIgnoreCase("grant")) {
            if (!sender.hasPermission(PERM_CLAIMBLOCKS_ADMIN)) return Collections.emptyList();
            return targetResolver.completions(args[2]);
        }
        return Collections.emptyList();
    }

    private void handleKingdomClaimBlocks(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_CLAIMBLOCKS_ADMIN)) {
            sender.sendMessage(Component.text("You lack permission " + PERM_CLAIMBLOCKS_ADMIN + ".",
                    NamedTextColor.RED));
            return;
        }
        if (args.length < 4 || !args[1].equalsIgnoreCase("grant")) {
            sender.sendMessage(Component.text(
                    "Usage: /dominium kingdomclaimblocks grant <kingdom> <amount>",
                    NamedTextColor.RED));
            return;
        }
        String rawName = args[2];
        long amount;
        try { amount = Long.parseLong(args[3]); }
        catch (NumberFormatException nfe) {
            sender.sendMessage(Component.text("Amount must be a positive integer.",
                    NamedTextColor.RED));
            return;
        }
        if (amount <= 0) {
            sender.sendMessage(Component.text("Amount must be positive.", NamedTextColor.RED));
            return;
        }
        String actor = "ADMIN:" + sender.getName();
        UUID key = UUID.randomUUID();
        dbExecutor.execute(() -> {
            var k = kingdomService.findByNormalizedName(rawName);
            if (k.isEmpty()) {
                runSync(() -> sender.sendMessage(Component.text(
                        "Unknown kingdom '" + rawName + "'.", NamedTextColor.RED)));
                return;
            }
            PostingOutcome outcome;
            try {
                outcome = kingdomAdminOps.grantToKingdom(k.get().id(), amount, actor, key);
            } catch (RuntimeException ex) {
                runSync(() -> sender.sendMessage(Component.text(
                        "Grant failed: " + ex.getMessage(), NamedTextColor.RED)));
                return;
            }
            long bal = outcome.balance() == null ? -1L : outcome.balance().balance();
            Bukkit.getLogger().info("[Dominium] " + actor + " granted " + amount
                    + " claim blocks to kingdom " + k.get().displayName() + " (" + k.get().id()
                    + ") key=" + key + " kind=" + outcome.kind() + " balance=" + bal);
            runSync(() -> sender.sendMessage(Component.text(
                    "Granted " + amount + " to " + k.get().displayName() + " ("
                            + outcome.kind() + "). Balance: " + bal + ".",
                    NamedTextColor.GREEN)));
        });
    }

    private void handleBankAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dominium.bank.admin")) {
            sender.sendMessage(Component.text("You lack permission dominium.bank.admin.",
                    NamedTextColor.RED));
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("operations")) {
            sender.sendMessage(Component.text(
                    "Usage: /dominium bank operations [kingdom]", NamedTextColor.RED));
            return;
        }
        dbExecutor.execute(() -> {
            List<dev.ankiesmp.dominium.core.bank.BankOperation> ops;
            String label;
            if (args.length >= 3) {
                var k = kingdomService.findByNormalizedName(args[2]);
                if (k.isEmpty()) {
                    runSync(() -> sender.sendMessage(Component.text(
                            "Unknown kingdom '" + args[2] + "'.", NamedTextColor.RED)));
                    return;
                }
                ops = bankStore.listForKingdom(k.get().id(), 25);
                label = k.get().displayName();
            } else {
                ops = bankStore.listIncomplete();
                label = "incomplete";
            }
            runSync(() -> {
                sender.sendMessage(Component.text("Bank operations (" + label
                        + "): " + ops.size(), NamedTextColor.GOLD));
                for (var op : ops) {
                    sender.sendMessage(Component.text(
                            "  " + op.correlationId() + "  " + op.kind() + "  "
                                    + op.amountMinor() + "  " + op.state()
                                    + "  " + op.failureReason().orElse(""),
                            NamedTextColor.GRAY));
                }
            });
        });
    }

    private void handleBorder(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("debug")) {
            sender.sendMessage(Component.text("Usage: /dominium border debug", NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Player only.", NamedTextColor.RED));
            return;
        }
        var world = new dev.ankiesmp.dominium.core.common.WorldRef(p.getWorld().getUID());
        double px = p.getLocation().getX();
        double pz = p.getLocation().getZ();
        UUID viewer = p.getUniqueId();
        var candidates = borderSelector.select(world, px, pz, viewer,
                borderCfg.renderDistance(), false); // altijd zonder filter voor debug
        p.sendMessage(Component.text("onlyForeignClaims=" + borderCfg.onlyForeignClaims(),
                NamedTextColor.GOLD));
        if (candidates.isEmpty()) {
            p.sendMessage(Component.text("  (no candidate claims within render-distance="
                    + borderCfg.renderDistance() + ")", NamedTextColor.GRAY));
            return;
        }
        for (var c : candidates) {
            boolean own = borderSelector.isOwnTerritory(c, viewer);
            boolean skip = borderCfg.onlyForeignClaims() && own;
            p.sendMessage(Component.text(
                    "candidateOwner=" + c.owner().type() + ":" + c.owner().id(),
                    NamedTextColor.AQUA));
            p.sendMessage(Component.text("viewerOwnsCandidate=" + own,
                    own ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            p.sendMessage(Component.text("skipOwnTerritory=" + skip,
                    skip ? NamedTextColor.RED : NamedTextColor.GREEN));
        }
    }

    private void handleClaims(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_CLAIMS_ADMIN)) {
            sender.sendMessage(Component.text("You lack permission " + PERM_CLAIMS_ADMIN + ".",
                    NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /dominium claims <inspect|delete|transfer> ...", NamedTextColor.RED));
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        switch (op) {
            case "inspect" -> claimsInspect(sender, args);
            case "delete"  -> claimsDelete(sender, args);
            case "transfer"-> claimsTransfer(sender, args);
            case "repair"  -> claimsRepair(sender, args);
            default -> sender.sendMessage(Component.text("Unknown claims op.",
                    NamedTextColor.RED));
        }
    }

    private void claimsRepair(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /dominium claims repair <player|kingdom> [merge|auto|delete <claim-id>]",
                    NamedTextColor.RED));
            return;
        }
        String rawTarget = args[2];
        String op = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "list";
        dbExecutor.execute(() -> {
            OwnerRef owner = resolveOwnerRef(rawTarget);
            if (owner == null) {
                runSync(() -> sender.sendMessage(Component.text(
                        "Unknown player or kingdom '" + rawTarget + "'.", NamedTextColor.RED)));
                return;
            }
            List<Claim> claims = new java.util.ArrayList<>();
            claimIndex.all().forEach(c -> {
                if (c.owner().type() == owner.type && c.owner().id().equals(owner.id)) {
                    claims.add(c);
                }
            });
            switch (op) {
                case "list"   -> repairList(sender, rawTarget, owner, claims);
                case "delete" -> repairDelete(sender, args, owner, claims);
                case "merge"  -> repairMerge(sender, owner, claims);
                case "auto"   -> repairAuto(sender, owner, claims);
                default -> runSync(() -> sender.sendMessage(Component.text(
                        "Unknown repair op '" + op + "'.", NamedTextColor.RED)));
            }
        });
    }

    private void repairList(CommandSender sender, String rawTarget, OwnerRef owner,
                            List<Claim> claims) {
        long balance = ledger.balanceOrZero(HolderKey.of(
                owner.type == ClaimType.KINGDOM
                        ? dev.ankiesmp.dominium.api.ClaimBlockHolderType.KINGDOM
                        : dev.ankiesmp.dominium.api.ClaimBlockHolderType.PLAYER,
                owner.id)).balance();
        Optional<ClaimRepairPlan.Plan> plan = claims.size() >= 2
                ? Optional.of(ClaimRepairPlan.analyse(claims, balance, claimIndex))
                : Optional.empty();
        runSync(() -> {
            sender.sendMessage(Component.text(
                    "Claim repair for " + rawTarget + " (" + owner.type + "): "
                            + claims.size() + " claim(s), balance=" + balance,
                    NamedTextColor.GOLD));
            if (claims.isEmpty()) {
                sender.sendMessage(Component.text("  No claims for this owner.",
                        NamedTextColor.GRAY));
                return;
            }
            for (Claim c : claims) {
                var r = c.rect();
                sender.sendMessage(Component.text(
                        "  " + c.id() + "  world=" + c.world().id()
                                + " (" + r.minX() + "," + r.minZ() + ")..("
                                + r.maxX() + "," + r.maxZ() + ")  area="
                                + r.cost() + "  created=" + c.createdAt(),
                        NamedTextColor.GRAY));
            }
            plan.ifPresent(pl -> {
                NamedTextColor c = pl.safe() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
                sender.sendMessage(Component.text("  Plan: " + pl.kind() + " — " + pl.message(), c));
                if (pl.safe()) {
                    sender.sendMessage(Component.text(
                            "  Actions: /dominium claims repair " + rawTarget + " merge  "
                                    + "|  /dominium claims repair " + rawTarget + " auto  "
                                    + "|  /dominium claims repair " + rawTarget + " delete <claim-id>",
                            NamedTextColor.AQUA));
                } else {
                    sender.sendMessage(Component.text(
                            "  Safe merge not possible. Use /dominium claims repair "
                                    + rawTarget + " delete <claim-id> for each duplicate.",
                            NamedTextColor.AQUA));
                }
            });
        });
    }

    private void repairDelete(CommandSender sender, String[] args, OwnerRef owner,
                              List<Claim> claims) {
        if (args.length < 5) {
            runSync(() -> sender.sendMessage(Component.text(
                    "Usage: /dominium claims repair <owner> delete <claim-id>",
                    NamedTextColor.RED)));
            return;
        }
        UUID claimId;
        try { claimId = UUID.fromString(args[4]); }
        catch (IllegalArgumentException ex) {
            runSync(() -> sender.sendMessage(Component.text("Invalid claim id.",
                    NamedTextColor.RED)));
            return;
        }
        boolean belongs = claims.stream().anyMatch(c -> c.id().equals(claimId));
        if (!belongs) {
            runSync(() -> sender.sendMessage(Component.text(
                    "That claim does not belong to " + owner.type + " " + owner.id + ".",
                    NamedTextColor.RED)));
            return;
        }
        UUID key = UUID.randomUUID();
        try {
            var result = claimService.delete(claimId, key);
            territoryCache.invalidateClaim(claimId);
            maybeUnblock(owner);
            runSync(() -> sender.sendMessage(Component.text(
                    "Deleted claim " + claimId + ", refunded " + result.removed().rect().cost()
                            + " blocks to " + owner.type + " " + owner.id + ".",
                    NamedTextColor.GREEN)));
        } catch (RuntimeException ex) {
            runSync(() -> sender.sendMessage(Component.text(
                    "Delete failed: " + ex.getMessage(), NamedTextColor.RED)));
        }
    }

    private void repairMerge(CommandSender sender, OwnerRef owner, List<Claim> claims) {
        if (claims.size() < 2) {
            runSync(() -> sender.sendMessage(Component.text(
                    "Owner has fewer than 2 claims; nothing to merge.", NamedTextColor.YELLOW)));
            return;
        }
        // Pre-flight rapport: geeft nette rejection-message wanneer bijv. balance
        // ontoereikend of overlap met andere owner. De echte transactie doet
        // deze checks nogmaals binnen dezelfde JDBC-Connection.
        long balance = ledger.balanceOrZero(HolderKey.of(
                owner.type == ClaimType.KINGDOM
                        ? dev.ankiesmp.dominium.api.ClaimBlockHolderType.KINGDOM
                        : dev.ankiesmp.dominium.api.ClaimBlockHolderType.PLAYER,
                owner.id)).balance();
        var plan = ClaimRepairPlan.analyse(claims, balance, claimIndex);
        if (!plan.safe()) {
            runSync(() -> sender.sendMessage(Component.text(
                    "Merge refused: " + plan.message(), NamedTextColor.RED)));
            return;
        }
        // Atomair pad — één JDBC-transactie voor keep-claim geometry-replace +
        // delete extras + optionele refund-post + revisions. Iedere fout binnen
        // de tx rolt ALLES terug (geen compensatieboekingen).
        boolean wasBlocked = mutationGuard.isBlocked(owner.type, owner.id);
        try {
            var atomicResult = atomicClaimOps.mergeAtomic(
                    owner.type, owner.id, worldGuardHook,
                    wgConfig.ignoreGlobalRegion(),
                    dev.ankiesmp.dominium.storage.claim.AtomicClaimOps.NO_HOOK);
            if (!atomicResult.ok()) {
                runSync(() -> sender.sendMessage(Component.text(
                        "Merge refused: " + atomicResult.rejection(), NamedTextColor.RED)));
                return;
            }
            // Alleen NA commit: index/cache bijwerken + mutation-guard opheffen.
            atomicClaimOps.applyAfterMerge(atomicResult,
                    claimIndex, territoryCache, mutationGuard, owner.type, owner.id);
            runSync(() -> sender.sendMessage(Component.text(
                    "Merged " + claims.size() + " claims into " + atomicResult.keepClaimId()
                            + " (union=" + atomicResult.newGeometry().bounds().width() + "x"
                            + atomicResult.newGeometry().bounds().depth() + ")."
                            + (atomicResult.refund() > 0
                                ? " Refunded " + atomicResult.refund() + " overlapping blocks."
                                : ""),
                    NamedTextColor.GREEN)));
        } catch (RuntimeException ex) {
            if (wasBlocked) mutationGuard.block(owner.type, owner.id);
            runSync(() -> sender.sendMessage(Component.text(
                    "Merge failed: " + ex.getMessage(), NamedTextColor.RED)));
        }
    }

    private void repairAuto(CommandSender sender, OwnerRef owner, List<Claim> claims) {
        if (claims.size() < 2) {
            runSync(() -> sender.sendMessage(Component.text(
                    "Owner has fewer than 2 claims; nothing to repair.", NamedTextColor.YELLOW)));
            return;
        }
        long balance = ledger.balanceOrZero(HolderKey.of(
                owner.type == ClaimType.KINGDOM
                        ? dev.ankiesmp.dominium.api.ClaimBlockHolderType.KINGDOM
                        : dev.ankiesmp.dominium.api.ClaimBlockHolderType.PLAYER,
                owner.id)).balance();
        var plan = ClaimRepairPlan.analyse(claims, balance, claimIndex);
        if (!plan.safe()) {
            runSync(() -> sender.sendMessage(Component.text(
                    "Auto refused (not unambiguously safe): " + plan.message()
                            + " Use an explicit repair action instead.",
                    NamedTextColor.RED)));
            return;
        }
        repairMerge(sender, owner, claims);
    }

    private void maybeUnblock(OwnerRef owner) {
        // Wanneer er nog maar één claim over is voor deze owner: unblock.
        long remaining = claimIndex.all().stream()
                .filter(c -> c.owner().type() == owner.type && c.owner().id().equals(owner.id))
                .count();
        if (remaining <= 1) {
            mutationGuard.unblock(owner.type, owner.id);
        }
    }

    private OwnerRef resolveOwnerRef(String rawTarget) {
        var player = targetResolver.resolve(rawTarget);
        if (player.isPresent()) return new OwnerRef(ClaimType.PERSONAL, player.get().uuid());
        var k = kingdomService.findByNormalizedName(rawTarget);
        return k.map(kk -> new OwnerRef(ClaimType.KINGDOM, kk.id())).orElse(null);
    }

    private record OwnerRef(ClaimType type, UUID id) {}

    private void claimsInspect(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /dominium claims inspect <player-name|kingdom-name>",
                    NamedTextColor.RED));
            return;
        }
        String raw = args[2];
        dbExecutor.execute(() -> {
            List<Claim> claims = new java.util.ArrayList<>();
            Optional<ResolvedPlayer> player = targetResolver.resolve(raw);
            if (player.isPresent()) {
                claimIndex.all().forEach(c -> {
                    if (c.owner().type() == ClaimType.PERSONAL
                            && c.owner().id().equals(player.get().uuid())) claims.add(c);
                });
            } else {
                var k = kingdomService.findByNormalizedName(raw);
                if (k.isPresent()) {
                    claimIndex.all().forEach(c -> {
                        if (c.owner().type() == ClaimType.KINGDOM
                                && c.owner().id().equals(k.get().id())) claims.add(c);
                    });
                }
            }
            runSync(() -> {
                if (claims.isEmpty()) {
                    sender.sendMessage(Component.text("No claims found for '" + raw + "'.",
                            NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text("Claims for '" + raw + "' (" + claims.size() + "):",
                            NamedTextColor.GOLD));
                    for (Claim c : claims) {
                        sender.sendMessage(Component.text("  " + c.id() + " [" + c.owner().type()
                                + "] " + c.rect().width() + "x" + c.rect().depth()
                                + " @ (" + c.rect().minX() + "," + c.rect().minZ() + ")",
                                NamedTextColor.GRAY));
                    }
                }
            });
        });
    }

    private void claimsDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /dominium claims delete <claim-id> [confirm]",
                    NamedTextColor.RED));
            return;
        }
        UUID claimId;
        try { claimId = UUID.fromString(args[2]); }
        catch (IllegalArgumentException ex) {
            sender.sendMessage(Component.text("Invalid claim id.", NamedTextColor.RED));
            return;
        }
        boolean confirmToken = args.length >= 4 && args[3].equalsIgnoreCase("confirm");
        String actorId = sender.getName();
        if (!confirmToken) {
            var existing = claimIndex.get(claimId).orElse(null);
            if (existing == null) {
                sender.sendMessage(Component.text("Unknown claim id.", NamedTextColor.RED));
                return;
            }
            claimDeleteConfirmations.arm(actorId, claimId);
            sender.sendMessage(Component.text(
                    "About to delete claim " + claimId + " (owner=" + existing.owner()
                            + ", " + existing.rect().width() + "x" + existing.rect().depth()
                            + ", cost=" + existing.rect().cost() + ").",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(
                    "Type: /dominium claims delete " + claimId + " confirm  within "
                            + (claimDeleteConfirmations.ttlSeconds()) + "s.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (!claimDeleteConfirmations.consume(actorId, claimId)) {
            sender.sendMessage(Component.text(
                    "No armed delete for this claim (expired or wrong id).",
                    NamedTextColor.RED));
            return;
        }
        UUID key = UUID.randomUUID();
        dbExecutor.execute(() -> {
            var existing = claimIndex.get(claimId);
            if (existing.isEmpty()) {
                runSync(() -> sender.sendMessage(Component.text("Unknown claim id.",
                        NamedTextColor.RED)));
                return;
            }
            try {
                var result = claimService.delete(claimId, key);
                territoryCache.invalidateClaim(claimId);
                runSync(() -> sender.sendMessage(Component.text(
                        "Deleted claim " + claimId + ", refunded "
                                + result.removed().rect().cost() + " blocks to " + result.removed().owner()
                                + ". Restart the server to re-apply the single-claim index.",
                        NamedTextColor.GREEN)));
            } catch (RuntimeException ex) {
                runSync(() -> sender.sendMessage(Component.text(
                        "Delete failed: " + ex.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    private void claimsTransfer(CommandSender sender, String[] args) {
        // Minimale slice-veilige transfer: verwijder claim + nieuwe eigenaar
        // maakt zelf een nieuwe claim aan. Volledige atomair-transfer met
        // ledger-swap komt met de kingdom-bank (fase 5).
        if (args.length < 5) {
            sender.sendMessage(Component.text(
                    "Usage: /dominium claims transfer <claim-id> <personal|kingdom> <name>",
                    NamedTextColor.RED));
            return;
        }
        UUID claimId;
        try { claimId = UUID.fromString(args[2]); }
        catch (IllegalArgumentException ex) {
            sender.sendMessage(Component.text("Invalid claim id.", NamedTextColor.RED));
            return;
        }
        String targetType = args[3].toLowerCase(Locale.ROOT);
        String rawName = args[4];
        UUID key = UUID.randomUUID();
        dbExecutor.execute(() -> {
            var existing = claimIndex.get(claimId).orElse(null);
            if (existing == null) {
                runSync(() -> sender.sendMessage(Component.text("Unknown claim id.",
                        NamedTextColor.RED)));
                return;
            }
            ClaimOwner newOwner;
            if (targetType.equals("personal")) {
                var p = targetResolver.resolve(rawName);
                if (p.isEmpty()) {
                    runSync(() -> sender.sendMessage(Component.text(
                            "Unknown player. The player must have joined this server before.",
                            NamedTextColor.RED)));
                    return;
                }
                newOwner = ClaimOwner.personal(p.get().uuid());
            } else if (targetType.equals("kingdom")) {
                var k = kingdomService.findByNormalizedName(rawName);
                if (k.isEmpty()) {
                    runSync(() -> sender.sendMessage(Component.text(
                            "Unknown kingdom '" + rawName + "'.", NamedTextColor.RED)));
                    return;
                }
                newOwner = ClaimOwner.kingdom(k.get().id());
            } else {
                runSync(() -> sender.sendMessage(Component.text(
                        "Type must be 'personal' or 'kingdom'.", NamedTextColor.RED)));
                return;
            }
            // Check unieke owner-invariant.
            var conflict = claimIndex.findByOwner(
                    newOwner.type(), newOwner.id());
            if (conflict.isPresent() && !conflict.get().id().equals(claimId)) {
                runSync(() -> sender.sendMessage(Component.text(
                        "Target already owns a claim (" + conflict.get().id()
                                + "). Merge or delete first.", NamedTextColor.RED)));
                return;
            }
            try {
                // Delete oude claim (refund naar oude holder).
                claimService.delete(claimId, key);
                territoryCache.invalidateClaim(claimId);
                // Create nieuwe claim voor nieuwe eigenaar (spend van nieuwe holder).
                var create = claimService.create(existing.world(), existing.rect(), newOwner,
                        UUID.randomUUID());
                if (create.isOk()) territoryCache.invalidateClaim(create.claim().id());
                runSync(() -> sender.sendMessage(Component.text(
                        create.isOk()
                            ? "Transferred claim to new owner (new id=" + create.claim().id() + ")."
                            : "Old claim deleted, but new owner has insufficient claim blocks. "
                                    + "Grant them blocks and re-create the claim manually.",
                        create.isOk() ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
            } catch (RuntimeException ex) {
                runSync(() -> sender.sendMessage(Component.text(
                        "Transfer failed: " + ex.getMessage(), NamedTextColor.RED)));
            }
        });
    }

    private static void runSync(Runnable r) {
        Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("Dominium"), r);
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String needle = prefix.toLowerCase(java.util.Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(java.util.Locale.ROOT).startsWith(needle))
                .toList();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Dominium v" + pluginVersion, NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /dominium tool     - get a claim shovel", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /dominium version  - show plugin version", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "  /dominium claimblocks grant <player> <amount>  - admin grant (perm: "
                        + PERM_CLAIMBLOCKS_ADMIN + ")",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "  /dominium kingdomclaimblocks grant <kingdom> <amount>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "  /dominium claims <inspect|delete|transfer|repair> ...", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "  /dominium border debug", NamedTextColor.GRAY));
    }

    private void giveTool(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Player only.", NamedTextColor.RED));
            return;
        }
        p.getInventory().addItem(tool.create(ShovelMode.PERSONAL_CLAIM));
        p.sendMessage(Component.text("Claim shovel granted.", NamedTextColor.GREEN));
    }

    private void handleClaimBlocks(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /dominium claimblocks grant <player> <amount>", NamedTextColor.RED));
            return;
        }
        String op = args[1].toLowerCase();
        if (!op.equals("grant")) {
            sender.sendMessage(Component.text(
                    "Unknown claimblocks operation '" + op + "'. Expected: grant", NamedTextColor.RED));
            return;
        }
        if (!sender.hasPermission(PERM_CLAIMBLOCKS_ADMIN)) {
            sender.sendMessage(Component.text(
                    "You lack permission " + PERM_CLAIMBLOCKS_ADMIN + ".", NamedTextColor.RED));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text(
                    "Usage: /dominium claimblocks grant <player> <amount>", NamedTextColor.RED));
            return;
        }
        String targetInput = args[2];
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException nfe) {
            sender.sendMessage(Component.text(
                    "Amount must be a positive integer.", NamedTextColor.RED));
            return;
        }
        if (amount <= 0) {
            sender.sendMessage(Component.text(
                    "Amount must be positive (got " + amount + ").", NamedTextColor.RED));
            return;
        }
        String actor = "ADMIN:" + sender.getName();
        UUID idempotencyKey = UUID.randomUUID();

        dbExecutor.execute(() -> {
            AdminGrantAction.Result result;
            try {
                result = adminGrantAction.run(targetInput, amount, actor, idempotencyKey);
            } catch (RuntimeException ex) {
                Bukkit.getScheduler().runTask(
                        Bukkit.getPluginManager().getPlugin("Dominium"),
                        () -> sender.sendMessage(Component.text(
                                "Admin grant failed: " + ex.getMessage(), NamedTextColor.RED)));
                Bukkit.getLogger().warning(
                        "[Dominium] Admin grant by " + actor + " for '" + targetInput
                                + "' (" + amount + ") failed: " + ex);
                return;
            }
            switch (result.kind()) {
                case UNKNOWN_PLAYER, INVALID_AMOUNT -> {
                    String msg = result.message();
                    Bukkit.getScheduler().runTask(
                            Bukkit.getPluginManager().getPlugin("Dominium"),
                            () -> sender.sendMessage(Component.text(msg, NamedTextColor.RED)));
                    Bukkit.getLogger().info(
                            "[Dominium] Admin grant by " + actor + " for '" + targetInput
                                    + "' rejected: " + result.kind());
                }
                case GRANTED -> {
                    var outcome = result.outcome();
                    var target = result.target();
                    long balance = outcome.balance() == null ? -1L : outcome.balance().balance();
                    String consoleMsg = "[Dominium] " + actor + " granted " + amount
                            + " claim blocks to " + target.name() + " (" + target.uuid() + ") key="
                            + idempotencyKey + " kind=" + outcome.kind() + " balance=" + balance;
                    Bukkit.getLogger().info(consoleMsg);
                    Bukkit.getScheduler().runTask(
                            Bukkit.getPluginManager().getPlugin("Dominium"),
                            () -> sender.sendMessage(Component.text(
                                    "Granted " + amount + " to " + target.name() + " ("
                                            + outcome.kind() + "). Balance: " + balance
                                            + ". idempotency-key=" + idempotencyKey,
                                    NamedTextColor.GREEN)));
                }
            }
        });
    }
}
