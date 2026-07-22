package dev.ankiesmp.dominium.core.bank;

import java.util.UUID;

/**
 * Soft-dep adapter naar Vault. Concrete Bukkit-implementatie leeft in
 * fase 5.x; hier alleen de interface + {@link NoOp}-variant zodat Dominium
 * zonder Vault gewoon blijft draaien.
 */
public interface VaultAdapter {

    boolean available();

    /** @return {@code true} als de speler genoeg had en het bedrag is afgeschreven. */
    boolean withdrawPlayer(UUID playerId, long amountMinor);

    /** @return {@code true} bij succesvolle deposit. */
    boolean depositPlayer(UUID playerId, long amountMinor);

    VaultAdapter NO_OP = new VaultAdapter() {
        @Override public boolean available() { return false; }
        @Override public boolean withdrawPlayer(UUID playerId, long amountMinor) { return false; }
        @Override public boolean depositPlayer(UUID playerId, long amountMinor) { return false; }
    };
}
