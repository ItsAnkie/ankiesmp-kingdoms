package dev.ankiesmp.dominium.storage.migrations;

import java.util.List;

/**
 * Single source of truth voor de bekende Dominium-migraties.
 * Iedere nieuwe {@code V{n}__*.sql} moet hier expliciet worden
 * geregistreerd — anders wordt hij niet toegepast.
 *
 * <p>Deze registry vervangt directory-enumeratie omdat het bundelen
 * van jar-directory-entries niet gegarandeerd is; door de paden hier
 * te noemen crasht startup direct als iets ontbreekt.
 */
public final class MigrationRegistry {

    public static final List<String> RESOURCE_PATHS = List.of(
            "db/migrations/V1__initial_claim_block_ledger.sql",
            "db/migrations/V2__bank_operation_journal.sql",
            "db/migrations/V3__claims.sql",
            "db/migrations/V4__access_and_activity.sql",
            "db/migrations/V5__kingdoms.sql",
            // V6 (single-claim-per-owner unique index) wordt NIET via de gewone
            // migratie-runner geplaatst — zie SingleClaimIndexInstaller.
            "db/migrations/V7__kingdom_bank_and_regions.sql"
    );

    private MigrationRegistry() {}
}
