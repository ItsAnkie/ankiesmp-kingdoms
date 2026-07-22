package dev.ankiesmp.dominium.core.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link PlayerLookup} voor tests. Bewust minimalistisch: één
 * lijst online + één lijst offline. Spelers moeten expliciet worden
 * geregistreerd; onbekende namen en UUIDs geven {@link Optional#empty()}
 * — precies zoals productie zich moet gedragen na de MT-003 fix.
 */
public final class FakePlayerLookup implements PlayerLookup {

    private final LinkedHashMap<UUID, ResolvedPlayer> online = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, ResolvedPlayer> known = new LinkedHashMap<>();

    public FakePlayerLookup addOnline(UUID uuid, String name) {
        ResolvedPlayer p = new ResolvedPlayer(uuid, name);
        online.put(uuid, p);
        known.put(uuid, p);
        return this;
    }

    public FakePlayerLookup addKnownOffline(UUID uuid, String name) {
        known.put(uuid, new ResolvedPlayer(uuid, name));
        return this;
    }

    @Override
    public Optional<ResolvedPlayer> onlineByExactName(String name) {
        // Contract van Server#getPlayerExact is case-sensitive per Paper's API.
        for (ResolvedPlayer p : online.values()) {
            if (p.name().equals(name)) return Optional.of(p);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ResolvedPlayer> onlineByUuid(UUID uuid) {
        return Optional.ofNullable(online.get(uuid));
    }

    @Override
    public Optional<ResolvedPlayer> knownOfflineByUuid(UUID uuid) {
        return Optional.ofNullable(known.get(uuid));
    }

    @Override
    public Collection<ResolvedPlayer> knownPlayers() {
        return new ArrayList<>(known.values());
    }

    /** Helper voor case-insensitive assertions in tests. */
    public List<String> knownNamesLowercase() {
        List<String> out = new ArrayList<>();
        for (ResolvedPlayer p : known.values()) out.add(p.name().toLowerCase(Locale.ROOT));
        return out;
    }
}
