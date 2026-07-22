# Dominium — Test plan

## Legenda

- **AT** — automatische test (JUnit).
- **PT** — property-based test.
- **IT** — integratietest tegen echte DB.
- **MT** — handmatige testserver-checklist.

## Fase 0 — Foundation

| id     | scope              | type | omschrijving                                                   |
|--------|--------------------|------|----------------------------------------------------------------|
| L-001  | ledger             | AT   | grant boekt correct en balans stijgt met delta                 |
| L-002  | ledger             | AT   | spend faalt zonder saldo en ledger blijft ongewijzigd          |
| L-003  | ledger             | AT   | dubbele idempotencyKey levert dezelfde entry en boekt niet 2x  |
| L-004  | ledger             | AT   | balans is gelijk aan som(delta) na willekeurige mixed acties   |
| L-005  | ledger             | PT   | random reeksen mutaties → geen negatieve balans, cap-safe      |
| L-006  | ledger             | AT   | grant negatief bedrag wordt geweigerd                          |
| L-007  | ledger             | AT   | total_earned/total_spent volgen respectievelijk + en |−|deltas  |
| DB-001 | migraties          | AT   | lege DB → V1+V2+V3 toegepast, alle tabellen bestaan            |
| DB-002 | migraties          | AT   | tweede opstart voegt geen migratie opnieuw toe                 |
| DB-003 | migraties          | AT   | alleen V1 toegepast → V2 en V3 komen erbij bij restart         |
| DB-004 | migraties          | AT   | V1 + V2 toegepast → alleen V3 komt erbij bij restart           |
| DB-005 | migraties          | AT   | gefaalde SQL rolt terug en registreert geen schema_version-rij |
| DB-006 | migraties          | AT   | ontbrekend resource in registry → IllegalStateException        |
| DB-007 | migraties          | AT   | dubbele versie in registry → IllegalStateException             |
| DB-008 | migraties          | AT   | laden vanuit URLClassLoader (jar-simulatie, zonder parent)     |
| BS-001 | bootstrap          | AT   | headless bootstrap start én sluit netjes, alle tabellen bestaan|
| BS-002 | bootstrap          | AT   | tweede bootstrap gebruikt bestaand schema, index leeg          |
| BS-003 | bootstrap          | AT   | mislukte migratie → Hikari-pool gesloten + executor terminated |
| BS-004 | bootstrap          | AT   | `close()` sluit Hikari en dbExecutor deterministisch           |
| MT-001 | plugin.yml         | MT   | Paper accepteert `plugin.yml` zonder mojibake / 0x80-code-point |
| MT-002 | smoketest          | MT   | `gradlew runServer` (Paper 26.2-62): fresh start past V1+V2+V3 toe; `/dominium version` en `plugins` werken; clean shutdown sluit Hikari-pool + db-executor; herstart slaat V1/V2/V3 over; driver-provenance-log + `SQLite JDBC 3.49.1.0` zichtbaar |

**Evidence MT-002** (uitgevoerd 2026-07-21):
- `run/logs/2026-07-21-4.log.gz` — fresh apply van V1/V2/V3, `/dominium version`, `/plugins`, clean shutdown ("Dominium core closed (HikariCP pool + db executor released).").
- `run/logs/latest.log` — restart met V1/V2/V3 SKIP, driver = `SQLite JDBC 3.49.1.0` (Paper wint parent-first, zoals verwacht en gedocumenteerd in `docs/DATABASE.md` §4).

## Fase 0.5 — Initial player claim-block grant

| id     | scope                 | type | omschrijving                                                    |
|--------|-----------------------|------|-----------------------------------------------------------------|
| IG-001 | initial grant         | AT   | nieuwe speler krijgt exact één startgrant                       |
| IG-002 | initial grant         | AT   | tweede join levert `ALREADY_APPLIED`, geen dubbele boeking      |
| IG-003 | initial grant         | AT   | restart (nieuwe service-instantie op zelfde DB) herhaalt niet   |
| IG-004 | initial grant         | AT   | twee gelijktijdige `attemptFor` boeken samen exact één grant    |
| IG-005 | initial grant         | AT   | `starting-balance = 0` → `GrantOutcome.DISABLED`, geen mutatie  |
| IG-006 | initial grant         | AT   | bestaand saldo zonder `INITIAL_GRANT`-entry ontvangt alsnog één grant (documented decision) |
| IG-007 | initial grant         | AT   | idempotency key deterministisch en stabiel per player-UUID      |
| IG-008 | initial grant         | AT   | negatief `starting-balance` afgewezen bij constructie           |
| AO-001 | admin ops             | AT   | positief bedrag boekt via ledger als `ADMIN_GRANT`              |
| AO-002 | admin ops             | AT   | 0 of negatief bedrag afgewezen                                  |
| AO-003 | admin ops             | AT   | zonder expliciete key stacken herhaalde calls (fresh randoms)   |
| AO-004 | admin ops             | AT   | expliciete key maakt de call retry-safe (`ALREADY_APPLIED`)     |
| AO-005 | admin ops             | AT   | null player/actor afgewezen                                     |
| CBR-001| `/claim blocks`       | AT   | rendert Available / Total earned / Total spent uit snapshot     |
| CBR-002| `/claim blocks`       | AT   | zero-state (verse speler) toont drie nullen                     |
| CBR-003| `/claim blocks`       | AT   | null-snapshot afgewezen                                         |
| PT-001 | target resolver       | AT   | online speler op exacte naam wordt geresolved                    |
| PT-002 | target resolver       | AT   | bekende offline speler op naam wordt geresolved                  |
| PT-003 | target resolver       | AT   | case-insensitieve exact-name match werkt (bewuste keuze)         |
| PT-004 | target resolver       | AT   | gelijkende naam (prefix / substring) matcht NIET met andere speler |
| PT-005 | target resolver       | AT   | onbekende naam → empty                                          |
| PT-006 | target resolver       | AT   | willekeurige geldige UUID zonder hasPlayedBefore → empty         |
| PT-007 | target resolver       | AT   | bekende offline UUID → resolved (naam uit lookup)               |
| PT-008 | target resolver       | AT   | online UUID → resolved via online-pad                            |
| PT-009 | target resolver       | AT   | malformed UUID (of `1-2-3-4-5`) → empty, valt niet ten prooi aan `UUID.fromString` |
| PT-010 | target resolver       | AT   | lege / whitespace input → empty                                  |
| PT-011 | target resolver       | AT   | tab completions bevatten alleen bekende spelers, prefix-gefilterd |
| PT-012 | target resolver       | AT   | tab completions dedupliceren wanneer speler zowel online als offline verschijnt |
| AG-001 | admin grant action    | AT   | online bekende speler → geaccepteerd, ledger-entry gemaakt       |
| AG-002 | admin grant action    | AT   | bekende offline speler → geaccepteerd                            |
| AG-003 | admin grant action    | AT   | onbekende naam → geweigerd, geen ledger- of balance-rij         |
| AG-004 | admin grant action    | AT   | willekeurige UUID → geweigerd, geen ledger- of balance-rij      |
| AG-005 | admin grant action    | AT   | gelijkende naam kiest nooit een verkeerde speler                 |
| AG-006 | admin grant action    | AT   | case-insensitieve exact-match wordt geaccepteerd (documented decision) |
| AG-007 | admin grant action    | AT   | herhaalde afwijzingen laten de balance-tabel volledig leeg      |
| AG-008 | admin grant action    | AT   | 0 of negatief bedrag geweigerd vóór ledger wordt aangeraakt     |
| MT-003 | live grant flow       | MT   | in-game verificatie van `/claim blocks` en `/dominium claimblocks grant`, inclusief afwijzing van willekeurige namen en UUIDs. Blijft **open** — moet opnieuw door gebruiker worden uitgevoerd na de MT-003 fix voordat deze op TESTED gaat. |

## Fase 1 — Claims & golden shovel

| id     | scope                    | type | omschrijving                                                   |
|--------|--------------------------|------|----------------------------------------------------------------|
| CR-001 | rectangle                | AT   | area = width × depth inclusief                                 |
| CR-002 | rectangle                | AT   | `ofCorners` ordent min/max ongeacht invoer                     |
| CR-003 | rectangle                | AT   | `resizeCostDelta` positief bij groter, negatief bij kleiner    |
| CR-004 | rectangle                | AT   | `intersects` klopt bij hoek-raak en niet bij adjacency         |
| CR-005 | rectangle                | AT   | `chebyshevGapTo` retourneert het minimum tussenruimte-getal    |
| CR-006 | rectangle                | AT   | `sharesEdge` alleen bij exacte adjacency                       |
| CR-007 | rectangle                | AT   | ongeldige geometry wordt geweigerd bij ctor                    |
| CI-001 | claim index              | AT   | `containing` vindt exact één claim in de bucket                |
| CI-002 | claim index              | AT   | `overlapping` unioneert candidate-buckets en dedupliceert      |
| CI-003 | claim index              | AT   | `replace` re-indexeert nieuwe geometrie                        |
| CI-004 | claim index              | AT   | `remove` maakt alle relevante buckets leeg                     |
| PV-001 | placement validator      | AT   | rejecteert te kleine zijde                                     |
| PV-002 | placement validator      | AT   | rejecteert overlap                                             |
| PV-003 | placement validator      | AT   | rejecteert buffer-schending                                    |
| PV-004 | placement validator      | AT   | rejecteert onvoldoende claim blocks en meldt precieze delta    |
| PV-005 | placement validator      | AT   | accepteert geldige create                                      |
| PV-006 | placement validator      | AT   | resize negeert overlap met eigen claim en berekent delta       |
| CS-001 | claim service (create)   | AT   | boekt exact `width × depth` op ledger                           |
| CS-002 | claim service (create)   | AT   | weigert bij onvoldoende saldo en muteert niets                 |
| CS-003 | claim service (resize+)  | AT   | boekt alleen de uitbreiding                                    |
| CS-004 | claim service (resize−)  | AT   | betaalt alleen het verschil terug                              |
| CS-005 | claim service (delete)   | AT   | vergoedt de volledige oppervlakte                              |

## Fase 2 — Protecties

| id     | scope                | type | omschrijving                                                     |
|--------|----------------------|------|------------------------------------------------------------------|
| FD-001 | flag defaults        | AT   | PERSONAL_OWNER krijgt ALLOW voor iedere flag                     |
| FD-002 | flag defaults        | AT   | KINGDOM_VISITOR heeft nooit CONTAINER of storage-indirect        |
| FD-003 | flag defaults        | AT   | KINGDOM_VISITOR heeft entry + door + gate + button/lever         |
| FD-004 | flag defaults        | AT   | PUBLIC heeft alleen ENTRY, rest PASS                             |
| FD-005 | flag defaults        | AT   | ALLY: entry ALLOW, build/container/PVP DENY                      |
| FD-006 | flag defaults        | AT   | KINGDOM_MEMBER krijgt BUILD/BREAK/CONTAINER                      |
| PS-001 | protection service   | AT   | wilderness altijd ALLOW ongeacht actor                           |
| PS-002 | protection service   | AT   | owner mag bouwen in eigen claim                                  |
| PS-003 | protection service   | AT   | stranger mag niet bouwen in andermans claim                      |
| PS-004 | protection service   | AT   | stranger mag entry maar niet container                           |
| PS-005 | protection service   | AT   | anonymous actor valt naar PUBLIC                                 |
| PS-006 | protection service   | AT   | DENY van eigen audience wint over PASS/ALLOW van public          |
| PS-007 | protection service   | AT   | onbekende flag valt op safe-default DENY                         |

## Fase 3 — Visitors, No Access, HUD, activity

| id     | scope                     | type | omschrijving                                                    |
|--------|---------------------------|------|-----------------------------------------------------------------|
| AC-001 | personal access service   | AT   | trust voegt entry toe en triggert invalidator                    |
| AC-002 | personal access service   | AT   | visitor→trusted promotie is atomair (1 rij)                     |
| AC-003 | personal access service   | AT   | owner kan zichzelf niet trusten                                  |
| AC-004 | personal access service   | AT   | non-owner kan niet muteren (trust én no-access)                 |
| AC-005 | personal access service   | AT   | untrust op onbekend target → NOT_FOUND                          |
| AC-006 | personal access service   | AT   | no-access toggle idempotent en invalidator wordt gecalled       |
| AC-007 | personal access service   | AT   | entries + settings overleven restart (nieuwe service-instantie)  |
| AC-008 | personal access service   | AT   | kingdom/admin-claim wordt terecht als NOT_OWNER geweigerd        |
| FD-007 | flag defaults             | AT   | `PERSONAL_VISITOR` matcht de visitor-invariant                   |
| AR-001 | audience resolver         | AT   | owner > trusted > visitor > public precedence                    |
| AR-002 | protection end-to-end     | AT   | trusted mag build/container; visitor entry/door maar geen container/bucket; public alleen entry |
| AR-003 | no-access overlay         | AT   | PUBLIC krijgt deny op alle flags; owner/trusted/visitor behouden hun rechten |
| TC-001 | territory cache           | AT   | hit/miss counters kloppen, upstream slechts één keer geraadpleegd |
| TC-002 | territory cache           | AT   | invalidateAccess per (claim, player) laat andere entries intact  |
| TC-003 | territory cache           | AT   | invalidateClaim sweept alle audience + settings van die claim    |
| TC-004 | territory cache           | AT   | settings-invalidation na no-access toggle                        |
| TC-005 | territory cache           | AT   | TTL-expiry refetcht                                              |
| TC-006 | territory cache           | AT   | clear() zet cache leeg                                           |
| TC-007 | territory cache           | AT   | contextAt levert juiste audience én no-access flag                |
| AT-001 | activity tracker          | AT   | actieve tijd tussen twee acties telt volledig                    |
| AT-002 | activity tracker          | AT   | AFK-tijd (> window) telt niet, cap is impliciet 0                |
| AT-003 | activity tracker          | AT   | drainAll reset counters, sessie blijft in leven                  |
| AT-004 | activity tracker          | AT   | quit levert accrued delta terug                                  |
| AT-005 | activity tracker          | AT   | reconnect start bij 0 (geen dubbel tellen)                       |
| AT-006 | activity tracker          | AT   | meerdere spelers werken onafhankelijk                            |
| EA-001 | active-play earner        | AT   | positief bedrag geboekt op ledger                                |
| EA-002 | active-play earner        | AT   | dubbele slot-call is idempotent op ledger                        |
| EA-003 | active-play earner        | AT   | daily cap wordt atomair afgedwongen (partial + capped)           |
| EA-004 | active-play earner        | AT   | UTC-dagwisseling reset de cap                                    |
| EA-005 | active-play earner        | AT   | earning disabled bij blocksPerInterval=0                         |
| EA-006 | active-play earner        | AT   | admin-grant telt niet mee voor active-play cap                   |
| EA-007 | active-play earner        | AT   | negatieve config afgewezen                                       |
| EA-008 | active-play earner        | AT   | ledger reason = ACTIVE_PLAY_EARN                                 |
| CBR-004| /claim blocks readout     | AT   | earning-enabled variant toont today + cap-remaining               |
| EX-001 | inactivity expiry         | AT   | te jonge claim (< min-age) blijft                                |
| EX-002 | inactivity expiry         | AT   | offline + inactive + oud genoeg → delete + ledger-refund         |
| EX-003 | inactivity expiry         | AT   | online owner wordt overgeslagen                                  |
| EX-004 | inactivity expiry         | AT   | kingdom/adminclaim wordt nooit verwijderd                        |
| EX-005 | inactivity expiry         | AT   | disabled config → geen scan                                      |
| EX-006 | inactivity expiry         | AT   | postDeleteHook (cache-invalidation) wordt aangeroepen            |
| MT-004 | live fase 3               | MT   | in-game: trust/untrust/visitor/no-access/HUD/movement, earning over UTC-dagwisseling, /claim blocks. Blijft **open** tot gebruikersbevestiging. |

## Fase 4 — Kingdom lifecycle & rollen

| id     | scope                     | type | omschrijving                                                    |
|--------|---------------------------|------|-----------------------------------------------------------------|
| KL-001 | kingdom service           | AT   | create maakt kingdom + exact één LEADER                          |
| KL-002 | kingdom service           | AT   | duplicate naam case-insensitive geweigerd                        |
| KL-003 | kingdom service           | AT   | speler kan niet in twee kingdoms                                 |
| KL-004 | membership                | AT   | leader kan niet leave                                            |
| KL-005 | membership                | AT   | member kan leave                                                 |
| KL-006 | lifecycle                 | AT   | disband verwijdert members/invites/visitors (FK CASCADE)         |
| KL-007 | transfer                  | AT   | transfer is atomair: 1 leader vóór en na, oude leader = MEMBER   |
| KL-008 | transfer                  | AT   | mislukte transfer (onbekend target) laat leader intact           |
| KL-009 | invariants                | AT   | exact één leader na iedere mutatie                               |
| KL-010 | naam validatie            | AT   | te kort / illegal chars afgewezen                                |
| IN-001 | invite service            | AT   | invite create + zichtbaar via invitesFor                          |
| IN-002 | invite service            | AT   | duplicate invite afgewezen                                       |
| IN-003 | invite service            | AT   | expired invite kan niet worden accepted                          |
| IN-004 | invite service            | AT   | accept: membership insert + invite delete atomair                 |
| IN-005 | invite service            | AT   | accept verwijdert bestaande visitor entry                        |
| IN-006 | invite service            | AT   | target die inmiddels elders lid is → accept faalt                |
| IN-007 | invite service            | AT   | decline verwijdert invite                                        |
| IN-008 | invite cleanup            | AT   | cleanupExpired verwijdert oude rows                              |
| IN-009 | invite service            | AT   | self-invite afgewezen                                            |
| MB-001 | membership                | AT   | promote member → CO_LEADER                                       |
| MB-002 | membership                | AT   | co-leader kan niet promoveren                                    |
| MB-003 | membership                | AT   | co-leader kan andere co-leader niet kicken                       |
| MB-004 | membership                | AT   | co-leader kan gewone member kicken                               |
| MB-005 | membership                | AT   | demote werkt alleen op CO_LEADER                                 |
| MB-006 | membership                | AT   | leader kan niet gekickt of gedemoted worden                      |
| MB-007 | membership                | AT   | self-target voor kick/promote/demote/transfer geweigerd          |
| PM-001 | permissions               | AT   | leader mag alle acties                                           |
| PM-002 | permissions               | AT   | co-leader mag invite/visitors/claims/kick_member                 |
| PM-003 | permissions               | AT   | co-leader mag leader/co-leader-acties NIET                        |
| PM-004 | permissions               | AT   | member heeft nul admin-permissies                                |
| VS-001 | visitor service           | AT   | add + remove                                                     |
| VS-002 | visitor service           | AT   | member kan niet visitor zijn                                     |
| VS-003 | visitor service           | AT   | visitor die member wordt: entry atomair weg                      |
| VS-004 | visitor service           | AT   | non-owner mag niet muteren                                       |
| VS-005 | visitor service           | AT   | persistentie na restart (nieuwe store-instantie)                 |
| KC-001 | kingdom cache             | AT   | hit/miss counters + upstream slechts één keer                     |
| KC-002 | kingdom cache             | AT   | invalidatePlayer wist membership + visitor-entries van speler     |
| KC-003 | kingdom cache             | AT   | invalidateKingdom sweept relaterade entries                       |
| KA-001 | audience resolver         | AT   | leader > co > member > visitor > public precedence                |
| KA-002 | audience resolver         | AT   | member van ander kingdom valt naar PUBLIC                         |
| KA-003 | audience resolver         | AT   | personal claim wordt naar personal-resolver gedelegeerd           |
| BG-001 | border geometry           | AT   | distance buiten claim                                            |
| BG-002 | border geometry           | AT   | distance binnen claim negatief                                    |
| BG-003 | border geometry           | AT   | inTriggerRange correct                                            |
| BG-004 | border geometry           | AT   | punten binnen render-distance + unieke hoeken                     |
| BG-005 | border geometry           | AT   | budget-cap houdt dichtstbijzijnde punten                          |
| BG-006 | border geometry           | AT   | speler ver weg → geen punten                                      |
| BG-007 | border geometry           | AT   | config-validatie afwijst 0/negatief/render<trigger                |
| BG-008 | border geometry           | AT   | grotere spacing → minder punten                                   |
| BG-009 | border geometry           | AT   | alle punten uniek                                                 |
| BS-001 | border selector           | AT   | vreemde claim nearby wordt geselecteerd                           |
| BS-002 | border selector           | AT   | own personal claim wordt gefilterd met onlyForeign=true            |
| BS-003 | border selector           | AT   | kingdommembership telt als own-territory                          |
| BS-004 | border selector           | AT   | speler ver weg krijgt niets                                       |
| CS-101 | confirm store             | AT   | arm + consume matcht op action én kingdom                         |
| CS-102 | confirm store             | AT   | mismatch → geen consume                                           |
| CS-103 | confirm store             | AT   | TTL-expiry → geen consume                                         |
| CS-104 | confirm store             | AT   | clear() dropt pending                                             |
| MT-005 | live fase 4               | MT   | in-game: `/kingdom create/invite/accept/kick/promote/transfer/disband/visitor`, HUD op kingdomterritory, border-particles (Happy Villager) — blijft **open** tot gebruikersbevestiging. |

## Fase 4.5 — Live-fix slice

| id      | scope                        | type | omschrijving                                                        |
|---------|------------------------------|------|---------------------------------------------------------------------|
| CE-001  | claim expansion              | AT   | selectie binnen bestaande claim → NO_OP                              |
| CE-002  | claim expansion              | AT   | los eiland → REJECT_DETACHED                                         |
| CE-003  | claim expansion              | AT   | alleen hoekcontact → REJECT_CORNER_ONLY                              |
| CE-004  | claim expansion              | AT   | edge-adjacency + rechthoekige union → OK                             |
| CE-005  | claim expansion              | AT   | adjacency maar L-vorm → REJECT_NOT_RECTANGULAR                       |
| CE-006  | claim expansion              | AT   | overlap + rechthoekige union → OK                                    |
| SC-001  | one-claim invariant          | AT   | DB weigert tweede PERSONAL-claim (V6 partial unique index)           |
| SC-002  | one-claim invariant          | AT   | DB weigert tweede KINGDOM-claim                                       |
| SC-003  | claim index                  | AT   | `findByOwner` vindt bestaande claim                                  |
| SC-004  | duplicate audit              | AT   | schone index → geen conflict                                          |
| DA-001  | duplicate audit              | AT   | schone index leeg                                                    |
| DA-002  | duplicate audit              | AT   | twee PERSONAL-claims voor zelfde owner → 1 conflict met 2 IDs         |
| DA-003  | duplicate audit              | AT   | ADMIN-claims uitgezonderd                                             |
| KO-001  | kingdom claim-block admin    | AT   | grant boekt op KINGDOM-holder, niet op speler                         |
| KO-002  | kingdom claim-block admin    | AT   | audit-actor, reason, reference velden bewaard                        |
| KO-003  | kingdom claim-block admin    | AT   | expliciete idempotency key → retry-safe                              |
| KO-004  | kingdom claim-block admin    | AT   | 0/negatief bedrag geweigerd                                          |
| HM-001  | hud template                 | AT   | `<owner>` placeholder resolveert                                     |
| HM-002  | hud template                 | AT   | `<kingdom>` placeholder resolveert                                   |
| HM-003  | hud template                 | AT   | malformed MiniMessage-template zet plugin niet uit                   |
| MT006-A | border particles regression  | AT   | onlyForeign=false: eigen claim wordt geselecteerd                    |
| MT006-B | border particles regression  | AT   | onlyForeign=true: eigen claim wordt gefilterd (anker voor oude bug)  |
| MT-006  | live particles/HUD/ownership | MT   | live-verificatie: particles zichtbaar met defaults; actionbar constant in wilderness/persoonlijk/kingdom; `/claim mode kingdom` + create maakt echte kingdomclaim; `/kingdom` opent GUI; `/dominium claims inspect/delete/transfer`. Blijft **open** tot gebruikersbevestiging. |

## Fase 4.6 — V6 upgrade-path fix

| id      | scope                       | type | omschrijving                                                        |
|---------|-----------------------------|------|---------------------------------------------------------------------|
| V6-001  | index installer             | AT   | schone DB zonder claims → APPLIED, index geplaatst, schema_version=6 |
| V6-002  | index installer             | AT   | één claim per owner → APPLIED                                        |
| V6-003  | index installer             | AT   | twee PERSONAL-claims dezelfde speler → DEFERRED, geen dataverlies, geen schema_version-rij |
| V6-004  | index installer             | AT   | twee KINGDOM-claims hetzelfde kingdom → DEFERRED                     |
| V6-005  | index installer             | AT   | na admin-delete van duplicaat → volgende install slaagt              |
| V6-006  | index installer             | AT   | tweede aanroep na APPLIED → ALREADY_APPLIED (idempotent)             |
| V6-007  | index installer             | AT   | andere owners zonder conflict worden niet geflagged                  |
| MG-001  | mutation guard              | AT   | geblokkeerde owner: create → BLOCKED; andere owner: create → OK      |
| MG-002  | mutation guard              | AT   | geblokkeerde owner: resize → BLOCKED                                  |
| MG-003  | mutation guard              | AT   | service weigert tweede claim ook zonder DB-index (DUPLICATE_OWNER)   |
| MG-004  | mutation guard              | AT   | ALLOW_ALL blokkeert niets                                            |
| CDC-001 | delete confirmations        | AT   | arm + consume matcht actor + claim                                   |
| CDC-002 | delete confirmations        | AT   | mismatch consumeert niet                                             |
| CDC-003 | delete confirmations        | AT   | expiry na TTL dropt pending                                          |
| MT-007  | live V6 upgrade             | MT   | bestaande dev-DB start in CLAIM_REPAIR_MODE, admin-flow lost conflicten op, restart plaatst V6, plugin verlaat repair-mode. Blijft **open** tot gebruikersbevestiging. |

## Latere fases

Zie master-prompt §24 voor de volledige teststrategie (unit/property,
DB-integratie via SQLite en Testcontainers-PostgreSQL, en de handmatige
listener/testserver-scenario's voor golden shovel, griefmatrix, visitors,
No Access, kingdom lifecycle, kingdom bank, war end-to-end, restart-
recovery en performance-seeding met 5.000–10.000 rechthoekige claims).

De testset in dit bestand groeit per fase. Iedere fase eindigt pas
groen wanneer:

1. `gradlew test` volledig slaagt;
2. relevante MT-checklist handmatig doorlopen is voor gevoelige listeners;
3. `docs/IMPLEMENTATION_STATUS.md` bijgewerkt is met echte statussen.
