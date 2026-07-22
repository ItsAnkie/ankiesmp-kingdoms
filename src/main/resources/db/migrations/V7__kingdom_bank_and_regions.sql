-- Fase 5: kingdom bank saldo (in minor units, bijv. eurocenten) en
-- multi-region claim-geometry. Alles idempotent via IF NOT EXISTS-safe patronen.

CREATE TABLE kingdom_bank (
    kingdom_id   TEXT    PRIMARY KEY,
    balance_minor INTEGER NOT NULL DEFAULT 0 CHECK (balance_minor >= 0),
    updated_at   INTEGER NOT NULL,
    FOREIGN KEY (kingdom_id) REFERENCES kingdoms(id) ON DELETE CASCADE
);

-- Multi-region geometry per claim. Bestaande rechthoekige claims worden
-- gemigreerd naar exact één region-rij met dezelfde bounds; claims.id blijft
-- behouden zodat alle FK's intact zijn. In deze slice blijven claims.min_x/
-- max_x/... bestaan als cached bounds; fase 5.x bepaalt of ze uitgefaseerd
-- kunnen worden (single source of truth wordt claim_regions).
CREATE TABLE claim_regions (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    claim_id     TEXT    NOT NULL,
    min_x        INTEGER NOT NULL,
    min_z        INTEGER NOT NULL,
    max_x        INTEGER NOT NULL,
    max_z        INTEGER NOT NULL,
    created_at   INTEGER NOT NULL,
    CHECK (min_x <= max_x),
    CHECK (min_z <= max_z),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX idx_claim_regions_claim ON claim_regions(claim_id);
CREATE INDEX idx_claim_regions_bounds ON claim_regions(min_x, max_x, min_z, max_z);

-- One-time seed: iedere bestaande claim krijgt precies één region met de
-- huidige bounds. Idempotent: alleen invoegen als er nog geen region voor
-- die claim bestaat.
INSERT INTO claim_regions(claim_id, min_x, min_z, max_x, max_z, created_at)
SELECT c.id, c.min_x, c.min_z, c.max_x, c.max_z, c.created_at
FROM claims c
WHERE NOT EXISTS (SELECT 1 FROM claim_regions r WHERE r.claim_id = c.id);
