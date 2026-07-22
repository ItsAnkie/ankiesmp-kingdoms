package dev.ankiesmp.dominium.core.bank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence-contract voor kingdom-bank + operation journal. Alle
 * lifecycle-mutaties zijn transactioneel binnen één DB-call.
 */
public interface BankStore {

    long balance(UUID kingdomId);

    /**
     * Atomair: schrijf journal-record + wijzig saldo. Wanneer amountDelta
     * negatief is en het saldo zou onder 0 vallen, retourneert de call
     * {@code false} zonder mutaties.
     */
    boolean applyBalanceChangeWithJournal(BankOperation op, long amountDelta, long maxBalance);

    void updateOperationState(UUID correlationId, BankOperation.State newState,
                              String failureReason, long updatedAtEpochMillis);

    Optional<BankOperation> findOperation(UUID correlationId);

    /** Voor recovery + admin-command. */
    List<BankOperation> listIncomplete();

    /** Voor admin-command per kingdom. */
    List<BankOperation> listForKingdom(UUID kingdomId, int limit);

    /**
     * Atomair: bank-debit + callback binnen dezelfde tx. Wanneer de callback
     * {@code false} teruggeeft of gooit, rolt de hele transactie terug
     * (bank ongewijzigd).
     */
    boolean atomicDebitWithLedger(BankOperation op, long amountMinor, long maxBalance,
                                  LedgerPostInTx ledgerCallback);

    @FunctionalInterface
    interface LedgerPostInTx {
        boolean apply(java.sql.Connection conn) throws java.sql.SQLException;
    }
}
