-- Journaal van externe economy-operaties (Vault etc.). Het schema staat
-- klaar zodat fase 5 het volledig kan invullen. De state-machine is:
--   PENDING_EXTERNAL -> EXTERNAL_APPLIED -> COMMITTED
--                      \--> COMPENSATION_REQUIRED -> COMPENSATED

CREATE TABLE bank_operation_journal (
    correlation_id TEXT PRIMARY KEY,
    kingdom_id     TEXT    NOT NULL,
    kind           TEXT    NOT NULL,          -- DEPOSIT, WITHDRAW, RESERVE, RELEASE, ESCROW_IN, PAYOUT
    amount_cents   INTEGER NOT NULL,          -- geldbedragen in kleinste eenheid
    state          TEXT    NOT NULL,          -- PENDING_EXTERNAL, EXTERNAL_APPLIED, COMMITTED, COMPENSATION_REQUIRED, COMPENSATED
    reason         TEXT    NOT NULL,
    actor          TEXT,
    external_ref   TEXT,
    created_at     INTEGER NOT NULL,
    updated_at     INTEGER NOT NULL,
    CHECK (amount_cents > 0)
);

CREATE INDEX idx_bank_journal_kingdom ON bank_operation_journal(kingdom_id);
CREATE INDEX idx_bank_journal_state ON bank_operation_journal(state);
