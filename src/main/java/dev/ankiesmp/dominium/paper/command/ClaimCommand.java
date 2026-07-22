package dev.ankiesmp.dominium.paper.command;

import dev.ankiesmp.dominium.core.access.AccessLevel;
import dev.ankiesmp.dominium.core.access.PersonalClaimAccessEntry;
import dev.ankiesmp.dominium.core.access.PersonalClaimAccessService;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimExpansion;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.ClaimService;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.claim.PlacementResult;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.earning.ActivePlayEarner;
import dev.ankiesmp.dominium.core.kingdom.KingdomMember;
import dev.ankiesmp.dominium.core.kingdom.KingdomPermissionService;
import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.ClaimBlocksReadout;
import dev.ankiesmp.dominium.core.player.PlayerTargetResolver;
import dev.ankiesmp.dominium.core.player.ResolvedPlayer;
import dev.ankiesmp.dominium.core.territory.TerritoryContextCache;
import dev.ankiesmp.dominium.paper.tool.ClaimTool;
import dev.ankiesmp.dominium.paper.tool.SelectionState;
import dev.ankiesmp.dominium.paper.tool.ShovelMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Handler voor {@code /claim}. Fase 1 subcommands + fase 3
 * trust/visitor/noaccess/access + blocks.
 */
public final class ClaimCommand implements CommandExecutor, TabCompleter {

    private final ClaimTool tool;
    private final SelectionState selections;
    private final ClaimIndex index;
    private final ClaimService claimService;
    private final ClaimBlockLedger ledger;
    private final PersonalClaimAccessService accessService;
    private final PlayerTargetResolver targetResolver;
    private final ActivePlayEarner earner;
    private final TerritoryContextCache cache;
    private final KingdomService kingdomService;
    private final dev.ankiesmp.dominium.core.integrations.WorldGuardHook worldGuardHook;
    private final dev.ankiesmp.dominium.paper.config.DominiumConfig.WorldGuardIntegrationConfig wgConfig;
    private final Executor dbExecutor;

    public ClaimCommand(ClaimTool tool,
                        SelectionState selections,
                        ClaimIndex index,
                        ClaimService claimService,
                        ClaimBlockLedger ledger,
                        PersonalClaimAccessService accessService,
                        PlayerTargetResolver targetResolver,
                        ActivePlayEarner earner,
                        TerritoryContextCache cache,
                        KingdomService kingdomService,
                        dev.ankiesmp.dominium.core.integrations.WorldGuardHook worldGuardHook,
                        dev.ankiesmp.dominium.paper.config.DominiumConfig.WorldGuardIntegrationConfig wgConfig,
                        Executor dbExecutor) {
        this.tool = Objects.requireNonNull(tool);
        this.selections = Objects.requireNonNull(selections);
        this.index = Objects.requireNonNull(index);
        this.claimService = Objects.requireNonNull(claimService);
        this.ledger = Objects.requireNonNull(ledger);
        this.accessService = Objects.requireNonNull(accessService);
        this.targetResolver = Objects.requireNonNull(targetResolver);
        this.earner = Objects.requireNonNull(earner);
        this.cache = Objects.requireNonNull(cache);
        this.kingdomService = Objects.requireNonNull(kingdomService);
        this.worldGuardHook = Objects.requireNonNull(worldGuardHook);
        this.wgConfig = Objects.requireNonNull(wgConfig);
        this.dbExecutor = Objects.requireNonNull(dbExecutor);
    }

    private java.util.Optional<String> wgConflict(Player p, WorldRef world,
                                                  java.util.List<ClaimRectangle> proposed) {
        if (!wgConfig.enabled() || !wgConfig.blockClaimsInRegions()) {
            return java.util.Optional.empty();
        }
        if (p.hasPermission("dominium.claim.worldguard.bypass")) return java.util.Optional.empty();
        return worldGuardHook.firstBlockingRegion(world, proposed, wgConfig.ignoreGlobalRegion());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Player only.", NamedTextColor.RED));
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "tool" -> giveTool(player);
            case "info" -> info(player);
            case "list" -> list(player);
            case "blocks" -> blocks(player);
            case "mode" -> modeCommand(player, args);
            case "confirm" -> confirm(player);
            case "abandon" -> abandon(player);
            case "trust" -> trust(player, args);
            case "untrust" -> untrust(player, args);
            case "visitor" -> visitor(player, args);
            case "noaccess" -> noaccess(player, args);
            case "access" -> access(player);
            default -> help(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("tool", "info", "list", "blocks", "mode", "confirm", "abandon",
                    "trust", "untrust", "visitor", "noaccess", "access"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return filterPrefix(List.of("personal", "kingdom"), args[1]);
        }
        if (args.length == 2) {
            String s = args[0].toLowerCase(Locale.ROOT);
            if (s.equals("trust") || s.equals("untrust")) {
                return targetResolver.completions(args[1]);
            }
            if (s.equals("visitor")) {
                return filterPrefix(List.of("add", "remove"), args[1]);
            }
            if (s.equals("noaccess")) {
                return filterPrefix(List.of("on", "off"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("visitor")) {
            return targetResolver.completions(args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(needle))
                .toList();
    }

    private void help(Player p) {
        p.sendMessage(Component.text("Dominium /claim:", NamedTextColor.GOLD));
        p.sendMessage(Component.text("  /claim tool                     - claim shovel", NamedTextColor.GRAY));
        p.sendMessage(Component.text("  /claim info | list | blocks     - inspect", NamedTextColor.GRAY));
        p.sendMessage(Component.text("  /claim confirm | abandon        - create / delete", NamedTextColor.GRAY));
        p.sendMessage(Component.text("  /claim trust <player>           - grant trusted", NamedTextColor.GRAY));
        p.sendMessage(Component.text("  /claim untrust <player>         - revoke trusted", NamedTextColor.GRAY));
        p.sendMessage(Component.text("  /claim visitor add|remove <p>   - visitor list", NamedTextColor.GRAY));
        p.sendMessage(Component.text("  /claim noaccess on|off          - toggle no-access", NamedTextColor.GRAY));
        p.sendMessage(Component.text("  /claim access                   - show access", NamedTextColor.GRAY));
    }

    private void giveTool(Player p) {
        ItemStack existing = null;
        for (ItemStack item : p.getInventory().getContents()) {
            if (tool.isTool(item)) { existing = item; break; }
        }
        if (existing != null) {
            p.sendMessage(Component.text("You already carry a claim shovel.", NamedTextColor.YELLOW));
            return;
        }
        p.getInventory().addItem(tool.create(ShovelMode.PERSONAL_CLAIM));
        p.sendMessage(Component.text("Claim shovel granted.", NamedTextColor.GREEN));
    }

    private void info(Player p) {
        WorldRef world = new WorldRef(p.getWorld().getUID());
        var loc = p.getLocation();
        index.containing(world, loc.getBlockX(), loc.getBlockZ()).ifPresentOrElse(
                c -> p.sendMessage(Component.text(
                        "You stand in " + c.owner() + " - " + c.rect().width() + "x" + c.rect().depth(),
                        NamedTextColor.AQUA)),
                () -> p.sendMessage(Component.text("Wilderness - no claim here.", NamedTextColor.GRAY))
        );
    }

    private void list(Player p) {
        List<Claim> mine = index.all().stream()
                .filter(c -> c.owner().type() == dev.ankiesmp.dominium.core.claim.ClaimType.PERSONAL
                        && c.owner().id().equals(p.getUniqueId()))
                .toList();
        if (mine.isEmpty()) {
            p.sendMessage(Component.text("You have no personal claims.", NamedTextColor.GRAY));
            return;
        }
        p.sendMessage(Component.text("Your claims (" + mine.size() + "):", NamedTextColor.GOLD));
        for (Claim c : mine) {
            p.sendMessage(Component.text(
                    "  " + c.rect().width() + "x" + c.rect().depth()
                            + " @ (" + c.rect().minX() + "," + c.rect().minZ() + ")",
                    NamedTextColor.GRAY));
        }
    }

    private void blocks(Player p) {
        UUID playerId = p.getUniqueId();
        Instant now = Instant.now();
        dbExecutor.execute(() -> {
            var snapshot = ledger.balanceOrZero(HolderKey.player(playerId));
            long earnedToday = earner.enabled() ? earner.earnedToday(playerId, now) : 0L;
            long capRemaining = earner.enabled() ? earner.dailyCapRemaining(playerId, now) : 0L;
            var lines = ClaimBlocksReadout.linesFor(snapshot, earnedToday, capRemaining, earner.enabled());
            p.getServer().getScheduler().runTask(
                    p.getServer().getPluginManager().getPlugin("Dominium"),
                    () -> {
                        if (!p.isOnline()) return;
                        boolean first = true;
                        for (String line : lines) {
                            p.sendMessage(Component.text(line,
                                    first ? NamedTextColor.GOLD : NamedTextColor.GRAY));
                            first = false;
                        }
                    });
        });
    }

    private void confirm(Player p) {
        SelectionState.Selection sel = selections.current(p.getUniqueId()).orElse(null);
        if (sel == null || sel.second().isEmpty()) {
            p.sendMessage(Component.text("No completed selection. Set two corners first.",
                    NamedTextColor.RED));
            return;
        }
        WorldRef world = sel.world();
        ClaimRectangle rect = sel.asRectangle().orElseThrow();
        ShovelMode mode = sel.mode();
        selections.clear(p.getUniqueId());
        if (mode == ShovelMode.PERSONAL_CLAIM) {
            proceedPersonal(p, world, rect);
        } else if (mode == ShovelMode.KINGDOM_CLAIM) {
            proceedKingdom(p, world, rect);
        } else {
            p.sendMessage(Component.text("Set the shovel to personal or kingdom mode.",
                    NamedTextColor.RED));
        }
    }

    private void proceedPersonal(Player p, WorldRef world, ClaimRectangle rect) {
        UUID actor = p.getUniqueId();
        UUID key = UUID.randomUUID();
        dbExecutor.execute(() -> {
            var existing = index.findByOwner(ClaimType.PERSONAL, actor);
            var conflict = wgConflict(p, world, java.util.List.of(rect));
            if (conflict.isPresent()) {
                runSync(p, () -> p.sendMessage(Component.text(
                        "Claim rejected: overlaps WorldGuard region '" + conflict.get() + "'.",
                        NamedTextColor.RED)));
                return;
            }
            if (existing.isPresent()) {
                handleExpansion(p, existing.get(), rect, key);
                return;
            }
            var result = claimService.create(world, rect, ClaimOwner.personal(actor), key);
            if (result.isOk()) cache.invalidateClaim(result.claim().id());
            runSync(p, () -> reportCreate(p, rect, result));
        });
    }

    private void proceedKingdom(Player p, WorldRef world, ClaimRectangle rect) {
        UUID actor = p.getUniqueId();
        UUID key = UUID.randomUUID();
        dbExecutor.execute(() -> {
            var membership = kingdomService.membershipFor(actor).orElse(null);
            if (membership == null) {
                runSync(p, () -> p.sendMessage(Component.text(
                        "You are not in a kingdom. Use /kingdom create <name> first.",
                        NamedTextColor.RED)));
                return;
            }
            if (!KingdomPermissionService.allowed(membership.role(),
                    KingdomPermissionService.Action.MANAGE_KINGDOM_CLAIMS)) {
                runSync(p, () -> p.sendMessage(Component.text(
                        "You do not have permission to manage kingdom claims.",
                        NamedTextColor.RED)));
                return;
            }
            UUID kingdomId = membership.kingdomId();
            var conflict = wgConflict(p, world, java.util.List.of(rect));
            if (conflict.isPresent()) {
                runSync(p, () -> p.sendMessage(Component.text(
                        "Kingdom claim rejected: overlaps WorldGuard region '" + conflict.get() + "'.",
                        NamedTextColor.RED)));
                return;
            }
            var existing = index.findByOwner(ClaimType.KINGDOM, kingdomId);
            if (existing.isPresent()) {
                handleExpansion(p, existing.get(), rect, key);
                return;
            }
            var result = claimService.create(world, rect, ClaimOwner.kingdom(kingdomId), key);
            if (result.isOk()) cache.invalidateClaim(result.claim().id());
            runSync(p, () -> reportCreate(p, rect, result));
        });
    }

    private void handleExpansion(Player p, Claim existing, ClaimRectangle selection, UUID key) {
        var plan = ClaimExpansion.plan(existing.rect(), selection);
        switch (plan.kind()) {
            case NO_OP -> runSync(p, () -> p.sendMessage(Component.text(plan.message(),
                    NamedTextColor.YELLOW)));
            case REJECT_DETACHED, REJECT_CORNER_ONLY, REJECT_NOT_RECTANGULAR -> runSync(p, () ->
                    p.sendMessage(Component.text(
                            plan.message() + "  You already own a claim; expand it instead.",
                            NamedTextColor.RED)));
            case OK -> {
                var resizeResult = claimService.resize(existing.id(), plan.newRect(), key);
                if (resizeResult.isOk()) cache.invalidateClaim(existing.id());
                runSync(p, () -> reportResize(p, existing, resizeResult));
            }
        }
    }

    private void reportResize(Player p, Claim existing, ClaimService.ResizeResult r) {
        if (r.isOk()) {
            long delta = r.claimBlockDelta();
            p.sendMessage(Component.text(
                    "Claim expanded to " + r.newRect().width() + "x" + r.newRect().depth()
                            + " (cost delta " + delta + ").", NamedTextColor.GREEN));
        } else {
            var rej = r.rejection();
            p.sendMessage(Component.text(
                    "Expansion rejected: " + rej.kind()
                            + (rej.message() == null ? "" : " - " + rej.message()),
                    NamedTextColor.RED));
        }
    }

    private void modeCommand(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(Component.text("Usage: /claim mode <personal|kingdom>",
                    NamedTextColor.RED));
            return;
        }
        String v = args[1].toLowerCase(Locale.ROOT);
        ShovelMode target = switch (v) {
            case "personal" -> ShovelMode.PERSONAL_CLAIM;
            case "kingdom"  -> ShovelMode.KINGDOM_CLAIM;
            case "inspect"  -> ShovelMode.INSPECT;
            default -> null;
        };
        if (target == null) {
            p.sendMessage(Component.text("Unknown mode. Use personal | kingdom.",
                    NamedTextColor.RED));
            return;
        }
        ItemStack held = null;
        for (ItemStack item : p.getInventory().getContents()) {
            if (tool.isTool(item)) { held = item; break; }
        }
        if (held == null) {
            p.sendMessage(Component.text("You need a Dominium shovel first (/claim tool).",
                    NamedTextColor.RED));
            return;
        }
        tool.writeMode(held, target);
        selections.clear(p.getUniqueId());
        p.sendMessage(Component.text("Shovel mode set to " + target + ".", NamedTextColor.GREEN));
    }

    private void runSync(Player p, Runnable r) {
        var plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("Dominium");
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) r.run(); });
    }

    private void reportCreate(Player p, ClaimRectangle rect, ClaimService.CreateResult r) {
        if (r.isOk()) {
            p.sendMessage(Component.text(
                    "Claim created - " + rect.width() + "x" + rect.depth()
                            + " (" + rect.cost() + " blocks). Balance: "
                            + r.spend().balance().balance(),
                    NamedTextColor.GREEN));
        } else {
            PlacementResult rej = r.rejection();
            if (rej.kind() == PlacementResult.Kind.BLOCKED) {
                sendBlockedMessage(p);
                return;
            }
            p.sendMessage(Component.text(
                    "Claim rejected: " + rej.kind() + (rej.message() == null ? "" : " - " + rej.message()),
                    NamedTextColor.RED));
        }
    }

    /**
     * Spelervriendelijke boodschap voor de repair-blokkade. Voor admins
     * tonen we bovendien een klikbare inspect-suggestie.
     */
    private void sendBlockedMessage(Player p) {
        p.sendMessage(Component.text(
                "You currently have multiple legacy claims. "
                        + "An administrator must repair them before you can modify your claim.",
                NamedTextColor.YELLOW));
        if (p.hasPermission("dominium.claims.admin")) {
            String cmd = "/dominium claims repair " + p.getName();
            p.sendMessage(Component.text("[Inspect duplicate claims]", NamedTextColor.AQUA)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            Component.text("Click to run: " + cmd)))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(cmd)));
        }
    }

    private void abandon(Player p) {
        Claim here = personalClaimAt(p);
        if (here == null) {
            p.sendMessage(Component.text("No claim at your location.", NamedTextColor.RED));
            return;
        }
        if (!isOwner(here, p.getUniqueId())) {
            p.sendMessage(Component.text("You do not own this claim.", NamedTextColor.RED));
            return;
        }
        UUID claimId = here.id();
        UUID idempotencyKey = UUID.randomUUID();
        dbExecutor.execute(() -> {
            var result = claimService.delete(claimId, idempotencyKey);
            cache.invalidateClaim(claimId);
            p.getServer().getScheduler().runTask(
                    p.getServer().getPluginManager().getPlugin("Dominium"),
                    () -> p.sendMessage(Component.text(
                            "Claim abandoned - refunded " + result.removed().rect().cost()
                                    + " blocks. Balance: " + result.refund().balance().balance(),
                            NamedTextColor.GREEN)));
        });
    }

    // ---- fase 3 subcommands ----

    private void trust(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(msg("Usage: /claim trust <player>", NamedTextColor.RED)); return; }
        Claim here = requireOwnedClaim(p);
        if (here == null) return;
        Optional<ResolvedPlayer> target = resolveTarget(p, args[1]);
        if (target.isEmpty()) return;
        dbExecutor.execute(() -> {
            var result = accessService.trust(here, p.getUniqueId(), target.get().uuid());
            replyAccessResult(p, "Trusted " + target.get().name(), result);
        });
    }

    private void untrust(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(msg("Usage: /claim untrust <player>", NamedTextColor.RED)); return; }
        Claim here = requireOwnedClaim(p);
        if (here == null) return;
        Optional<ResolvedPlayer> target = resolveTarget(p, args[1]);
        if (target.isEmpty()) return;
        dbExecutor.execute(() -> {
            var result = accessService.untrust(here, p.getUniqueId(), target.get().uuid());
            replyAccessResult(p, "Untrusted " + target.get().name(), result);
        });
    }

    private void visitor(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(msg("Usage: /claim visitor <add|remove> <player>", NamedTextColor.RED));
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        if (!op.equals("add") && !op.equals("remove")) {
            p.sendMessage(msg("Usage: /claim visitor <add|remove> <player>", NamedTextColor.RED));
            return;
        }
        Claim here = requireOwnedClaim(p);
        if (here == null) return;
        Optional<ResolvedPlayer> target = resolveTarget(p, args[2]);
        if (target.isEmpty()) return;
        dbExecutor.execute(() -> {
            var result = op.equals("add")
                    ? accessService.visitor(here, p.getUniqueId(), target.get().uuid())
                    : accessService.unvisitor(here, p.getUniqueId(), target.get().uuid());
            replyAccessResult(p,
                    (op.equals("add") ? "Added visitor " : "Removed visitor ") + target.get().name(),
                    result);
        });
    }

    private void noaccess(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(msg("Usage: /claim noaccess <on|off>", NamedTextColor.RED)); return; }
        String v = args[1].toLowerCase(Locale.ROOT);
        boolean on = v.equals("on") || v.equals("true");
        if (!on && !(v.equals("off") || v.equals("false"))) {
            p.sendMessage(msg("Usage: /claim noaccess <on|off>", NamedTextColor.RED));
            return;
        }
        Claim here = requireOwnedClaim(p);
        if (here == null) return;
        dbExecutor.execute(() -> {
            var result = accessService.setNoAccess(here, p.getUniqueId(), on);
            replyAccessResult(p, "No Access " + (on ? "enabled" : "disabled"), result);
        });
    }

    private void access(Player p) {
        Claim here = requireOwnedClaim(p);
        if (here == null) return;
        UUID claimId = here.id();
        dbExecutor.execute(() -> {
            List<PersonalClaimAccessEntry> entries = accessService.list(here);
            boolean na = accessService.settings(here).noAccess();
            p.getServer().getScheduler().runTask(
                    p.getServer().getPluginManager().getPlugin("Dominium"),
                    () -> renderAccess(p, claimId, na, entries));
        });
    }

    private void renderAccess(Player p, UUID claimId, boolean noAccess,
                              List<PersonalClaimAccessEntry> entries) {
        if (!p.isOnline()) return;
        p.sendMessage(Component.text("Access for claim " + claimId, NamedTextColor.GOLD));
        p.sendMessage(Component.text("  No Access: " + (noAccess ? "on" : "off"),
                noAccess ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
        long trusted = entries.stream().filter(e -> e.level() == AccessLevel.TRUSTED).count();
        long visitors = entries.stream().filter(e -> e.level() == AccessLevel.VISITOR).count();
        p.sendMessage(Component.text("  Trusted (" + trusted + "):", NamedTextColor.AQUA));
        entries.stream().filter(e -> e.level() == AccessLevel.TRUSTED)
                .forEach(e -> p.sendMessage(Component.text("    " + e.playerUuid(), NamedTextColor.GRAY)));
        p.sendMessage(Component.text("  Visitors (" + visitors + "):", NamedTextColor.AQUA));
        entries.stream().filter(e -> e.level() == AccessLevel.VISITOR)
                .forEach(e -> p.sendMessage(Component.text("    " + e.playerUuid(), NamedTextColor.GRAY)));
    }

    // ---- helpers ----

    private Claim personalClaimAt(Player p) {
        WorldRef world = new WorldRef(p.getWorld().getUID());
        var loc = p.getLocation();
        return index.containing(world, loc.getBlockX(), loc.getBlockZ()).orElse(null);
    }

    private Claim requireOwnedClaim(Player p) {
        Claim here = personalClaimAt(p);
        if (here == null) {
            p.sendMessage(msg("Stand inside the claim you want to manage.", NamedTextColor.RED));
            return null;
        }
        if (!isOwner(here, p.getUniqueId())) {
            p.sendMessage(msg("You do not own this claim.", NamedTextColor.RED));
            return null;
        }
        return here;
    }

    private static boolean isOwner(Claim c, UUID playerId) {
        return c.owner().type() == dev.ankiesmp.dominium.core.claim.ClaimType.PERSONAL
                && c.owner().id().equals(playerId);
    }

    private Optional<ResolvedPlayer> resolveTarget(Player p, String rawInput) {
        Optional<ResolvedPlayer> target = targetResolver.resolve(rawInput);
        if (target.isEmpty()) {
            p.sendMessage(msg(
                    "Unknown player. The player must have joined this server before.",
                    NamedTextColor.RED));
        }
        return target;
    }

    private void replyAccessResult(Player p, String successMsg,
                                   PersonalClaimAccessService.Result result) {
        var scheduler = p.getServer().getScheduler();
        var plugin = p.getServer().getPluginManager().getPlugin("Dominium");
        String text = switch (result.kind()) {
            case OK -> successMsg + ".";
            case NOT_OWNER -> "You do not own this claim.";
            case CANNOT_TARGET_SELF -> "You cannot target yourself.";
            case CANNOT_TARGET_OWNER -> "You cannot target the claim owner.";
            case NOT_FOUND -> "That player is not on this claim's access list.";
            case REMOVED_DIFFERENT_LEVEL -> "Removed (entry was " + result.level() + ").";
        };
        NamedTextColor color = result.isOk()
                ? NamedTextColor.GREEN
                : (result.kind() == PersonalClaimAccessService.Kind.REMOVED_DIFFERENT_LEVEL
                    ? NamedTextColor.YELLOW : NamedTextColor.RED);
        scheduler.runTask(plugin, () -> { if (p.isOnline()) p.sendMessage(msg(text, color)); });
    }

    private static Component msg(String s, NamedTextColor c) { return Component.text(s, c); }
}
