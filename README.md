# Dominium

Paper-plugin voor block-based claims (à la GriefPrevention) plus
**kingdoms** die aanvoelen als uitgebreide teams — geen politieke
simulatie. Solo-spelers kunnen volwaardig spelen met persoonlijke claims;
kingdoms voegen samenwerking, een gezamenlijk territorium, een bank en
consensuele oorlogen toe.

## Technisch

- **Paper API:** `26.2.build.+`
- **Java:** 25
- **Build:** Gradle 9.6.1 (Kotlin DSL)
- **DB default:** SQLite (WAL) via HikariCP

## Documentatie

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md)
- [`docs/DATABASE.md`](docs/DATABASE.md)
- [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md)
- [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md)

## Build

```bash
./gradlew build
./gradlew test
./gradlew runServer   # Paper dev-server met plugin geladen
```
