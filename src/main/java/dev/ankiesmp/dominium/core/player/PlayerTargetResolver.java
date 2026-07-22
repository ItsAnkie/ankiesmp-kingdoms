package dev.ankiesmp.dominium.core.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Vertaalt een command-string ({@code "Rens"}, {@code "7e1f...-uuid"}, ...)
 * naar een {@link ResolvedPlayer}, of geeft {@link Optional#empty()} als
 * de speler nog nooit op deze server heeft gespeeld. Nooit ledger-mutaties;
 * dit is puur een resolver.
 *
 * <p>Resolutievolgorde:
 * <ol>
 *   <li>Parse als UUID. Slaagt dat, dan alleen accepteren als
 *       {@link PlayerLookup#onlineByUuid(UUID)} of
 *       {@link PlayerLookup#knownOfflineByUuid(UUID)} een treffer heeft.</li>
 *   <li>Anders behandelen als naam. Eerst
 *       {@link PlayerLookup#onlineByExactName(String)}. Geen match,
 *       dan case-insensitief door {@link PlayerLookup#knownPlayers()}
 *       lopen en de eerste exact-op-hoofdlettergevoeligheid-na
 *       matchende naam terugvinden.</li>
 * </ol>
 *
 * <p><b>Bewuste keuze:</b> naam-matching is case-insensitief maar
 * <b>uitsluitend op exact gelijke naam</b>, niet op prefix. Zo kan
 * {@code /dominium claimblocks grant Rens ...} nooit per ongeluk
 * {@code RensJAM} raken.
 */
public final class PlayerTargetResolver {

    private final PlayerLookup lookup;

    public PlayerTargetResolver(PlayerLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    public Optional<ResolvedPlayer> resolve(String rawInput) {
        Objects.requireNonNull(rawInput, "rawInput");
        String input = rawInput.trim();
        if (input.isEmpty()) return Optional.empty();

        Optional<UUID> maybeUuid = tryParseUuid(input);
        if (maybeUuid.isPresent()) {
            UUID uuid = maybeUuid.get();
            Optional<ResolvedPlayer> online = lookup.onlineByUuid(uuid);
            if (online.isPresent()) return online;
            return lookup.knownOfflineByUuid(uuid);
        }

        Optional<ResolvedPlayer> exactOnline = lookup.onlineByExactName(input);
        if (exactOnline.isPresent()) return exactOnline;

        String needle = input.toLowerCase(Locale.ROOT);
        for (ResolvedPlayer candidate : lookup.knownPlayers()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(needle)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Tab-completion voor spelers-argumenten. Levert unieke, case-insensitief
     * gefilterde namen op basis van {@link PlayerLookup#knownPlayers()} —
     * dus alleen spelers die aantoonbaar op deze server zijn geweest.
     * Nooit online Mojang-lookups.
     */
    public List<String> completions(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        String needle = prefix.toLowerCase(Locale.ROOT);
        Collection<ResolvedPlayer> known = lookup.knownPlayers();
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (ResolvedPlayer p : known) {
            String name = p.name();
            if (name.toLowerCase(Locale.ROOT).startsWith(needle)) {
                unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
        }
        List<String> out = new ArrayList<>(unique.values());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static Optional<UUID> tryParseUuid(String s) {
        // UUID.fromString is genereus (accepteert bijvoorbeeld "1-2-3-4-5");
        // we vereisen daarom ook 36 tekens + 4 streepjes op de juiste plaats.
        if (s.length() != 36) return Optional.empty();
        if (s.charAt(8) != '-' || s.charAt(13) != '-'
                || s.charAt(18) != '-' || s.charAt(23) != '-') return Optional.empty();
        try {
            return Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
