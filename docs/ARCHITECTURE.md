# Dominium — Architectuur

## 1. Doel

Dominium is een Paper-plugin voor blok-gebaseerde GriefPrevention-achtige
claims, aangevuld met **kingdoms** (uitgebreide teams — geen politieke
simulatie). Het speluitzicht is dat solo-spelers volwaardig met persoonlijke
claims kunnen spelen en dat kingdoms daar samenwerking, gezamenlijke
territoriumadministratie, een bank en consensuele oorlogen aan toevoegen.

Kingdoms zijn expliciet **geen** regeringen: geen verkiezingen, geen votes,
geen laws, geen councils, geen opvolgingssystemen. Alleen `LEADER`,
`CO_LEADER`, `MEMBER` en een aparte visitor-toegangslijst.

## 2. Technische basis

- **Java:** 25 (bewust ingesteld in `build.gradle.kts`).
- **Paper API:** `io.papermc.paper:paper-api:26.2.build.+`.
- **Build:** Gradle 9.6.1 (Kotlin DSL).
- **Test:** JUnit 5.
- **DB:** SQLite (default) via HikariCP; PostgreSQL-ondersteuning is
  voorzien maar niet in fase 0 aangesloten.

## 3. Module-indeling

Master-prompt §3.2 stelt multi-module voor (`dominium-api`, `-core`,
`-storage`, `-paper`). Voor fase 0–3 hanteren we **één Gradle-module met
strikt gescheiden interne packages**, gemotiveerd door:

- geen bestaande productie-code; multi-module levert nu enkel buildfriction;
- de packagegrens is even goed te bewaken via ArchUnit-achtige tests later;
- opsplitsen naar echte modules blijft mogelijk zodra `dominium-api` een
  eerste extern gepubliceerd contract vormt.

Package-root: `dev.ankiesmp.dominium`.

```
dev.ankiesmp.dominium
├── api          -- publieke DTO's / servicecontracten / events
├── core         -- pure domeinlogica (geen Bukkit/Paper imports)
│   ├── common   -- geld, ids, tijd-primitieven
│   └── ledger   -- claim-block ledger
├── storage      -- HikariCP, migraties, JDBC-repositories
│   ├── db
│   └── migrations
└── paper        -- plugin bootstrap, commands, listeners, GUIs
```

Regel: `core` mag niets uit `paper`/Bukkit/Adventure/JDBC importeren.
`storage` mag `core`-types én JDBC gebruiken, maar geen Bukkit.
`paper` mag alle bovenstaande gebruiken.

## 4. DI en services

Handmatige constructor DI in de plugin bootstrap (`DominiumPlugin`). Er is
géén globale `DominiumManager` of statische service-locator. De bootstrap
bouwt services op in de juiste volgorde en houdt referenties.

## 5. Threading

- Databaseworkers draaien op een dedicated executor.
- Domeinmutaties gebeuren voorspelbaar op de serverthread.
- World-mutaties gebeuren nooit async.
- Async taken krijgen enkel immutable snapshots/DTO's mee.

## 6. Ledger-principes

Claim-block-ledger en (later) kingdom-bank-ledger zijn append-only,
idempotent op basis van een `idempotency_key`, met een gematerialiseerd
saldo dat in dezelfde transactie wordt bijgewerkt. Zie
[`docs/DATABASE.md`](DATABASE.md).

## 7. Roadmap

Zie [`docs/IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md) voor
per-fase status. De master-prompt (§25) definieert 13 fases (0–12).
