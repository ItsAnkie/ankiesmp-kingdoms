package dev.ankiesmp.dominium.paper.gui;

/**
 * Enum voor click-actions op GUI-items. Wordt in PDC opgeslagen zodat
 * click-recognition nooit van displayname afhangt.
 *
 * <p>Bank/claim-block-preset acties zijn statisch — de daadwerkelijk
 * uitgevoerde hoeveelheid komt uit {@link BankPresets}. Alle bank-clicks
 * worden op de dbExecutor uitgevoerd en re-valideren op dat moment
 * membership, capability, balance en de GUI-generation.
 */
public enum KingdomGuiAction {
    NOOP,
    CREATE_HINT,
    LEAVE,
    DISBAND,
    MEMBERS_PANEL,
    VISITORS_PANEL,
    INVITES_PANEL,
    BANK_PANEL,
    BANK_REFRESH,
    BANK_DEPOSIT_10,
    BANK_DEPOSIT_100,
    BANK_DEPOSIT_1000,
    BANK_WITHDRAW_10,
    BANK_WITHDRAW_100,
    BANK_WITHDRAW_1000,
    POOL_CONTRIBUTE_10,
    POOL_CONTRIBUTE_100,
    POOL_CONTRIBUTE_1000,
    POOL_BUY_10,
    POOL_BUY_100,
    POOL_BUY_1000,
    CLOSE
}
