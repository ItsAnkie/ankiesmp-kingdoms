package dev.ankiesmp.dominium.paper.command;

import dev.ankiesmp.dominium.core.kingdom.Kingdom;
import dev.ankiesmp.dominium.core.kingdom.KingdomInvite;
import dev.ankiesmp.dominium.core.kingdom.KingdomInviteService;
import dev.ankiesmp.dominium.core.kingdom.KingdomMember;
import dev.ankiesmp.dominium.core.kingdom.KingdomMembershipService;
import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.core.kingdom.KingdomVisitor;
import dev.ankiesmp.dominium.core.kingdom.KingdomVisitorService;
import dev.ankiesmp.dominium.core.player.PlayerTargetResolver;
import dev.ankiesmp.dominium.core.player.ResolvedPlayer;
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
 * {@code /kingdom ...} — lifecycle, membership, invites, visitors.
 */
public final class KingdomCommand implements CommandExecutor, TabCompleter {

    private final KingdomService kingdomService;
    private final KingdomMembershipService membershipService;
    private final KingdomInviteService inviteService;
    private final KingdomVisitorService visitorService;
    private final PlayerTargetResolver targetResolver;
    private final ConfirmationStore confirmations;
    private final Executor dbExecutor;
    private final dev.ankiesmp.dominium.paper.gui.KingdomGuiService gui;
    private final dev.ankiesmp.dominium.core.bank.KingdomBankService bankService;
    private final dev.ankiesmp.dominium.core.bank.KingdomClaimBlockPoolService poolService;

    public KingdomCommand(KingdomService kingdomService,
                          KingdomMembershipService membershipService,
                          KingdomInviteService inviteService,
                          KingdomVisitorService visitorService,
                          PlayerTargetResolver targetResolver,
                          ConfirmationStore confirmations,
                          Executor dbExecutor,
                          dev.ankiesmp.dominium.paper.gui.KingdomGuiService gui,
                          dev.ankiesmp.dominium.core.bank.KingdomBankService bankService,
                          dev.ankiesmp.dominium.core.bank.KingdomClaimBlockPoolService poolService) {
        this.kingdomService = Objects.requireNonNull(kingdomService);
        this.membershipService = Objects.requireNonNull(membershipService);
        this.inviteService = Objects.requireNonNull(inviteService);
        this.visitorService = Objects.requireNonNull(visitorService);
        this.targetResolver = Objects.requireNonNull(targetResolver);
        this.confirmations = Objects.requireNonNull(confirmations);
        this.dbExecutor = Objects.requireNonNull(dbExecutor);
        this.gui = Objects.requireNonNull(gui);
        this.bankService = Objects.requireNonNull(bankService);
        this.poolService = Objects.requireNonNull(poolService);
    }

    private void bank(Player p, String[] args) {
        UUID actor = p.getUniqueId();
        String op = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "view";
        dbExecutor.execute(() -> {
            var m = kingdomService.membershipFor(actor);
            if (m.isEmpty()) {
                runSync(p, () -> p.sendMessage(msg("You are not in a kingdom.", NamedTextColor.RED)));
                return;
            }
            if (op.equals("view")) {
                long bal = bankService.balance(m.get().kingdomId());
                runSync(p, () -> p.sendMessage(msg(
                        "Kingdom bank: "
                                + dev.ankiesmp.dominium.core.bank.Money.minorToMajor(bal),
                        NamedTextColor.GOLD)));
                return;
            }
            if (op.equals("deposit") || op.equals("withdraw")) {
                if (args.length < 3) {
                    runSync(p, () -> p.sendMessage(msg("Usage: /kingdom bank " + op + " <amount>",
                            NamedTextColor.RED)));
                    return;
                }
                long minor;
                try {
                    minor = dev.ankiesmp.dominium.core.bank.Money.majorToMinor(
                            Double.parseDouble(args[2]));
                } catch (RuntimeException ex) {
                    runSync(p, () -> p.sendMessage(msg("Invalid amount: " + ex.getMessage(),
                            NamedTextColor.RED)));
                    return;
                }
                var r = op.equals("deposit")
                        ? bankService.deposit(actor, minor)
                        : bankService.withdraw(actor, minor);
                var color = r.kind() == dev.ankiesmp.dominium.core.bank.KingdomBankService.Kind.OK
                        ? NamedTextColor.GREEN : NamedTextColor.RED;
                runSync(p, () -> p.sendMessage(msg("Bank " + op + ": " + r.kind()
                        + (r.message() == null ? "" : " - " + r.message()), color)));
                return;
            }
            runSync(p, () -> p.sendMessage(msg("Unknown bank op: " + op, NamedTextColor.RED)));
        });
    }

    private void claimblocks(Player p, String[] args) {
        UUID actor = p.getUniqueId();
        String op = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "view";
        dbExecutor.execute(() -> {
            var m = kingdomService.membershipFor(actor);
            if (m.isEmpty()) {
                runSync(p, () -> p.sendMessage(msg("You are not in a kingdom.", NamedTextColor.RED)));
                return;
            }
            if (op.equals("view")) {
                runSync(p, () -> p.sendMessage(msg(
                        "Kingdom claim blocks: (see /claim blocks for personal; kingdom via ledger)",
                        NamedTextColor.GOLD)));
                return;
            }
            if (op.equals("contribute") || op.equals("buy")) {
                if (args.length < 3) {
                    runSync(p, () -> p.sendMessage(msg(
                            "Usage: /kingdom claimblocks " + op + " <blocks>", NamedTextColor.RED)));
                    return;
                }
                long blocks;
                try { blocks = Long.parseLong(args[2]); }
                catch (NumberFormatException nfe) {
                    runSync(p, () -> p.sendMessage(msg("Amount must be a positive integer.",
                            NamedTextColor.RED)));
                    return;
                }
                var r = op.equals("contribute")
                        ? poolService.contribute(actor, blocks)
                        : poolService.buy(actor, blocks);
                var color = r.kind() == dev.ankiesmp.dominium.core.bank.KingdomClaimBlockPoolService.Kind.OK
                        ? NamedTextColor.GREEN : NamedTextColor.RED;
                runSync(p, () -> p.sendMessage(msg("Claim blocks " + op + ": " + r.kind()
                        + (r.message() == null ? "" : " - " + r.message())
                        + (r.kind() == dev.ankiesmp.dominium.core.bank.KingdomClaimBlockPoolService.Kind.OK
                            ? "  kingdom-balance=" + r.kingdomBalance() : ""),
                        color)));
                return;
            }
            runSync(p, () -> p.sendMessage(msg("Unknown claimblocks op: " + op, NamedTextColor.RED)));
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(msg("Player only.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            gui.open(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create"   -> create(p, args);
            case "info"     -> info(p, args);
            case "members"  -> members(p);
            case "invite"   -> invite(p, args);
            case "accept"   -> accept(p, args);
            case "decline"  -> decline(p, args);
            case "invites"  -> invites(p);
            case "leave"    -> leave(p);
            case "kick"     -> kick(p, args);
            case "promote"  -> promote(p, args);
            case "demote"   -> demote(p, args);
            case "transfer" -> transferArm(p, args);
            case "disband"  -> disbandArm(p);
            case "confirm"  -> confirm(p);
            case "visitor"  -> visitor(p, args);
            case "visitors" -> visitors(p);
            case "bank"     -> bank(p, args);
            case "claimblocks" -> claimblocks(p, args);
            default -> help(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("create", "info", "members", "invite", "accept",
                    "decline", "invites", "leave", "kick", "promote", "demote",
                    "transfer", "disband", "confirm", "visitor", "visitors"), args[0]);
        }
        if (args.length == 2) {
            String s = args[0].toLowerCase(Locale.ROOT);
            if (s.equals("invite") || s.equals("kick") || s.equals("promote")
                    || s.equals("demote") || s.equals("transfer")) {
                return targetResolver.completions(args[1]);
            }
            if (s.equals("visitor")) {
                return filterPrefix(List.of("add", "remove"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("visitor")) {
            return targetResolver.completions(args[2]);
        }
        return Collections.emptyList();
    }

    // ---------- lifecycle ----------

    private void create(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(msg("Usage: /kingdom create <name>", NamedTextColor.RED)); return; }
        String rawName = joinAfter(args, 1);
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var r = kingdomService.create(rawName, actor);
            runSync(p, () -> {
                if (r.isOk()) p.sendMessage(msg("Kingdom '" + r.value().displayName()
                        + "' created — you are the leader.", NamedTextColor.GREEN));
                else p.sendMessage(msg(r.message(), NamedTextColor.RED));
            });
        });
    }

    private void info(Player p, String[] args) {
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            Optional<Kingdom> k;
            if (args.length >= 2) {
                k = kingdomService.findByNormalizedName(joinAfter(args, 1));
            } else {
                var m = kingdomService.membershipFor(actor);
                k = m.flatMap(mm -> kingdomService.findById(mm.kingdomId()));
            }
            runSync(p, () -> {
                if (k.isEmpty()) { p.sendMessage(msg("No such kingdom.", NamedTextColor.RED)); return; }
                var kk = k.get();
                p.sendMessage(msg("Kingdom: " + kk.displayName(), NamedTextColor.GOLD));
                p.sendMessage(msg("  id: " + kk.id(), NamedTextColor.GRAY));
                p.sendMessage(msg("  created: " + kk.createdAt(), NamedTextColor.GRAY));
            });
        });
    }

    private void members(Player p) {
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var m = kingdomService.membershipFor(actor);
            if (m.isEmpty()) { runSync(p, () -> p.sendMessage(msg("You are not in a kingdom.",
                    NamedTextColor.RED))); return; }
            List<KingdomMember> ms = kingdomService.listMembers(m.get().kingdomId());
            runSync(p, () -> {
                p.sendMessage(msg("Members (" + ms.size() + "):", NamedTextColor.GOLD));
                for (KingdomMember mm : ms) {
                    p.sendMessage(msg("  " + mm.role() + ": " + mm.playerUuid(), NamedTextColor.GRAY));
                }
            });
        });
    }

    // ---------- invites ----------

    private void invite(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(msg("Usage: /kingdom invite <player>", NamedTextColor.RED)); return; }
        Optional<ResolvedPlayer> target = resolveTarget(p, args[1]);
        if (target.isEmpty()) return;
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var r = inviteService.invite(actor, target.get().uuid());
            runSync(p, () -> {
                if (r.isOk()) p.sendMessage(msg("Invited " + target.get().name() + ".", NamedTextColor.GREEN));
                else p.sendMessage(msg(r.message(), NamedTextColor.RED));
            });
        });
    }

    private void accept(Player p, String[] args) {
        UUID actor = p.getUniqueId();
        String rawName = args.length >= 2 ? joinAfter(args, 1) : null;
        dbExecutor.execute(() -> {
            UUID kingdomId;
            if (rawName != null) {
                var k = kingdomService.findByNormalizedName(rawName);
                if (k.isEmpty()) { runSync(p, () -> p.sendMessage(msg("No such kingdom.",
                        NamedTextColor.RED))); return; }
                kingdomId = k.get().id();
            } else {
                var list = inviteService.invitesFor(actor);
                if (list.size() != 1) {
                    runSync(p, () -> p.sendMessage(msg(
                            "Specify a kingdom: /kingdom accept <name>", NamedTextColor.RED)));
                    return;
                }
                kingdomId = list.get(0).kingdomId();
            }
            var r = inviteService.accept(actor, kingdomId);
            runSync(p, () -> {
                if (r.isOk()) p.sendMessage(msg("You joined the kingdom.", NamedTextColor.GREEN));
                else p.sendMessage(msg(r.message(), NamedTextColor.RED));
            });
        });
    }

    private void decline(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(msg("Usage: /kingdom decline <kingdom>",
                NamedTextColor.RED)); return; }
        UUID actor = p.getUniqueId();
        String rawName = joinAfter(args, 1);
        dbExecutor.execute(() -> {
            var k = kingdomService.findByNormalizedName(rawName);
            if (k.isEmpty()) { runSync(p, () -> p.sendMessage(msg("No such kingdom.",
                    NamedTextColor.RED))); return; }
            var r = inviteService.decline(actor, k.get().id());
            runSync(p, () -> {
                if (r.isOk()) p.sendMessage(msg("Invite declined.", NamedTextColor.YELLOW));
                else p.sendMessage(msg(r.message(), NamedTextColor.RED));
            });
        });
    }

    private void invites(Player p) {
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            List<KingdomInvite> list = inviteService.invitesFor(actor);
            runSync(p, () -> {
                if (list.isEmpty()) { p.sendMessage(msg("No pending invites.", NamedTextColor.GRAY)); return; }
                p.sendMessage(msg("Pending invites (" + list.size() + "):", NamedTextColor.GOLD));
                for (KingdomInvite i : list) {
                    p.sendMessage(msg("  " + i.kingdomId() + " (expires " + i.expiresAt() + ")",
                            NamedTextColor.GRAY));
                }
            });
        });
    }

    // ---------- membership ----------

    private void leave(Player p) {
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var r = membershipService.leave(actor);
            runSync(p, () -> {
                if (r.isOk()) p.sendMessage(msg("You left the kingdom.", NamedTextColor.YELLOW));
                else p.sendMessage(msg(r.message(), NamedTextColor.RED));
            });
        });
    }

    private void kick(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(msg("Usage: /kingdom kick <player>", NamedTextColor.RED)); return; }
        Optional<ResolvedPlayer> target = resolveTarget(p, args[1]);
        if (target.isEmpty()) return;
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var r = membershipService.kick(actor, target.get().uuid());
            runSync(p, () -> {
                if (r.isOk()) p.sendMessage(msg("Kicked " + target.get().name() + ".", NamedTextColor.GREEN));
                else p.sendMessage(msg(r.message(), NamedTextColor.RED));
            });
        });
    }

    private void promote(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(msg("Usage: /kingdom promote <player>", NamedTextColor.RED)); return; }
        Optional<ResolvedPlayer> target = resolveTarget(p, args[1]);
        if (target.isEmpty()) return;
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var r = membershipService.promote(actor, target.get().uuid());
            runSync(p, () -> {
                if (r.isOk()) p.sendMessage(msg("Promoted " + target.get().name() + " to CO_LEADER.",
                        NamedTextColor.GREEN));
                else p.sendMessage(msg(r.message(), NamedTextColor.RED));
            });
        });
    }

    private void demote(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(msg("Usage: /kingdom demote <player>", NamedTextColor.RED)); return; }
        Optional<ResolvedPlayer> target = resolveTarget(p, args[1]);
        if (target.isEmpty()) return;
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var r = membershipService.demote(actor, target.get().uuid());
            runSync(p, () -> {
                if (r.isOk()) p.sendMessage(msg("Demoted " + target.get().name() + " to MEMBER.",
                        NamedTextColor.YELLOW));
                else p.sendMessage(msg(r.message(), NamedTextColor.RED));
            });
        });
    }

    private void transferArm(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(msg("Usage: /kingdom transfer <player>", NamedTextColor.RED)); return; }
        Optional<ResolvedPlayer> target = resolveTarget(p, args[1]);
        if (target.isEmpty()) return;
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var m = kingdomService.membershipFor(actor);
            if (m.isEmpty()) { runSync(p, () -> p.sendMessage(msg("You are not in a kingdom.",
                    NamedTextColor.RED))); return; }
            confirmations.arm(actor, ConfirmationStore.Action.TRANSFER,
                    m.get().kingdomId(), target.get().uuid());
            long secs = confirmations.ttlMillis() / 1000L;
            runSync(p, () -> p.sendMessage(msg("Transfer leadership to " + target.get().name()
                    + " armed. Type /kingdom confirm within " + secs + "s.",
                    NamedTextColor.YELLOW)));
        });
    }

    private void disbandArm(Player p) {
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var m = kingdomService.membershipFor(actor);
            if (m.isEmpty()) { runSync(p, () -> p.sendMessage(msg("You are not in a kingdom.",
                    NamedTextColor.RED))); return; }
            confirmations.arm(actor, ConfirmationStore.Action.DISBAND, m.get().kingdomId(), null);
            long secs = confirmations.ttlMillis() / 1000L;
            runSync(p, () -> p.sendMessage(msg("Disband armed. Type /kingdom confirm within " + secs + "s.",
                    NamedTextColor.YELLOW)));
        });
    }

    private void confirm(Player p) {
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var m = kingdomService.membershipFor(actor);
            if (m.isEmpty()) { runSync(p, () -> p.sendMessage(msg("You are not in a kingdom.",
                    NamedTextColor.RED))); return; }
            UUID kid = m.get().kingdomId();
            var maybeDisband = confirmations.consume(actor,
                    ConfirmationStore.Action.DISBAND, kid);
            if (maybeDisband.isPresent()) {
                var r = kingdomService.disband(kid, actor);
                runSync(p, () -> {
                    if (r.isOk()) p.sendMessage(msg("Kingdom disbanded.", NamedTextColor.RED));
                    else p.sendMessage(msg(r.message(), NamedTextColor.RED));
                });
                return;
            }
            var maybeTransfer = confirmations.consume(actor,
                    ConfirmationStore.Action.TRANSFER, kid);
            if (maybeTransfer.isPresent()) {
                var target = maybeTransfer.get().target();
                var r = membershipService.transferLeadership(actor, target);
                runSync(p, () -> {
                    if (r.isOk()) p.sendMessage(msg("Leadership transferred.", NamedTextColor.GREEN));
                    else p.sendMessage(msg(r.message(), NamedTextColor.RED));
                });
                return;
            }
            runSync(p, () -> p.sendMessage(msg("Nothing to confirm (or confirmation expired).",
                    NamedTextColor.RED)));
        });
    }

    // ---------- visitors ----------

    private void visitor(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(msg("Usage: /kingdom visitor <add|remove> <player>", NamedTextColor.RED));
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        if (!op.equals("add") && !op.equals("remove")) {
            p.sendMessage(msg("Usage: /kingdom visitor <add|remove> <player>", NamedTextColor.RED));
            return;
        }
        Optional<ResolvedPlayer> target = resolveTarget(p, args[2]);
        if (target.isEmpty()) return;
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var r = op.equals("add")
                    ? visitorService.add(actor, target.get().uuid())
                    : visitorService.remove(actor, target.get().uuid());
            runSync(p, () -> {
                if (r.isOk()) p.sendMessage(msg(
                        (op.equals("add") ? "Visitor added: " : "Visitor removed: ")
                                + target.get().name(), NamedTextColor.GREEN));
                else p.sendMessage(msg(r.message(), NamedTextColor.RED));
            });
        });
    }

    private void visitors(Player p) {
        UUID actor = p.getUniqueId();
        dbExecutor.execute(() -> {
            var m = kingdomService.membershipFor(actor);
            if (m.isEmpty()) { runSync(p, () -> p.sendMessage(msg("You are not in a kingdom.",
                    NamedTextColor.RED))); return; }
            List<KingdomVisitor> list = visitorService.list(m.get().kingdomId());
            runSync(p, () -> {
                p.sendMessage(msg("Visitors (" + list.size() + "):", NamedTextColor.GOLD));
                for (KingdomVisitor v : list) {
                    p.sendMessage(msg("  " + v.playerUuid(), NamedTextColor.GRAY));
                }
            });
        });
    }

    // ---------- helpers ----------

    private void help(Player p) {
        p.sendMessage(msg("Dominium /kingdom:", NamedTextColor.GOLD));
        p.sendMessage(msg("  /kingdom create <name>   /kingdom info [name]", NamedTextColor.GRAY));
        p.sendMessage(msg("  /kingdom members         /kingdom leave", NamedTextColor.GRAY));
        p.sendMessage(msg("  /kingdom invite <player> /kingdom invites", NamedTextColor.GRAY));
        p.sendMessage(msg("  /kingdom accept [name]   /kingdom decline <name>", NamedTextColor.GRAY));
        p.sendMessage(msg("  /kingdom kick <player>   /kingdom promote/demote <player>", NamedTextColor.GRAY));
        p.sendMessage(msg("  /kingdom transfer <player> then /kingdom confirm", NamedTextColor.GRAY));
        p.sendMessage(msg("  /kingdom disband then /kingdom confirm", NamedTextColor.GRAY));
        p.sendMessage(msg("  /kingdom visitor <add|remove> <player>   /kingdom visitors", NamedTextColor.GRAY));
    }

    private Optional<ResolvedPlayer> resolveTarget(Player p, String raw) {
        Optional<ResolvedPlayer> target = targetResolver.resolve(raw);
        if (target.isEmpty()) {
            p.sendMessage(msg("Unknown player. The player must have joined this server before.",
                    NamedTextColor.RED));
        }
        return target;
    }

    private static String joinAfter(String[] args, int fromInclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = fromInclusive; i < args.length; i++) {
            if (i > fromInclusive) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
    }

    private static Component msg(String s, NamedTextColor c) { return Component.text(s, c); }

    private static void runSync(Player p, Runnable r) {
        var plugin = Bukkit.getPluginManager().getPlugin("Dominium");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) r.run();
        });
    }
}
