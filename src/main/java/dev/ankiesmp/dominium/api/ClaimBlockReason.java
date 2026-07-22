package dev.ankiesmp.dominium.api;

/**
 * Reden-enum voor mutaties in de claim-block ledger. Iedere entry heeft
 * verplicht een reason zodat audit en refund/compensatie mogelijk zijn.
 */
public enum ClaimBlockReason {
    INITIAL_GRANT,
    ACTIVE_PLAY_EARN,
    ADMIN_GRANT,
    ADMIN_REVOKE,
    EXTERNAL_REWARD,
    PERSONAL_CLAIM_SPEND,
    PERSONAL_CLAIM_REFUND,
    KINGDOM_CLAIM_SPEND,
    KINGDOM_CLAIM_REFUND,
    DONATION_OUT,
    DONATION_IN,
    AUTOMATIC_DONATION_ALLOCATION,
    PENDING_WITHDRAWAL,
    WITHDRAWAL_COMPLETION,
    MAINTENANCE_CORRECTION,
    MEMBERSHIP_TRANSFER_CORRECTION,
    MIGRATION
}
