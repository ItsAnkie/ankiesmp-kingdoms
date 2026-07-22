CREATE TABLE claims (
    id           TEXT    PRIMARY KEY,
    world_id     TEXT    NOT NULL,
    owner_type   TEXT    NOT NULL,      -- PERSONAL, KINGDOM, ADMIN
    owner_id     TEXT    NOT NULL,
    min_x        INTEGER NOT NULL,
    min_z        INTEGER NOT NULL,
    max_x        INTEGER NOT NULL,
    max_z        INTEGER NOT NULL,
    created_at   INTEGER NOT NULL,
    CHECK (min_x <= max_x),
    CHECK (min_z <= max_z)
);

CREATE INDEX idx_claims_world ON claims(world_id);
CREATE INDEX idx_claims_owner ON claims(owner_type, owner_id);

CREATE TABLE claim_geometry_revisions (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    claim_id     TEXT    NOT NULL,
    min_x        INTEGER NOT NULL,
    min_z        INTEGER NOT NULL,
    max_x        INTEGER NOT NULL,
    max_z        INTEGER NOT NULL,
    reason       TEXT    NOT NULL,      -- CREATE, RESIZE, RESTORE, DELETE
    cost_delta   INTEGER NOT NULL,      -- + is spend, - is refund, 0 for delete
    created_at   INTEGER NOT NULL,
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX idx_claim_revisions_claim ON claim_geometry_revisions(claim_id);
