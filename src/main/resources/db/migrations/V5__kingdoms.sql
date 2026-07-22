-- Fase 4: kingdoms als uitgebreide teams. Geen politiek, geen laws,
-- geen elections; alleen LEADER/CO_LEADER/MEMBER + aparte visitorlijst.

CREATE TABLE kingdoms (
    id               TEXT    PRIMARY KEY,
    display_name     TEXT    NOT NULL,
    normalized_name  TEXT    NOT NULL UNIQUE,
    created_at       INTEGER NOT NULL,
    updated_at       INTEGER NOT NULL
);

CREATE TABLE kingdom_members (
    kingdom_id   TEXT    NOT NULL,
    player_uuid  TEXT    NOT NULL,
    role         TEXT    NOT NULL CHECK (role IN ('LEADER','CO_LEADER','MEMBER')),
    joined_at    INTEGER NOT NULL,
    promoted_at  INTEGER,
    PRIMARY KEY (kingdom_id, player_uuid),
    UNIQUE (player_uuid),
    FOREIGN KEY (kingdom_id) REFERENCES kingdoms(id) ON DELETE CASCADE
);

-- Exact één LEADER per kingdom.
CREATE UNIQUE INDEX idx_kingdom_single_leader
    ON kingdom_members(kingdom_id)
    WHERE role = 'LEADER';

CREATE INDEX idx_kingdom_members_kingdom ON kingdom_members(kingdom_id);

CREATE TABLE kingdom_invites (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    kingdom_id   TEXT    NOT NULL,
    target_uuid  TEXT    NOT NULL,
    inviter_uuid TEXT    NOT NULL,
    created_at   INTEGER NOT NULL,
    expires_at   INTEGER NOT NULL,
    UNIQUE (kingdom_id, target_uuid),
    FOREIGN KEY (kingdom_id) REFERENCES kingdoms(id) ON DELETE CASCADE
);

CREATE INDEX idx_kingdom_invites_target  ON kingdom_invites(target_uuid);
CREATE INDEX idx_kingdom_invites_expires ON kingdom_invites(expires_at);

CREATE TABLE kingdom_visitors (
    kingdom_id   TEXT    NOT NULL,
    player_uuid  TEXT    NOT NULL,
    added_by     TEXT    NOT NULL,
    created_at   INTEGER NOT NULL,
    PRIMARY KEY (kingdom_id, player_uuid),
    FOREIGN KEY (kingdom_id) REFERENCES kingdoms(id) ON DELETE CASCADE
);

CREATE INDEX idx_kingdom_visitors_kingdom ON kingdom_visitors(kingdom_id);
CREATE INDEX idx_kingdom_visitors_player  ON kingdom_visitors(player_uuid);
