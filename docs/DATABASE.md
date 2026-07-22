# Dominium — Database

Default: **SQLite** in WAL-mode, één file per server (`plugins/Dominium/data.db`).
PostgreSQL wordt voorbereid maar in fase 0 niet aangesloten.

## 1. Verbinding

- HikariCP als connection-pool.
- `PRAGMA journal_mode=WAL;` en `PRAGMA foreign_keys=ON;` bij openen.
- Alle I/O gebeurt op een dedicated `DatabaseExecutor`.

## 2. Migraties

Eigen versioned migrations, geen Flyway-runtimedependency.

Tabel `schema_version`:

| column       | type    | notes            |
|--------------|---------|------------------|
| version      | INTEGER | PK, monotoon     |
| applied_at   | INTEGER | epoch millis     |
| description  | TEXT    | menselijk label  |

**Discovery**: er wordt bewust **geen** directory-enumeratie via
`ClassLoader.getResources("db/migrations")` gebruikt. Die faalt in
shaded plugin-jars omdat directory-entries in het zip-archief optioneel
zijn en Shadow ze niet altijd emit. In plaats daarvan is er één
expliciete, geordende **registry** in
`dev.ankiesmp.dominium.storage.migrations.MigrationRegistry`:

```java
public static final List<String> RESOURCE_PATHS = List.of(
    "db/migrations/V1__initial_claim_block_ledger.sql",
    "db/migrations/V2__bank_operation_journal.sql",
    "db/migrations/V3__claims.sql"
);
```

`ClasspathMigrationSource` leest ieder pad via `ClassLoader.getResourceAsStream`.
Ontbreekt een pad → `IllegalStateException` bij startup. Twee entries met
hetzelfde `Vn`-nummer → idem.

**Uitvoering**: `MigrationRunner.migrate()` draait iedere pending migratie
in één transactie:

1. `schema_version` wordt (indien nodig) aangemaakt.
2. Per migratie: reeds toegepast → skip; anders SQL uitvoeren → INSERT in
   `schema_version` → commit.
3. Faalt de SQL of de INSERT, dan rolt de transactie terug en stopt de
   runner met een exception (de rest wordt niet uitgevoerd).

De runner logt via SLF4J welke migraties gevonden/skiped/toegepast zijn.

**Iedere nieuwe migratie moet in `MigrationRegistry` worden bijgeschreven** —
alleen het bestand toevoegen is niet genoeg.

**Volgorde**: `SqlClaimRepository.loadAll()` en andere repository-reads
mogen pas ná `MigrationRunner#migrate()` worden aangeroepen; de bootstrap
handhaaft die volgorde.

## 3. Fase 0 schema

### `schema_version`
Zie boven.

### `claim_block_ledger`
Append-only ledger van alle claim-block-mutaties.

| column          | type    | notes                                      |
|-----------------|---------|--------------------------------------------|
| id              | INTEGER | PK autoincrement                           |
| holder_type     | TEXT    | `PLAYER` of `KINGDOM`                      |
| holder_id       | TEXT    | UUID / kingdom-id                          |
| delta           | INTEGER | + verwerving / − uitgave                   |
| reason          | TEXT    | zie enum `ClaimBlockReason`                |
| reference       | TEXT    | optionele externe referentie               |
| idempotency_key | TEXT    | UNIQUE — voorkomt dubbele boekingen        |
| actor           | TEXT    | UUID of `SYSTEM`/`ADMIN:<name>`            |
| metadata_json   | TEXT    | vrij JSON-veld                             |
| created_at      | INTEGER | epoch millis                               |

Index: `(holder_type, holder_id)`.

### `claim_block_balance`
Gematerialiseerd saldo per holder, atomair bijgewerkt binnen elke boeking.

| column        | type    | notes                                    |
|---------------|---------|------------------------------------------|
| holder_type   | TEXT    | PK deel 1                                |
| holder_id     | TEXT    | PK deel 2                                |
| balance       | INTEGER | ≥ 0 (CHECK-constraint)                   |
| total_earned  | INTEGER | ≥ 0 lifetime deltas > 0                  |
| total_spent   | INTEGER | ≥ 0 lifetime |deltas < 0|                |
| updated_at    | INTEGER | epoch millis                             |

PK: `(holder_type, holder_id)`.

### Invarianten (afgedwongen in code + tests)

- `balance` is nooit negatief.
- Twee boekingen met dezelfde `idempotency_key` worden geweigerd (unique).
- `balance` is reproduceerbaar uit de som van `delta` per holder.
- Iedere ledgerinsert en balansupdate is één DB-transactie.
- Transfers tussen holders (later) schrijven beide zijden binnen dezelfde
  transactie.

### `claims`
Aangelegd door V3. Kolommen: `id`, `world_id`, `owner_type`
(`PERSONAL|KINGDOM|ADMIN`), `owner_id`, `min_x/z`, `max_x/z`,
`created_at`. `CHECK` op `min ≤ max`. Indexen op `(world_id)` en
`(owner_type, owner_id)`.

### `claim_geometry_revisions`
Aangelegd door V3. Audit-log per create/resize/delete met `cost_delta`
zodat een refund/rollback zonder pad afhankelijk is van externe logs.

## 4. SQLite-driver

Paper 26.2 levert zelf een `org.xerial:sqlite-jdbc` op de runtime-classpath
(bevestigd via smoketest: parent-first classloader wint altijd van een
plugin-lokale driver). Dominium bundelt de driver daarom **niet** in de
shaded jar. In `build.gradle.kts` staat sqlite-jdbc alleen als
`compileOnly` (zodat `org.sqlite.JDBC` beschikbaar is tijdens compilatie)
en `testImplementation` (zodat JUnit-tests een echte driver hebben).
Zo vermijden we een dubbele, ongebruikte 5+ MB aan native binaries en
verwarring over welke driver `System.load(...)` uiteindelijk aanroept.

`DominiumCore` doet bij startup twee diagnostische stappen:

1. **`logDriverProvenance()`** — probeert `org.sqlite.JDBC` en
   `org.sqlite.core.NativeDB` te laden en logt telkens
   `ClassLoader`, `ProtectionDomain#getCodeSource()#getLocation()` zodat
   direct zichtbaar is welke JAR / welke classloader wint.
2. **`logDriverVersion()`** — vraagt via een echte connection
   `DatabaseMetaData.getDriverName()` + `getDriverVersion()` op.

Als de driverversie in de log afwijkt van de versie die Paper meelevert,
staat er een tweede JAR met sqlite-jdbc op de classpath (bijvoorbeeld
een andere plugin met een eigen bundle). Operators kunnen die dan
verwijderen of aligneren op basis van de exacte code-source uit
`logDriverProvenance()`.

Referentie runtime-observatie (Paper 26.2 build 62): driver =
`SQLite JDBC 3.49.1.0`, geladen uit Paper's eigen library-loader.

## 5. Initial player claim-block grant

Iedere speler krijgt bij hun eerste door Dominium waargenomen join een
startsaldo uit `config.yml`:

```yaml
claim-blocks:
  starting-balance: 1000
```

Er wordt géén aparte "heeft-al-eens-een-startgrant-gekregen"-tabel
bijgehouden. De `UNIQUE`-constraint op `claim_block_ledger.idempotency_key`
is de enige bron van waarheid. De key wordt deterministisch afgeleid:

```
UUID.nameUUIDFromBytes("initial-player-grant:" + playerUuid)
```

Gevolg:

- Reconnect, serverrestart, dubbele `PlayerJoinEvent` en retries na een
  DB-fout leveren allemaal `ALREADY_APPLIED` op — nooit dubbel geboekt.
- Spelers die vóór de invoering van deze feature al waren gejoined
  ontvangen bij hun eerstvolgende join alsnog exact één grant (er is
  simpelweg nog geen ledger-rij met die deterministische key).
- **Bewuste keuze:** als een speler op dat moment al een balans heeft
  (bijvoorbeeld via `ADMIN_GRANT`) maar nog geen `INITIAL_GRANT`-entry,
  krijgt hij toch het startsaldo erbij. We introduceren geen tweede
  bron van waarheid ("heeft ooit een balans gehad") naast de ledger.
  Zie testcase `IG-006` in `InitialClaimBlockGrantTest`.

Admin-grants via `/dominium claimblocks grant <player> <amount>` gebruiken
dezelfde ledger met reason `ADMIN_GRANT`. Standaard krijgt iedere admin
grant een fresh-random idempotency key; callers die retry-safe moeten
zijn kunnen een expliciete key doorgeven via
`ClaimBlockAdminOps#grantToPlayer(uuid, amount, actor, key)`.

### 5.1. Target-resolutie voor admin-commands

Regel: **`Bukkit.getOfflinePlayer(String)` en `Bukkit.getOfflinePlayer(UUID)`
zijn geen existentiebewijs.** Beide geven ook voor onbekende input een
niet-null wrapper terug. Zonder aanvullende check zou een admin per
ongeluk een balans kunnen boeken voor een niet-bestaande naam of een
willekeurige UUID (dit is precies de MT-003 bug die live is waargenomen).

De resolutie zit in `PlayerTargetResolver` en `PlayerLookup`
(zie `dev.ankiesmp.dominium.core.player`). De regels:

1. Parse eerst als UUID (36 tekens, streepjes op posities 8/13/18/23).
   Slaagt dat, dan alleen accepteren als de UUID online is **of**
   `hasPlayedBefore()` is (via lokale playerdata/usercache — nooit
   een Mojang-webrequest).
2. Anders behandelen als naam. Eerst `Server#getPlayerExact` (case-sensitive,
   exact). Geen match, dan case-insensitief door alle bekende spelers
   (`Server#getOfflinePlayers()` gefilterd op `hasPlayedBefore()`).
3. **Naam-match is uitsluitend op exact gelijke naam** (case-insensitief);
   **geen prefix- of substring-match**. Zo kan `/dominium claimblocks grant
   Rens ...` nooit per ongeluk `RensJAM` raken. Deze keuze is bewust en
   gedocumenteerd in `PlayerTargetResolver`; regressie is `PT-004` en
   `AG-005`.
4. Geen match → duidelijke foutmelding
   (`Unknown player. The player must have joined this server before.`)
   en **absoluut geen** ledger- of balance-mutatie. `AdminGrantAction`
   controleert dit vóór de ledger wordt aangeraakt; regressie is
   `AG-003`, `AG-004` en `AG-007`.

Tab completion voor `/dominium claimblocks grant <player>` gebruikt
dezelfde `knownPlayers()`-set: alleen bekende spelers, prefix-gefilterd,
case-insensitief. Nooit online Mojang-lookups.

## 6. Fase 3 schema (V4)

### `personal_claim_access`
Trusted en visitor entries per persoonlijke claim.

| column       | type    | notes                                             |
|--------------|---------|---------------------------------------------------|
| id           | INTEGER | PK autoincrement                                  |
| claim_id     | TEXT    | FK `claims(id)` ON DELETE CASCADE                 |
| player_uuid  | TEXT    |                                                   |
| level        | TEXT    | CHECK IN ('TRUSTED','VISITOR')                    |
| added_at     | INTEGER | epoch millis                                      |
| added_by     | TEXT    | actor-UUID die de mutatie deed                    |

UNIQUE (`claim_id`, `player_uuid`): één rij per (claim, speler). Promotie
visitor↔trusted is atomair via DELETE+INSERT in dezelfde transactie —
zie `SqlPersonalClaimAccessStore#upsert`. Indexen op `claim_id` en
`player_uuid`.

### `personal_claim_settings`
Per-claim overlay op de audience-defaults. Fase 3 heeft alleen `no_access`;
latere fases voegen kolommen toe.

| column      | type    | notes                                              |
|-------------|---------|----------------------------------------------------|
| claim_id    | TEXT    | PK; FK `claims(id)` ON DELETE CASCADE              |
| no_access   | INTEGER | 0/1 (CHECK)                                        |
| updated_at  | INTEGER | epoch millis                                       |

### `player_activity_state`
Batched activity-persistence. Aggregatie in-memory in `ActivityTracker`;
flush voegt seconden bij en overschrijft `last_active_at`.

| column                | type    | notes                                    |
|-----------------------|---------|------------------------------------------|
| player_uuid           | TEXT    | PK                                       |
| last_active_at        | INTEGER | epoch millis                             |
| total_active_seconds  | INTEGER | monotoon oplopend                        |
| updated_at            | INTEGER | epoch millis                             |

### `player_earning_state`
Daily cap-state voor `ActivePlayEarner`. PK is (`player_uuid`, `active_day`)
zodat de cap-check atomair via `INSERT ... ON CONFLICT DO UPDATE ...`
loopt (`reserveDailyEarning`). Dag = UTC epoch-day.

| column         | type    | notes                                          |
|----------------|---------|------------------------------------------------|
| player_uuid    | TEXT    | PK deel 1                                      |
| active_day     | INTEGER | PK deel 2 (UTC epoch-day)                      |
| blocks_earned  | INTEGER | reeds toegekende blocks vandaag                |
| updated_at     | INTEGER | epoch millis                                   |

### Access-invarianten (afgedwongen in code + tests)

- Één rij per (claim, speler); promotie is atomair (`AC-002`).
- Owner kan zichzelf niet trusten (`AC-003`); claim-eigenaar mag niet als
  visitor of trusted staan.
- Alleen owner kan muteren (`AC-004`); no-access is een owner-only setting.
- Entries en no-access-flag overleven restart (`AC-007`).
- Cache-writes gebeuren pas ná commit; invalidator draait per mutatie
  (`AC-006`, `TC-002..TC-004`).
- Ledger-refund bij inactivity-expiry loopt via bestaande `ClaimService.delete`
  (transactioneel, refund-atomair). Kingdom/adminclaims worden nooit
  door persoonlijke expiry aangeraakt (`EX-004`).
- Active-play earning is idempotent per (`player`, `UTC-day`, `interval-slot`):
  crash/retry boekt niet dubbel (`EA-002`). Daily cap wordt atomair
  afgedwongen (`EA-003`). Admin-grants tellen niet mee (`EA-006`).

## 7. Fase 4 schema (V5)

### `kingdoms`
| column          | type    | notes                                     |
|-----------------|---------|-------------------------------------------|
| id              | TEXT    | PK, UUID                                  |
| display_name    | TEXT    | zoals speler getypt                       |
| normalized_name | TEXT    | UNIQUE, case-insensitive lookup           |
| created_at      | INTEGER | epoch millis                              |
| updated_at      | INTEGER | epoch millis                              |

### `kingdom_members`
| column       | type    | notes                                          |
|--------------|---------|------------------------------------------------|
| kingdom_id   | TEXT    | FK `kingdoms(id)` ON DELETE CASCADE            |
| player_uuid  | TEXT    | UNIQUE (één kingdom per speler globaal)         |
| role         | TEXT    | CHECK IN ('LEADER','CO_LEADER','MEMBER')       |
| joined_at    | INTEGER |                                                |
| promoted_at  | INTEGER | nullable                                       |

PK (`kingdom_id`, `player_uuid`). **Partial unique index** `idx_kingdom_single_leader` op `(kingdom_id) WHERE role = 'LEADER'` → er kan maar één LEADER per kingdom bestaan. Leadership-transfer is een twee-fase update in één transactie: eerst oude leader → MEMBER, dan nieuwe → LEADER.

### `kingdom_invites`
| column       | type    | notes                                          |
|--------------|---------|------------------------------------------------|
| id           | INTEGER | PK autoincrement                               |
| kingdom_id   | TEXT    | FK `kingdoms(id)` ON DELETE CASCADE            |
| target_uuid  | TEXT    | index voor lookup                              |
| inviter_uuid | TEXT    |                                                |
| created_at   | INTEGER |                                                |
| expires_at   | INTEGER | index voor cleanup                             |

UNIQUE (`kingdom_id`, `target_uuid`).

### `kingdom_visitors`
| column       | type    | notes                                          |
|--------------|---------|------------------------------------------------|
| kingdom_id   | TEXT    | FK `kingdoms(id)` ON DELETE CASCADE            |
| player_uuid  | TEXT    | PK deel 2                                       |
| added_by     | TEXT    |                                                |
| created_at   | INTEGER |                                                |

### Kingdom-invarianten (afgedwongen in code + tests)

- Exact één LEADER per kingdom (partial unique index + service-invariant `KL-009`).
- Één kingdom per speler (`kingdom_members.player_uuid` UNIQUE + `KL-003`).
- Leader kan niet leave; moet eerst transferren of disbanden (`KL-004`).
- Kingdom disband cascadeert members / invites / visitors weg (`KL-006`).
- Invites zijn uniek per (kingdom, target) en vervallen na config-TTL.
- Accept invite is transactioneel: check membership + delete visitor + insert member + delete alle invites voor deze target (`IN-004`, `IN-005`, `IN-006`).
- Visitor die member wordt: visitor-entry wordt in dezelfde `acceptInvite`-transactie verwijderd (`VS-003`).
- Namen zijn case-insensitief uniek (`KL-002`), veilige-charset validatie (`KL-010`).
- Cache-writes (`KingdomCache`) gebeuren pas ná commit; alle mutaties invalidateren de cache expliciet.

## 8. Fase 4.5/4.6 — één claim per owner (V6 met preflight)

V6 dwingt af: **max één claim per PERSONAL / KINGDOM owner**. ADMIN-claims blijven bewust vrij.

```sql
CREATE UNIQUE INDEX idx_claims_unique_owner
    ON claims(owner_type, owner_id)
    WHERE owner_type IN ('PERSONAL', 'KINGDOM');
```

**Upgrade-path (fase 4.6 correctie):** V6 wordt bewust **niet** door de gewone
`MigrationRunner` uitgevoerd. Databases uit oudere builds kunnen legitieme
duplicaten bevatten; een blinde `CREATE UNIQUE INDEX` zou dan crashen met
`SQLITE_CONSTRAINT_UNIQUE` vóór welke admin-tooling dan ook beschikbaar
is. In plaats daarvan:

1. `SingleClaimIndexInstaller.install(db)` doet een preflight:
   ```sql
   SELECT owner_type, owner_id, COUNT(*) FROM claims
    WHERE owner_type IN ('PERSONAL','KINGDOM')
    GROUP BY owner_type, owner_id HAVING COUNT(*) > 1
   ```
2. Nul duplicaten → `CREATE UNIQUE INDEX ... IF NOT EXISTS` + `INSERT INTO schema_version(6, ...)` in één transactie → `APPLIED`.
3. Duplicaten → geen wijzigingen, `DEFERRED` + conflict-lijst; plugin start in **CLAIM_REPAIR_MODE**.

**CLAIM_REPAIR_MODE:**

- Bestaande claims blijven bruikbaar en beschermd.
- `ClaimMutationGuard` blokkeert alleen de exact conflicterende owners voor create/resize (`MG-001`, `MG-002`). Andere spelers/kingdoms zijn onbeperkt (`V6-007`).
- `ClaimService.create` heeft ook een defensive `findByOwner`-check die een tweede claim voor dezelfde owner weigert **ongeacht de DB-index** (`MG-003`).
- Admin-tools blijven werken: `/dominium claims inspect|delete|transfer`.
- `/dominium claims delete <id>` vereist bevestiging via `/dominium claims delete <id> confirm` binnen 30s.
- Na het oplossen van alle conflicten past de volgende restart V6 alsnog toe (`V6-005`); de plugin verlaat automatisch repair-mode.

Regressie: `SingleClaimIndexInstallerTest` (7 tests), `ClaimMutationGuardTest` (4), `ClaimDeleteConfirmationsTest` (3).

**Correctie op eerdere statusclaim:** de eerdere doc-regel "bestaande databases met duplicaten crashen niet; DuplicateOwnerAudit waarschuwt" was feitelijk onjuist — die audit draaide pas ná de migraties. V6 crashte al op `CREATE UNIQUE INDEX`. De fix is de preflight-installer hierboven.

Overige gevolgen:

- Command-laag detecteert bestaande owner via `ClaimIndex#findByOwner` en routeert selectie naar `claimService.resize` met een rechthoekige union — regels in `ClaimExpansion` (`CE-001..CE-006`).
- Kingdomclaims boeken op een KINGDOM-holder in de bestaande append-only ledger; geen fake bank. Development-only grant via `/dominium kingdomclaimblocks grant <kingdom> <amount>`; permissie `dominium.claimblocks.admin`.

## 9. Later toe te voegen tabellen

Zie master-prompt §19. Onder meer: `worlds`, `player_profiles`, `claims`,
`claim_geometry_revisions`, `claim_spatial_buckets`, `claim_flags`,
`kingdoms`, `kingdom_members`, `kingdom_role_permissions`,
`kingdom_bank_accounts`, `kingdom_bank_ledger`,
`kingdom_bank_operation_journal`, `kingdom_bank_reservations`,
`war_proposals`, `war_proposal_versions`, `wars`, `raid_windows`,
`raid_sessions`, `history_events`, `audit_log`, `disputes`.

**Uitgesloten** (bewust, per master-prompt): `plots`, `governments`,
`laws`, `councils`, `elections`, `electorate_snapshots`, `ballots`,
`settlements`, `succession`.
