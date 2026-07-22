package dev.ankiesmp.dominium.core.ledger;

import dev.ankiesmp.dominium.core.common.HolderKey;

import java.util.Optional;

/**
 * Append-only, idempotent boekhouding van claim blocks.
 *
 * <p>Invarianten die iedere implementatie moet handhaven:
 * <ul>
 *   <li>Balans is nooit negatief. Bij een spend (delta &lt; 0) die de
 *       balans zou laten dalen onder nul retourneert {@link #post} een
 *       {@link PostingOutcome.Kind#INSUFFICIENT_BALANCE}.</li>
 *   <li>Een tweede aanroep met dezelfde {@link PostingRequest#idempotencyKey()}
 *       als een eerder succesvolle boeking retourneert
 *       {@link PostingOutcome.Kind#ALREADY_APPLIED} zonder opnieuw te
 *       boeken.</li>
 *   <li>De {@link BalanceSnapshot} is reproduceerbaar uit de som van
 *       alle {@link LedgerEntry#delta()}.</li>
 *   <li>Boeking + saldo-update gebeuren binnen één transactie.</li>
 * </ul>
 *
 * <p>Alle methoden zijn <b>blocking</b> — de bootstrap draait ze op een
 * dedicated database-executor, niet op de serverthread.
 */
public interface ClaimBlockLedger {

    PostingOutcome post(PostingRequest request);

    /**
     * Atomair beide posts binnen één DB-transactie. Als een van beide faalt,
     * commit niets. Idempotent op de {@code idempotencyKey}-basis: retry met
     * dezelfde base-key retourneert {@link PostingOutcome.Kind#ALREADY_APPLIED}
     * voor beide posts.
     *
     * @param debit   PERSONAL/KINGDOM holder waarvan afgeschreven wordt
     *                (delta &lt; 0 verplicht)
     * @param credit  ontvangende holder (delta &gt; 0 verplicht)
     * @return {@link TransferOutcome} met de nieuwe balansen of een
     *         {@link TransferOutcome.Kind#INSUFFICIENT_BALANCE}.
     */
    TransferOutcome atomicTransfer(PostingRequest debit, PostingRequest credit);

    Optional<BalanceSnapshot> balance(HolderKey holder);

    /**
     * Handig helper: geeft nul-saldo terug wanneer een holder nog nooit
     * geboekt is. Nooit {@code null}.
     */
    BalanceSnapshot balanceOrZero(HolderKey holder);
}
