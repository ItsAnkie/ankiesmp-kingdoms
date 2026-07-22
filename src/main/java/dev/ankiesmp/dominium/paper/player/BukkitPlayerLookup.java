package dev.ankiesmp.dominium.paper.player;

import dev.ankiesmp.dominium.core.player.PlayerLookup;
import dev.ankiesmp.dominium.core.player.ResolvedPlayer;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Bukkit-adapter voor {@link PlayerLookup}. Alle methoden gebruiken
 * uitsluitend lokale server-state; er is nooit een blocking
 * Mojang-webrequest.
 *
 * <p>Cruciaal detail: {@link Server#getOfflinePlayer(UUID)} en
 * {@link Server#getOfflinePlayer(String)} retourneren altijd een
 * niet-null wrapper — ook voor een onbekend UUID of een niet-bestaande
 * naam. Alleen {@link OfflinePlayer#hasPlayedBefore()} bewijst dat er
 * daadwerkelijk een player-profile op deze server bestaat.
 */
public final class BukkitPlayerLookup implements PlayerLookup {

    private final Server server;

    public BukkitPlayerLookup(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<ResolvedPlayer> onlineByExactName(String name) {
        Objects.requireNonNull(name, "name");
        Player p = server.getPlayerExact(name);
        return p == null ? Optional.empty() : Optional.of(toResolved(p));
    }

    @Override
    public Optional<ResolvedPlayer> onlineByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        Player p = server.getPlayer(uuid);
        return p == null ? Optional.empty() : Optional.of(toResolved(p));
    }

    @Override
    public Optional<ResolvedPlayer> knownOfflineByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        OfflinePlayer op = server.getOfflinePlayer(uuid);
        if (!op.hasPlayedBefore()) return Optional.empty();
        String name = op.getName();
        if (name == null) return Optional.empty();
        return Optional.of(new ResolvedPlayer(uuid, name));
    }

    @Override
    public Collection<ResolvedPlayer> knownPlayers() {
        // Dedupliceer op UUID; getOfflinePlayers() bevat historisch al iedereen
        // die ooit heeft gejoined, maar we vermijden aannames en unioneren met
        // de huidige online-lijst.
        LinkedHashMap<UUID, ResolvedPlayer> byUuid = new LinkedHashMap<>();
        for (OfflinePlayer p : server.getOfflinePlayers()) {
            if (!p.hasPlayedBefore()) continue;
            String name = p.getName();
            if (name == null) continue;
            byUuid.putIfAbsent(p.getUniqueId(), new ResolvedPlayer(p.getUniqueId(), name));
        }
        for (Player p : server.getOnlinePlayers()) {
            byUuid.putIfAbsent(p.getUniqueId(), toResolved(p));
        }
        return new ArrayList<>(byUuid.values());
    }

    private static ResolvedPlayer toResolved(Player p) {
        return new ResolvedPlayer(p.getUniqueId(), p.getName());
    }
}
