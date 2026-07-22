package dev.ankiesmp.dominium.core.player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Dunne, testbare abstractie boven Bukkit's speler-directory. Iedere
 * methode kán zonder blocking Mojang-webrequest beantwoord worden — de
 * Bukkit-implementatie gebruikt uitsluitend lokale sources (online-lijst,
 * usercache/playerdata via {@code hasPlayedBefore()}).
 *
 * <p>Bewust <b>niet</b> gebruiken:
 * {@code Bukkit.getOfflinePlayer(String)} en
 * {@code Bukkit.getOfflinePlayer(UUID)} zonder daarna
 * {@code hasPlayedBefore()} te checken — die aanroepen geven een
 * niet-null wrapper terug voor onbekende spelers en zijn dus geen
 * geldig existentiebewijs.
 */
public interface PlayerLookup {

    /** Online speler met exact deze naam (case-sensitive, zoals Paper's contract), of empty. */
    Optional<ResolvedPlayer> onlineByExactName(String name);

    /** Online speler met dit UUID, of empty. */
    Optional<ResolvedPlayer> onlineByUuid(UUID uuid);

    /**
     * Speler met dit UUID die eerder op deze server heeft gespeeld
     * ({@code hasPlayedBefore()}). Never triggers a Mojang lookup.
     */
    Optional<ResolvedPlayer> knownOfflineByUuid(UUID uuid);

    /**
     * Alle spelers die eerder op deze server hebben gespeeld
     * (inclusief momenteel online). Wordt gebruikt voor case-insensitieve
     * naammatching en tab completion. Never triggers a Mojang lookup.
     */
    Collection<ResolvedPlayer> knownPlayers();
}
