CREATE TABLE claim_block_ledger (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    holder_type     TEXT    NOT NULL,
    holder_id       TEXT    NOT NULL,
    delta           INTEGER NOT NULL,
    reason          TEXT    NOT NULL,
    reference       TEXT,
    idempotency_key TEXT    NOT NULL UNIQUE,
    actor           TEXT,
    created_at      INTEGER NOT NULL,
    CHECK (delta <> 0)
);

CREATE INDEX idx_ledger_holder ON claim_block_ledger(holder_type, holder_id);

CREATE TABLE claim_block_balance (
    holder_type   TEXT    NOT NULL,
    holder_id     TEXT    NOT NULL,
    balance       INTEGER NOT NULL,
    total_earned  INTEGER NOT NULL,
    total_spent   INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL,
    PRIMARY KEY (holder_type, holder_id),
    CHECK (balance >= 0),
    CHECK (total_earned >= 0),
    CHECK (total_spent >= 0)
);
