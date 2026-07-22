package dev.ankiesmp.dominium.paper.bank;

import dev.ankiesmp.dominium.core.bank.Money;
import dev.ankiesmp.dominium.core.bank.VaultAdapter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflective Vault-adapter. Ontwijkt compile-time dependency op Vault door
 * de Economy-service via {@code Bukkit.getServicesManager()} en reflectie
 * te resolven. Zonder Vault of zonder economy-provider levert
 * {@link #detect} de {@link VaultAdapter#NO_OP}-variant op.
 */
public final class BukkitVaultAdapter implements VaultAdapter {

    private final Object economy;
    private final Method withdrawMethod;
    private final Method depositMethod;
    private final String providerName;

    private BukkitVaultAdapter(Object economy, Method withdrawMethod, Method depositMethod,
                               String providerName) {
        this.economy = economy;
        this.withdrawMethod = withdrawMethod;
        this.depositMethod = depositMethod;
        this.providerName = providerName;
    }

    /**
     * @return een echte adapter wanneer Vault + een geregistreerde
     *  Economy-provider aanwezig zijn; anders {@link VaultAdapter#NO_OP}.
     */
    public static VaultAdapter detect(Logger log) {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            var servicesManager = Bukkit.getServer().getServicesManager();
            var reg = servicesManager.getRegistration(
                    (Class<Object>) (Class<?>) economyClass);
            if (reg == null) {
                log.info("Vault present but no Economy provider registered — using NO_OP adapter.");
                return VaultAdapter.NO_OP;
            }
            Object provider = reg.getProvider();
            Method w = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            Method d = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            String name = provider.getClass().getSimpleName();
            log.info("Vault economy adapter active: provider={}", name);
            return new BukkitVaultAdapter(provider, w, d, name);
        } catch (ClassNotFoundException ex) {
            log.info("Vault plugin not installed — using NO_OP economy adapter.");
            return VaultAdapter.NO_OP;
        } catch (ReflectiveOperationException ex) {
            log.warn("Vault present but reflective binding failed ({}): using NO_OP adapter.",
                    ex.toString());
            return VaultAdapter.NO_OP;
        } catch (RuntimeException ex) {
            log.warn("Vault detection failed: {} — using NO_OP adapter.", ex.toString());
            return VaultAdapter.NO_OP;
        }
    }

    @Override public boolean available() { return true; }

    @Override
    public boolean withdrawPlayer(UUID playerId, long amountMinor) {
        return call(withdrawMethod, playerId, amountMinor);
    }

    @Override
    public boolean depositPlayer(UUID playerId, long amountMinor) {
        return call(depositMethod, playerId, amountMinor);
    }

    private boolean call(Method m, UUID playerId, long amountMinor) {
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(playerId);
            Object response = m.invoke(economy, op, Money.minorToMajor(amountMinor));
            // Vault EconomyResponse.transactionSuccess() → boolean.
            Method success = response.getClass().getMethod("transactionSuccess");
            Object ok = success.invoke(response);
            return ok instanceof Boolean b && b;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public String providerName() { return providerName; }
}
