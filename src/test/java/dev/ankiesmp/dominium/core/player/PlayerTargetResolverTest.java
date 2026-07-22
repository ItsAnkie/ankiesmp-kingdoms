package dev.ankiesmp.dominium.core.player;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressie-suite voor de MT-003 bug: {@code Bukkit.getOfflinePlayer(...)}
 * geeft ook voor onbekende input een niet-null wrapper, waardoor het
 * admin-command eerder ten onrechte grants aan spookspelers boekte.
 */
class PlayerTargetResolverTest {

    private static final UUID RENSJAM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID XTC     = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private FakePlayerLookup lookup;
    private PlayerTargetResolver resolver;

    private void setup() {
        lookup = new FakePlayerLookup()
                .addOnline(RENSJAM, "RensJAM")
                .addKnownOffline(XTC, "XTC");
        resolver = new PlayerTargetResolver(lookup);
    }

    // PT-001 — online speler op exacte naam is direct raak.
    @Test
    void onlineExactNameResolves() {
        setup();
        Optional<ResolvedPlayer> r = resolver.resolve("RensJAM");
        assertTrue(r.isPresent());
        assertEquals(RENSJAM, r.get().uuid());
        assertEquals("RensJAM", r.get().name());
    }

    // PT-002 — offline maar hasPlayedBefore is toegestaan.
    @Test
    void knownOfflineNameResolves() {
        setup();
        Optional<ResolvedPlayer> r = resolver.resolve("XTC");
        assertTrue(r.isPresent());
        assertEquals(XTC, r.get().uuid());
    }

    // PT-003 — case-insensitieve exact-match fallback via knownPlayers().
    @Test
    void caseInsensitiveExactNameResolvesKnownPlayer() {
        setup();
        assertTrue(resolver.resolve("xtc").isPresent(),
                "case-insensitive exact match is bewust ondersteund");
        assertTrue(resolver.resolve("rensjam").isPresent());
    }

    // PT-004 — gelijkende naam (prefix / substring) matcht NIET met een andere speler.
    @Test
    void similarButNotEqualNameIsRejected() {
        setup();
        // 'Rens' is een prefix van 'RensJAM' maar mag nooit RensJAM raken.
        assertTrue(resolver.resolve("Rens").isEmpty());
        assertTrue(resolver.resolve("rens").isEmpty());
        assertTrue(resolver.resolve("RensJA").isEmpty());
    }

    // PT-005 — een onbekende naam wordt geweigerd (het bug-scenario).
    @Test
    void unknownNameIsRejected() {
        setup();
        assertTrue(resolver.resolve("Rens").isEmpty());
        assertTrue(resolver.resolve("NonExistent").isEmpty());
    }

    // PT-006 — een willekeurige geldige UUID die nooit heeft gespeeld wordt geweigerd.
    @Test
    void randomValidUuidIsRejected() {
        setup();
        UUID ghost = UUID.randomUUID();
        assertTrue(resolver.resolve(ghost.toString()).isEmpty());
    }

    // PT-007 — een bekende offline UUID mag wel resolven.
    @Test
    void knownOfflineUuidResolves() {
        setup();
        Optional<ResolvedPlayer> r = resolver.resolve(XTC.toString());
        assertTrue(r.isPresent());
        assertEquals(XTC, r.get().uuid());
        assertEquals("XTC", r.get().name());
    }

    // PT-008 — online UUID resolveert via de online-path (niet via known-offline).
    @Test
    void onlineUuidResolvesFromOnlineList() {
        setup();
        Optional<ResolvedPlayer> r = resolver.resolve(RENSJAM.toString());
        assertTrue(r.isPresent());
        assertEquals("RensJAM", r.get().name());
    }

    // PT-009 — malformed UUID valt door naar naampad en wordt correct geweigerd.
    @Test
    void malformedUuidIsNotAcceptedAsAName() {
        setup();
        // Een string die eruitziet als "7e..." maar geen geldige UUID is
        // valt terug op naam-matching en vindt niets.
        assertTrue(resolver.resolve("7e1f").isEmpty());
        assertTrue(resolver.resolve("11111111-1111-1111-1111-11111111111").isEmpty(),
                "UUID met 35 tekens is geen UUID");
        assertTrue(resolver.resolve("1-2-3-4-5").isEmpty(),
                "UUID.fromString accepteert dit; wij niet");
    }

    // PT-010 — lege / whitespace input weigert.
    @Test
    void emptyInputRejected() {
        setup();
        assertTrue(resolver.resolve("").isEmpty());
        assertTrue(resolver.resolve("   ").isEmpty());
    }

    // PT-011 — completions bevatten alleen bekende spelers, prefix-gefilterd, case-insensitief.
    @Test
    void completionsOnlyIncludeKnownPlayers() {
        setup();
        List<String> all = resolver.completions("");
        assertTrue(all.contains("RensJAM"));
        assertTrue(all.contains("XTC"));

        List<String> r = resolver.completions("R");
        assertEquals(List.of("RensJAM"), r);

        List<String> lower = resolver.completions("r");
        assertEquals(List.of("RensJAM"), lower);

        List<String> none = resolver.completions("ZZZ");
        assertTrue(none.isEmpty());
    }

    // PT-012 — completions dedupliceren wanneer dezelfde speler in beide lijsten staat.
    @Test
    void completionsDeduplicate() {
        FakePlayerLookup dup = new FakePlayerLookup()
                .addOnline(RENSJAM, "RensJAM")
                .addKnownOffline(RENSJAM, "RensJAM");
        var res = new PlayerTargetResolver(dup);
        assertEquals(List.of("RensJAM"), res.completions(""));
    }
}
