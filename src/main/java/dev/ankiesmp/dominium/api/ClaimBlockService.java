package dev.ankiesmp.dominium.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publiek contract voor het toekennen, intrekken en opvragen van
 * claim blocks. Bewust ontworpen zonder Bukkit-types zodat externe
 * plugins (chatminigames, vote-plugins, quests) via een stabiele API
 * beloningen kunnen uitkeren.
 *
 * <p>Alle mutaties zijn idempotent op basis van {@code idempotencyKey}.
 * Een tweede aanroep met dezelfde key als een eerder succesvolle mutatie
 * mag {@link ClaimBlockResult#alreadyApplied()} teruggeven maar boekt
 * nooit opnieuw. Retries van dezelfde reward mogen dus veilig gebeuren.
 */
public interface ClaimBlockService {

    /**
     * Verhoog het saldo van {@code playerId} met {@code amount} claim blocks.
     *
     * @param playerId       target speler
     * @param amount         positief geheel getal
     * @param reason         auditbare reden
     * @param externalRef    optionele externe reference (bv. minigame-id); mag null zijn
     * @param idempotencyKey verplicht — voorkomt dubbele boekingen
     */
    CompletableFuture<ClaimBlockResult> grantToPlayer(
            UUID playerId,
            long amount,
            ClaimBlockReason reason,
            String externalRef,
            UUID idempotencyKey);

    CompletableFuture<ClaimBlockResult> revokeFromPlayer(
            UUID playerId,
            long amount,
            ClaimBlockReason reason,
            String externalRef,
            UUID idempotencyKey);

    CompletableFuture<Long> getPlayerBalance(UUID playerId);

    CompletableFuture<Long> getKingdomBalance(UUID kingdomId);
}
