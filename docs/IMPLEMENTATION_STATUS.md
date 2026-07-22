# Dominium — Implementation status

Legenda: `NOT_STARTED` · `IN_PROGRESS` · `IMPLEMENTED` · `TESTED` · `BLOCKED`.

`TESTED` betekent: bijbehorende automatische én/of handmatige test is
daadwerkelijk uitgevoerd en groen bevonden op deze codebase.

## Fase 0 — Foundation

| onderdeel                                | status        | notities                                                            |
|-------------------------------------------|---------------|---------------------------------------------------------------------|
| build.gradle.kts + dependencies           | IMPLEMENTED   | Java 25, Paper 26.2, HikariCP, SQLite-JDBC, JUnit 5                 |
| package-restructuur naar `dev.ankiesmp.dominium` | IMPLEMENTED | oude `AnkieSMPKingdom`-class verwijderd                       |
| plugin.yml → Dominium                     | IMPLEMENTED   |                                                                    |
| configloader                              | IMPLEMENTED   | typed `DominiumConfig` uit `config.yml`                             |
| service registry (constructor DI)         | IMPLEMENTED   | `DominiumServices` bootstrap                                        |
| database bootstrap (HikariCP + SQLite)    | IMPLEMENTED   | WAL, foreign_keys=ON                                                |
| versioned migrations                      | TESTED        | expliciete `MigrationRegistry` + `ClasspathMigrationSource`; jar-classloader-test; upgrade V1→V2/V3 en V2→V3 |
| claim-block ledger (append-only)          | IMPLEMENTED   | idempotency + gematerialiseerd saldo                                |
| claim-block invarianten (property tests)  | TESTED        | JUnit 5, zie `ClaimBlockLedgerTest`                                 |
| bank operation state-machine scaffolding  | IMPLEMENTED   | states + entiteit, nog geen Vault-adapter                           |
| plugin start/stop clean                   | TESTED        | 4 bootstrap-tests in `DominiumServicesBootstrapTest` (headless start, restart, mislukte migratie sluit Hikari + executor, close deterministisch) |
| exception-safe bootstrap                  | TESTED        | BS-003: bij failed migration wordt Hikari-pool gesloten én executor terminated voordat exception naar buiten komt |
| SQLite-driver (unbundled, Paper-provided) | TESTED        | `compileOnly` + `testImplementation` in build.gradle.kts; jar bevat geen `org/sqlite/*`; `DominiumCore` logt zowel provenance (classloader + code-source) als `DriverName/Version`. Smoketest: Paper's `SQLite JDBC 3.49.1.0` wint parent-first — zie `docs/DATABASE.md` §4 |

**Gate fase 0:**
- [x] `gradlew clean build --no-build-cache` groen (54 tests PASSED, jar
      gebouwd als `dominium-0.1.0-SNAPSHOT.jar`, shaded, ~300 KB).
- [x] Ledger + migratie + bootstrap tests groen — zie tabel hierboven
      (7 + 8 + 4 = 19 tests op storage/bootstrap; 54 in totaal).
- [x] Jar-inhoud geverifieerd: `db/migrations/V1..V3` aanwezig,
      HikariCP gerelocateerd naar `dev.ankiesmp.dominium.libs.hikari.*`,
      **geen** `org/sqlite/*` in de jar (bewust unbundled — Paper levert
      de driver), `plugin.yml` correct met `main`,
      `${version}`/`${description}` geëxpandeerd.
- [x] Paper 26.2-62 smoketest uitgevoerd (`run/logs/2026-07-21-4.log.gz`
      + `run/logs/latest.log`) — zie MT-002 in `docs/TEST_PLAN.md`.
      Bevestigd: driver-provenance-log + `SQLite JDBC 3.49.1.0`,
      eerste start past V1+V2+V3 toe, `/dominium version` + `plugins`
      werken, clean shutdown sluit HikariCP-pool én db-executor,
      tweede start slaat V1/V2/V3 correct over. Consolelogs bevatten
      geen mojibake meer sinds `filteringCharset = "UTF-8"` en de
      ASCII-vervanging in operator-facing logregels.
- [ ] In-game gameplay-smoketest (`/claim tool` daadwerkelijk gebruiken
      op een levende Paper-server met speler) — nog te doen; de huidige
      smoketest bevestigt alleen bootstrap/shutdown-pad, niet
      shovel-interactie of listeners.

**Bekende omgevingsvereisten:**
- JDK 25 is verplicht (bewuste keuze in `build.gradle.kts`, matcht Paper 26.2).
- Op deze machine is Zulu 25 gevonden onder `C:\Program Files\Zulu\zulu-25`;
  Gradle detecteert het pas wanneer `JAVA_HOME` erop wijst **of** met
  `-Dorg.gradle.java.installations.paths="C:\Program Files\Zulu\zulu-25"`.

## Fase 0.5 — Initial player claim-block grant

Ontstond uit de gameplay-smoketest: nieuwe spelers hadden 0 claim blocks
en konden niets claimen. Werd toegevoegd vóór fase 3 zodat de bestaande
`/claim`-flow onder alle omstandigheden gebruikt kan worden.

| onderdeel                                     | status        | notities                                                        |
|-----------------------------------------------|---------------|-----------------------------------------------------------------|
| `claim-blocks.starting-balance` in `config.yml` | IMPLEMENTED | default `1000`; `0` schakelt de feature uit; validatie op `>= 0` |
| `InitialClaimBlockGrant` (pure core service)  | TESTED        | 8 tests in `InitialClaimBlockGrantTest`, incl. concurrent join   |
| Deterministische idempotency key              | TESTED        | `UUID.nameUUIDFromBytes("initial-player-grant:" + playerUuid)`; IG-007 |
| `InitialGrantListener` op `PlayerJoinEvent`   | IMPLEMENTED   | async naar db-executor, notificatie sync terug naar mainthread   |
| `/claim blocks`                               | IMPLEMENTED   | leest `balanceOrZero` via ledger; formatter in `ClaimBlocksReadout` (TESTED) |
| `/dominium claimblocks grant <player> <n>`    | IMPLEMENTED   | permissie `dominium.claimblocks.admin` (default op); positieve bedragen; random idempotency key; audit-actor `ADMIN:<name>`; console + speler feedback |
| `ClaimBlockAdminOps` (pure core service)      | TESTED        | 5 tests in `ClaimBlockAdminOpsTest`                              |
| `PlayerLookup` + `PlayerTargetResolver`       | TESTED        | 12 tests in `PlayerTargetResolverTest`; MT-003 regressie: `Bukkit.getOfflinePlayer(...)` mag nooit meer als bewijs van bestaan gelden |
| `AdminGrantAction` (resolver ↔ ledger binding)| TESTED        | 8 tests in `AdminGrantActionTest`; afgewezen targets produceren aantoonbaar geen ledger- of balance-rij |
| `BukkitPlayerLookup` (Paper-adapter)          | IMPLEMENTED   | dun; alle gedragsregels zitten in de pure resolver + adapter delegeert 1-op-1 |
| Tab completion op `/dominium claimblocks grant` | TESTED      | via `PlayerTargetResolver#completions`; alleen bekende spelers, case-insensitief prefix |

**Gate fase 0.5:**
- [x] `gradlew clean build --no-build-cache` groen (90 tests PASSED — 54 bestaand + 16 fase 0.5 + 20 MT-003 fix).
- [x] Idempotency dekt: reconnect, restart, dubbele join, concurrent init, retry na DB-fout.
- [x] Startsaldo 0 → geen ledger-mutatie, `GrantOutcome.DISABLED`.
- [x] Bestaand saldo zonder `INITIAL_GRANT`-entry ontvangt alsnog exact één startgrant (IG-006, bewust gedocumenteerd — zie `docs/DATABASE.md` §5).
- [x] Admin-command target-resolutie is structureel gefixt: onbekende namen én willekeurige UUIDs worden afgewezen zonder ledgermutatie (PT-001..PT-012, AG-001..AG-008). Documented decision: case-insensitieve **exacte** naam-match; **geen** prefix/substring-match zodat `Rens` nooit per ongeluk `RensJAM` raakt. Zie `docs/DATABASE.md` §5.
- [ ] Live-server hertest MT-003: gebruiker moet in-game bevestigen dat de bug niet reproduceerbaar is voordat deze regel op TESTED gaat. Blijft `MT` in `docs/TEST_PLAN.md` totdat die run gebeurt.

## Fase 1 — Claims en golden shovel

| onderdeel                                     | status        | notities                                                        |
|-----------------------------------------------|---------------|-----------------------------------------------------------------|
| `ClaimRectangle` (inclusief, cost=`w*d`)      | TESTED        | 7 tests in `ClaimRectangleTest`                                 |
| `Claim`, `ClaimOwner`, `ClaimType`            | IMPLEMENTED   | immutable value objects, geen Bukkit-imports                    |
| Chunk-bucket spatial index                    | TESTED        | 4 tests in `ClaimIndexTest`                                     |
| `PlacementValidator` (size/overlap/buffer)    | TESTED        | 6 tests in `PlacementValidatorTest`                             |
| `ClaimService` create/resize/delete + ledger  | TESTED        | 5 tests in `ClaimServiceTest`, atomair via ledger + store       |
| Claim SQL-repo + revision-log (V3-migratie)   | IMPLEMENTED   | vindt loadAll bij startup; TESTED indirect via ClaimServiceTest |
| Golden shovel PDC-tag + `ClaimTool`           | IMPLEMENTED   | markerkey `dominium:claim_tool`, mode in PDC                    |
| `SelectionState` (per-speler A/B corners)     | IMPLEMENTED   | in-memory, wordt bij quit gewist                                |
| `ClaimToolListener`                           | IMPLEMENTED   | L-click inspect · R-click set A/B · sneak+R cycle mode          |
| `/claim tool|info|list|confirm|abandon`       | IMPLEMENTED   | plus `/dominium tool|version`                                   |
| Kingdom modus (`KINGDOM_CLAIM`)               | BLOCKED       | wacht op fase 4 (kingdom lifecycle); shovel weigert nu vriendelijk |
| Particles / map / HUD                         | NOT_STARTED   | fase 3                                                          |
| Kingdom-adjacency + outpost + minimum-buffer  | NOT_STARTED   | fase 4/5                                                        |

**Gate fase 1:**
- [x] `gradlew clean build` groen (28 tests PASSED).
- [x] Personal-claim create/resize/shrink/delete corrigeren ledger exact.
- [ ] Handmatige testserver-check golden shovel (MT — nog niet uitgevoerd).

## Fase 2 — Protecties

| onderdeel                                     | status        | notities                                                        |
|-----------------------------------------------|---------------|-----------------------------------------------------------------|
| `Flag` enum (§10.2 volledige set)             | IMPLEMENTED   | 50 flags gedefinieerd; niet elke flag heeft in fase 2 een listener |
| `Audience` + `Decision` enums                 | IMPLEMENTED   |                                                                 |
| `FlagDefaults.standard()`                     | TESTED        | 6 tests in `FlagDefaultsTest`                                   |
| `AudienceResolver` interface                  | IMPLEMENTED   | fase 2 impl: `PersonalOwnerAudienceResolver` (owner vs public)   |
| `ProtectionService` precedence + deny-wins    | TESTED        | 7 tests in `ProtectionServiceTest`                              |
| `ProtectionGuard` (listener-helper)           | IMPLEMENTED   |                                                                 |
| `BuildListener` (place/break/bucket)          | IMPLEMENTED   | MT nodig op live-server (listeners niet in unit-suite)          |
| `InteractListener` (doors/gates/buttons/…)    | IMPLEMENTED   | MT nodig                                                        |
| `ContainerListener` (InventoryOpenEvent)      | IMPLEMENTED   | dekt alle `InventoryHolder` van block-states (chest/barrel/hopper/dispenser/etc.) |
| `PvPListener` (PVP + pet + animal)            | IMPLEMENTED   | inclusief projectile-shooter-resolve                            |
| `EnvironmentListener` (cross-border)          | IMPLEMENTED   | piston, liquid, fire, TNT/creeper/other explode, dispenser, mob-grief, farmland trample |
| `EntityListener` (hangings/vehicles/entities) | IMPLEMENTED   | item frame, armor stand, painting, minecart/boat, villager      |

**Gate fase 2:**
- [x] `gradlew clean build` groen (41 tests PASSED).
- [x] `FlagDefaults` + `ProtectionService` precedence tests inclusief deny-wins en safe-default.
- [ ] Volledige griefmatrix op live Paper 26.2 server (MT — nog niet uitgevoerd).

**Bewuste keuze:** de listeners nemen deel via `EventPriority.LOW` en
returnen zo vroeg mogelijk (wilderness = één bucket-lookup + shared
constante). Er is nog geen per-speler territory-cache; die komt in
fase 3 samen met de HUD.

## Fase 3 — Visitors, No Access, HUD, activiteit

| onderdeel                                     | status        | notities                                                              |
|-----------------------------------------------|---------------|-----------------------------------------------------------------------|
| V4-migratie + `MigrationRegistry`             | TESTED        | `personal_claim_access`, `personal_claim_settings`, `player_activity_state`, `player_earning_state`; FK naar `claims(id) ON DELETE CASCADE`; unique per (claim, player); CHECK op level. |
| `PersonalClaimAccessStore` (SQL)              | TESTED        | atomair upsert (DELETE + INSERT in één tx) → visitor↔trusted promotie   |
| `PersonalClaimAccessService` (pure core)      | TESTED        | 8 tests: alleen owner, geen self/owner target, NOT_FOUND, restart-safe |
| `Audience.PERSONAL_VISITOR` + `FlagDefaults`  | TESTED        | visitor-invariant centraal; nooit container/build/bucket/redstone/pvp  |
| `PersonalClaimAudienceResolver`               | TESTED        | precedence: owner > trusted > visitor > public                        |
| `ProtectionService` No Access overlay         | TESTED        | PUBLIC krijgt hard deny op alle flags voor claims met noAccess         |
| `TerritoryContextCache` (gedeeld)             | TESTED        | 7 tests: hit/miss, per-player invalidate, per-claim sweep, TTL, clear, contextAt |
| `ClaimCommand` trust/untrust/visitor/noaccess/access | IMPLEMENTED | + tab completion via `PlayerTargetResolver` (MT-003 pad)               |
| `TerritoryHudListener` (actionbar + no-access enforce) | IMPLEMENTED | throttled per refresh-millis; move-cancel via `setTo(from)` |
| `ActivityTracker` + listener                  | TESTED        | 6 tests: accrue, AFK-reset, drain, quit, reconnect, multi-player       |
| `PlayerActivityStore` (SQL, batched)          | IMPLEMENTED   | batched upsert, geen writes per event                                  |
| `ActivePlayEarner` + `EarningStore`           | TESTED        | 8 tests: cap-atomair, UTC-day, idempotent slot, admin-grant telt niet mee |
| `InactivityExpiryService` (pure core)         | TESTED        | 6 tests: min-age, offline+inactive expire, online skip, kingdom skip, disabled, postDeleteHook |
| Cache-invalidation hooks                      | IMPLEMENTED   | access-service, expiry postDeleteHook, ClaimCommand na create/delete   |

**Gate fase 3:**
- [x] `gradlew clean build --no-build-cache` groen (130 tests PASSED — 90 bestaand + 40 nieuw).
- [x] Alle mutaties invalidateeren de cache pas ná store-transactie.
- [x] Hot listeners doen geen DB-query (route via `TerritoryContextCache`).
- [x] Adminclaims/kingdomclaims worden nooit door persoonlijke expiry aangeraakt.
- [ ] Live MT: in-game verificatie van trust/untrust/visitor/no-access/HUD/movement, earning-cap over UTC-dagwisseling, en /claim blocks output — nog uit te voeren.

## Fase 4 — Kingdom lifecycle & rollen

| onderdeel                                              | status        | notities                                                            |
|--------------------------------------------------------|---------------|---------------------------------------------------------------------|
| V5-migratie + `MigrationRegistry`                      | TESTED        | `kingdoms`, `kingdom_members` (partial unique idx op LEADER), `kingdom_invites`, `kingdom_visitors`; FK ON DELETE CASCADE |
| `Kingdom`/`KingdomMember`/`KingdomInvite`/`KingdomVisitor` records | IMPLEMENTED | immutable, geen Bukkit-types                              |
| `KingdomName` (validatie + normalisatie)               | IMPLEMENTED   | safe-charset, min/max lengte, case-insensitive dedupe               |
| `KingdomStore` + `SqlKingdomStore`                     | IMPLEMENTED   | createWithLeader / acceptInvite / transferLeadership / disband draaien elk in één DB-transactie |
| `KingdomService` (create/disband/lookup)               | TESTED        | 10 tests in `KingdomLifecycleTest`                                  |
| `KingdomMembershipService` (leave/kick/promote/demote/transfer) | TESTED | 7 tests in `KingdomMembershipServiceTest`                          |
| `KingdomInviteService`                                 | TESTED        | 9 tests in `KingdomInviteServiceTest` (expiry, dup, cross-kingdom race) |
| `KingdomVisitorService`                                | TESTED        | 5 tests in `KingdomVisitorServiceTest` incl. visitor→member atomic remove |
| `KingdomPermissionService`                             | TESTED        | 4 tests: leader/co-leader/member matrix                             |
| `KingdomCache` + invalidator hooks                     | TESTED        | 3 tests: hit/miss, invalidatePlayer, invalidateKingdom              |
| `KingdomAwareAudienceResolver` (composeert PersonalClaimAudienceResolver) | TESTED | 3 tests: precedence leader>co>member>visitor>public, other-kingdom→PUBLIC, personal delegate |
| Audience `KINGDOM_LEADER/CO_LEADER/MEMBER/VISITOR`     | IMPLEMENTED   | via bestaande `FlagDefaults`-rows; `KINGDOM_VISITOR` invariant stond al centraal in fase 2 |
| `/kingdom` command + confirm-flow + tab completion     | IMPLEMENTED   | subcommands: create/info/members/invite/accept/decline/invites/leave/kick/promote/demote/transfer/disband/confirm/visitor/visitors |
| `ConfirmationStore` (per-actor, TTL, action+kingdom-bound) | TESTED    | 4 tests: match, mismatch, expiry, clear                             |
| HUD-uitbreiding voor kingdomterritory                  | IMPLEMENTED   | `<KingdomName> - Leader/Co-Leader/Member/Visitor`; no-DB in hot path |
| `ClaimBorderGeometry` (pure geometrie)                 | TESTED        | 9 tests: distance in/out, trigger-range, spacing, budget-cap, unieke hoeken, config-validatie |
| `ClaimBorderSelector` (own-territory filter incl. kingdommembership) | TESTED | 4 tests                                                          |
| `ClaimBorderParticleTask` (Bukkit-renderer)            | IMPLEMENTED   | één gedeelde task; `HAPPY_VILLAGER`; per-speler budget; terrain-following met chunk-loaded check; fallback naar player-Y |
| `plugin.yml` `/kingdom` command + usage                | IMPLEMENTED   |                                                                     |

**Bewuste keuzes:**
- Kingdomclaims (`KINGDOM_CLAIM` shovel-modus) blijven bewust geblokkeerd tot fase 5 de claimpool + kingdombank levert. Er is geen tijdelijke workaround.
- Audience `ALLY` blijft voorbereid maar wordt in fase 4 nog niet toegekend; fase 6 (diplomacy) vult dat pad in.

**Gate fase 4:**
- [x] `gradlew clean build --no-build-cache` groen (188 tests PASSED — 130 vorige + 58 nieuw).
- [x] `Bukkit.getOfflinePlayer(...)`-anti-pattern nergens opnieuw geïntroduceerd (alles via `PlayerTargetResolver`).
- [x] Alle mutaties invalideren zowel `TerritoryContextCache` als `KingdomCache` waar relevant.
- [x] Persoonlijke claims blijven onaangeraakt bij kingdom-join (persoonlijk saldo + persoonlijke claims blijven bestaan).
- [ ] Live-server MT: `/kingdom create/invite/accept/kick/promote/transfer/disband/visitor`, HUD op kingdomterritory, border-particles (Happy Villager) rondom vreemde persoonlijke claims. Blijft **open** tot gebruikersbevestiging.

## Fase 4.5 — Live-fix slice (particles, HUD, kingdom ownership, één-claim, GUI)

Deze slice ontstond uit een echte Paper-live-run. De items uit die run zijn verwerkt:

| item                                                  | status        | notities                                                              |
|-------------------------------------------------------|---------------|-----------------------------------------------------------------------|
| Border-particles live bug                             | IMPLEMENTED   | Root cause: `only-foreign-claims: true` filterde tester's eigen claim. Fix: default `false`, compile-time `Particle.HAPPY_VILLAGER`, debug-log achter `debug: true`. Regressie in `ClaimBorderParticleDefaultTest`. |
| Constante actionbar-HUD                               | TESTED        | Nieuwe `TerritoryHudTask` (scheduled, 20t default). Templates volledig configureerbaar via MiniMessage; malformed template zet plugin NIET uit — fallback via `HudMessageTemplate` + 3 tests. |
| `/claim mode <personal\|kingdom>`                     | IMPLEMENTED   | Wijzigt PDC-mode op de bestaande shovel; nooit stille fallback naar personal. |
| Kingdom-claim ownership                               | IMPLEMENTED   | `/claim confirm` in kingdom-mode gebruikt `ClaimOwner.kingdom(...)` + `HolderKey.kingdom(...)`. Permissiecheck via `KingdomPermissionService.Action.MANAGE_KINGDOM_CLAIMS`. Geen fake-bank; kingdom heeft eigen ledger-holder. |
| `/dominium kingdomclaimblocks grant <kingdom> <n>`    | TESTED        | 4 tests in `KingdomClaimBlockAdminOpsTest` (kingdom-holder, audit fields, idempotency, non-positive). Development/admin-only, achter `dominium.claimblocks.admin`. |
| Één claim per PERSONAL / KINGDOM owner                | TESTED        | V6 partial unique index via `SingleClaimIndexInstaller` met preflight (zie fase 4.6); service-layer defensive `DUPLICATE_OWNER` check ongeacht DB-index (`SC-001`, `SC-002`, `MG-003`); `ClaimIndex#findByOwner` (`SC-003`); `DuplicateOwnerAudit` (`DA-001..DA-003`). |
| Rechthoek-expansion logica                            | TESTED        | Pure `ClaimExpansion.plan`; 6 tests: NO_OP / DETACHED / CORNER_ONLY / edge-adjacency OK / L-shape / overlapping-OK. `/claim confirm` routeert bestaande owner naar `claimService.resize` met de union-rect. |
| Kingdom chest-GUI                                     | IMPLEMENTED   | `/kingdom` zonder args opent GUI. Custom `KingdomGuiHolder`; alle clicks via PDC-tagged actions; `KingdomGuiListener` cancelt drags/click default; leave/disband/members/visitors/invites-panels. Alle acties delegeren naar dezelfde core-services als de commands. |
| Admin claim tools                                     | IMPLEMENTED   | `/dominium claims inspect <player\|kingdom>`, `delete <claim-id>`, `transfer <claim-id> <personal\|kingdom> <name>` (delete+create met unique-owner check). Achter `dominium.claims.admin`. |

**Bewuste keuzes:**
- Kingdom-claim betalingen gebruiken de bestaande append-only ledger op een KINGDOM-holder. Geen tijdelijke fake-bank of leader-saldo-workaround. Fase 5 vervangt het admincommand door de volledige claimpool + kingdombank.
- V6 gebruikt een SQLite partial unique index; ADMIN-claims blijven bewust ongelimiteerd.
- Startup-`DuplicateOwnerAudit` logt alleen; geen automatische merge of delete.

**Correctie op eerdere claim:** de eerdere status "bestaande databases met duplicaten crashen niet; DuplicateOwnerAudit waarschuwt" was feitelijk onjuist. De audit draaide pas ná de migraties, dus V6 crashte al met `SQLITE_CONSTRAINT_UNIQUE` op databases met bestaande duplicaten uit oudere builds. Zie fase 4.6 voor de structurele fix.

**Gate slice:**
- [x] `gradlew clean build --no-build-cache` groen (210 tests PASSED — 188 vorige + 22 nieuw).
- [x] Alle mutaties invalideren `TerritoryContextCache` + `KingdomCache`.
- [x] Geen Bukkit-code in core-tests; Adventure/MiniMessage als testImplementation toegevoegd voor pure template-testen.
- [ ] Live-MT: border-particles zichtbaar met default config, actionbar constant, `/claim mode kingdom` gevolgd door create, GUI-flow. Blijft **open** tot gebruikersbevestiging.

## Fase 4.6 — V6 upgrade-path fix (CLAIM_REPAIR_MODE)

Live gevonden bug: op databases uit fase 3/4 met meerdere claims per owner faalde V6 met `SQLITE_CONSTRAINT_UNIQUE` en de plugin crashte vóór welke tooling dan ook beschikbaar was.

| onderdeel                                     | status        | notities                                                            |
|-----------------------------------------------|---------------|---------------------------------------------------------------------|
| V6 uit `MigrationRegistry` verwijderd         | IMPLEMENTED   | SQL leeft nu in `SingleClaimIndexInstaller`; MigrationRunner blijft strikt schema-only |
| `SingleClaimIndexInstaller` (preflight)       | TESTED        | 7 tests (V6-001..V6-007): clean/single/duplicate-personal/duplicate-kingdom/after-repair/idempotent/other-owners-not-flagged |
| `ClaimMutationGuard` + repair-mode wire-up    | TESTED        | 4 tests (MG-001..MG-004): blocked-create, blocked-resize, defensive `DUPLICATE_OWNER` zonder DB-index, ALLOW_ALL permissive |
| `ClaimService.create/resize` guard-checks     | TESTED        | in `MG-*` + `SC-*`                                                  |
| `ClaimService.create` defensive `findByOwner` | TESTED        | `MG-003`, `SC-001`, `SC-002` — service weigert tweede claim ongeacht DB-index |
| `DominiumCore` bootstrap flow                 | IMPLEMENTED   | logt duidelijke conflict-lijst; zet `claimRepairMode()` op services; guard wordt in `ClaimService` geïnjecteerd |
| `/dominium claims delete` confirm-flow        | TESTED        | 3 tests in `ClaimDeleteConfirmationsTest`; arm+confirm binnen 30s, mismatch en expiry blokkeren |
| `SqlClaimRepository.delete` één transactie    | IMPLEMENTED   | `DELETE FROM claims` cascadeert naar revisions/access via FK ON DELETE CASCADE; refund via ledger.post met idempotency-key als durable audit-trace |
| Repair-mode start-up warning banner           | IMPLEMENTED   | in `DominiumPlugin.onEnable` — geen SQL-stacktrace, wel duidelijke actionable log |

**Gate fase 4.6:**
- [x] `gradlew clean build --no-build-cache` groen (224 tests PASSED — 210 vorige + 14 nieuw).
- [x] V6 wordt nooit als toegepast gemarkeerd zolang conflicten bestaan (`V6-003`, `V6-004`).
- [x] Na `/dominium claims delete <id> confirm` + restart wordt de index alsnog geplaatst (`V6-005`).
- [x] Tweede claim voor dezelfde owner wordt in repair-mode ook zonder DB-index door de service geweigerd (`MG-003`).
- [x] Andere owners zonder conflict blijven ongeblokkeerd (`MG-001`, `V6-007`).
- [ ] Live-MT: op de bestaande dev-DB starten in CLAIM_REPAIR_MODE, admin-flow doorlopen, restart, V6 toegepast. Blijft **open** tot gebruikersbevestiging.

## Fase 5 — Claimpool en kingdom bank

`NOT_STARTED`.

## Fase 6 — Diplomatie

`NOT_STARTED`.

## Fase 7 — War proposals & stake reservations

`NOT_STARTED`.

## Fase 8 — Scheduling & preparation

`NOT_STARTED`.

## Fase 9 — Raid engine

`NOT_STARTED`.

## Fase 10 — Rollback, outcome, conquest

`NOT_STARTED`.

## Fase 11 — Progressie, admin, integraties

`NOT_STARTED`.

## Fase 12 — Releasehardening

`NOT_STARTED`.

## Hervattingsinstructie

Wanneer een volgende sessie hier aanhaakt: begin met fase 1
(claimindex + golden shovel PDC + selection state + block-based rectangle
preview + `/claim` commands). Volg de checklist in
`docs/TEST_PLAN.md` en werk dit statusdocument bij per afgerond onderdeel.
