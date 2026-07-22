package dev.ankiesmp.dominium.storage.claim;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimGeometry;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.ClaimService;
import dev.ankiesmp.dominium.core.ledger.PostingOutcome;
import dev.ankiesmp.dominium.core.ledger.PostingRequest;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.ledger.SqlClaimBlockLedger;

import java.util.Optional;
import java.util.UUID;

/**
 * Dunne bridge tussen {@link ClaimService.ClaimStore} en de JDBC-repo.
 *
 * <p>Wanneer een {@link Database} + {@link SqlClaimBlockLedger} zijn
 * meegegeven, ondersteunt deze store het atomic-expand-pad
 * ({@link #expandAtomic}): ledger-spend + WG-check + region-replace +
 * bounds + revision in exact één transactie op één {@code Connection}.
 * Bij failure rolt alles terug — er wordt géén compensatie-boeking
 * gedaan. Zonder deze twee dependencies gooit {@code expandAtomic}
 * bewust: dat pad hoort in productie nooit door een niet-atomair pad
 * te worden vervangen.
 */
public final class SqlClaimStore implements ClaimService.ClaimStore {

    private final SqlClaimRepository repository;
    private final Database database;
    private final SqlClaimBlockLedger ledger;

    /** Legacy ctor voor tests die het atomic pad niet nodig hebben. */
    public SqlClaimStore(SqlClaimRepository repository) {
        this(repository, null, null);
    }

    public SqlClaimStore(SqlClaimRepository repository, Database database,
                         SqlClaimBlockLedger ledger) {
        this.repository = repository;
        this.database = database;
        this.ledger = ledger;
    }

    @Override public void insert(Claim claim) { repository.insert(claim); }

    @Override public void updateRectangle(UUID claimId, ClaimRectangle oldRect, ClaimRectangle newRect) {
        repository.updateRectangle(claimId, oldRect, newRect);
    }

    @Override
    public void replaceGeometry(UUID claimId, ClaimGeometry oldGeometry, ClaimGeometry newGeometry) {
        repository.replaceGeometry(claimId, oldGeometry, newGeometry, "GEOMETRY_UPDATE");
    }

    @Override public void delete(UUID claimId) { repository.delete(claimId); }

    @Override
    public ClaimService.ExpandAtomicOutcome expandAtomic(ClaimService.ExpandAtomicRequest req) {
        if (database == null || ledger == null) {
            throw new UnsupportedOperationException(
                    "SqlClaimStore constructed without Database/SqlClaimBlockLedger — "
                            + "atomic expand is not available. Wire the full ctor in production.");
        }
        try {
            return database.withTransaction(conn -> {
                // 1) WG-check binnen de tx: als WG blokkeert → RollbackSignal.
                Optional<String> wgBlock = req.wgCheck().get();
                if (wgBlock.isPresent()) {
                    throw new RollbackSignal("worldguard region " + wgBlock.get());
                }
                // 2) Ledger-spend (alleen als delta > 0; delta = 0 → skip post).
                PostingOutcome spend = null;
                if (req.extraCost() > 0) {
                    spend = ledger.postInConnection(conn, PostingRequest.builder()
                            .holder(req.holder())
                            .delta(-req.extraCost())
                            .reason(req.spendReason())
                            .idempotencyKey(req.idempotencyKey())
                            .reference("claim:expand-atomic")
                            .build());
                    if (spend.kind() == PostingOutcome.Kind.INSUFFICIENT_BALANCE) {
                        throw new RollbackSignal("insufficient claim blocks");
                    }
                }
                // 3) Region-replace + bounds + revision — één transactie.
                repository.replaceGeometryInConnection(conn, req.claimId(),
                        req.oldGeometry(), req.newGeometry(), "EXPAND");
                return ClaimService.ExpandAtomicOutcome.ok(spend);
            });
        } catch (RollbackSignal rs) {
            return ClaimService.ExpandAtomicOutcome.rejected(rs.getMessage());
        }
    }

    /** Sentinel om binnen {@link Database#withTransaction} volledig te rollbacken. */
    private static final class RollbackSignal extends RuntimeException {
        private static final long serialVersionUID = 1L;
        RollbackSignal(String message) { super(message, null, false, false); }
    }
}
