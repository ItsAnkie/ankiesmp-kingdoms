package dev.ankiesmp.dominium.paper.hud;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.kingdom.Kingdom;
import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.core.protection.Audience;
import dev.ankiesmp.dominium.core.territory.TerritoryContext;
import dev.ankiesmp.dominium.core.territory.TerritoryContextCache;
import dev.ankiesmp.dominium.paper.config.DominiumConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;

/**
 * Onderhoudt de actionbar-HUD via één gedeelde periodieke taak. Refresht
 * ook wanneer de speler stilstaat. Leest uit de {@link
 * TerritoryContextCache}, doet geen DB-queries. MiniMessage-templates zijn
 * volledig configureerbaar.
 */
public final class TerritoryHudTask implements Runnable {

    private final Server server;
    private final TerritoryContextCache cache;
    private final KingdomService kingdomService;
    private final DominiumConfig.TerritoryHudConfig cfg;
    private final HudMessageTemplate wilderness;
    private final HudMessageTemplate personalOwner;
    private final HudMessageTemplate personalTrusted;
    private final HudMessageTemplate personalVisitor;
    private final HudMessageTemplate personalPublic;
    private final HudMessageTemplate kingdomLeader;
    private final HudMessageTemplate kingdomCoLeader;
    private final HudMessageTemplate kingdomMember;
    private final HudMessageTemplate kingdomVisitor;
    private final HudMessageTemplate kingdomPublic;

    public TerritoryHudTask(Server server, TerritoryContextCache cache,
                            KingdomService kingdomService,
                            DominiumConfig.TerritoryHudConfig cfg, Logger log) {
        this.server = Objects.requireNonNull(server);
        this.cache = Objects.requireNonNull(cache);
        this.kingdomService = Objects.requireNonNull(kingdomService);
        this.cfg = Objects.requireNonNull(cfg);
        this.wilderness     = new HudMessageTemplate(cfg.wilderness(), log);
        this.personalOwner  = new HudMessageTemplate(cfg.personalOwner(), log);
        this.personalTrusted= new HudMessageTemplate(cfg.personalTrusted(), log);
        this.personalVisitor= new HudMessageTemplate(cfg.personalVisitor(), log);
        this.personalPublic = new HudMessageTemplate(cfg.personalPublic(), log);
        this.kingdomLeader  = new HudMessageTemplate(cfg.kingdomLeader(), log);
        this.kingdomCoLeader= new HudMessageTemplate(cfg.kingdomCoLeader(), log);
        this.kingdomMember  = new HudMessageTemplate(cfg.kingdomMember(), log);
        this.kingdomVisitor = new HudMessageTemplate(cfg.kingdomVisitor(), log);
        this.kingdomPublic  = new HudMessageTemplate(cfg.kingdomPublic(), log);
    }

    @Override
    public void run() {
        if (!cfg.enabled()) return;
        for (Player p : server.getOnlinePlayers()) {
            try {
                Component msg = compose(p);
                p.sendActionBar(msg);
            } catch (RuntimeException ex) {
                // Nooit de hele taak stukmaken door één speler.
            }
        }
    }

    public Component compose(Player p) {
        var loc = p.getLocation();
        WorldRef world = new WorldRef(p.getWorld().getUID());
        TerritoryContext ctx = cache.contextAt(world, loc.getBlockX(), loc.getBlockZ(),
                p.getUniqueId());
        return composeFor(ctx, p.getServer());
    }

    public Component composeFor(TerritoryContext ctx, Server srv) {
        if (!ctx.inClaim()) {
            return wilderness.render(null, null);
        }
        Claim claim = ctx.claim().orElseThrow();
        if (claim.owner().type() == ClaimType.PERSONAL) {
            String ownerName = resolveOwnerName(srv, claim);
            return switch (ctx.audience()) {
                case PERSONAL_OWNER  -> personalOwner.render(ownerName, null);
                case TRUSTED_PLAYER  -> personalTrusted.render(ownerName, null);
                case PERSONAL_VISITOR-> personalVisitor.render(ownerName, null);
                default              -> personalPublic.render(ownerName, null);
            };
        }
        if (claim.owner().type() == ClaimType.KINGDOM) {
            String name = resolveKingdomName(claim);
            return switch (ctx.audience()) {
                case KINGDOM_LEADER   -> kingdomLeader.render(null, name);
                case KINGDOM_CO_LEADER-> kingdomCoLeader.render(null, name);
                case KINGDOM_MEMBER   -> kingdomMember.render(null, name);
                case KINGDOM_VISITOR  -> kingdomVisitor.render(null, name);
                default               -> kingdomPublic.render(null, name);
            };
        }
        return Component.empty();
    }

    private String resolveOwnerName(Server srv, Claim claim) {
        try {
            OfflinePlayer op = srv.getOfflinePlayer(claim.owner().id());
            String name = op.getName();
            return name != null ? name : claim.owner().id().toString().substring(0, 8);
        } catch (RuntimeException e) {
            return claim.owner().id().toString().substring(0, 8);
        }
    }

    private String resolveKingdomName(Claim claim) {
        Optional<Kingdom> k = kingdomService.findById(claim.owner().id());
        return k.map(Kingdom::displayName)
                .orElse("Kingdom " + claim.owner().id().toString().substring(0, 8));
    }
}
