-- Fase 3: personal-claim trusted/visitor lists, no-access setting,
-- player activity + earning state.

CREATE TABLE personal_claim_access (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    claim_id     TEXT    NOT NULL,
    player_uuid  TEXT    NOT NULL,
    level        TEXT    NOT NULL CHECK (level IN ('TRUSTED','VISITOR')),
    added_at     INTEGER NOT NULL,
    added_by     TEXT    NOT NULL,
    UNIQUE (claim_id, player_uuid),
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX idx_pc_access_claim  ON personal_claim_access(claim_id);
CREATE INDEX idx_pc_access_player ON personal_claim_access(player_uuid);

CREATE TABLE personal_claim_settings (
    claim_id     TEXT    PRIMARY KEY,
    no_access    INTEGER NOT NULL DEFAULT 0 CHECK (no_access IN (0,1)),
    updated_at   INTEGER NOT NULL,
    FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE TABLE player_activity_state (
    player_uuid              TEXT    PRIMARY KEY,
    last_active_at           INTEGER NOT NULL,
    total_active_seconds     INTEGER NOT NULL DEFAULT 0,
    updated_at               INTEGER NOT NULL
);

-- Earning cap-state per player per UTC day. Uniqueness op (player_uuid, active_day)
-- garandeert dat de daily cap atomair afgedwongen kan worden.
CREATE TABLE player_earning_state (
    player_uuid   TEXT    NOT NULL,
    active_day    INTEGER NOT NULL,
    blocks_earned INTEGER NOT NULL DEFAULT 0,
    updated_at    INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, active_day)
);

CREATE INDEX idx_earning_day ON player_earning_state(active_day);
