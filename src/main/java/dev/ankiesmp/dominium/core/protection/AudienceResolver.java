package dev.ankiesmp.dominium.core.protection;

import dev.ankiesmp.dominium.core.claim.Claim;

import java.util.UUID;

/**
 * Bepaalt welke {@link Audience} een gegeven actor heeft ten opzichte
 * van een claim. Wordt geïnjecteerd zodat latere fases (kingdoms,
 * diplomatie) de logica kunnen uitbreiden zonder de protection-core
 * te breken.
 *
 * <p>{@code actorId} mag {@code null} zijn wanneer de actor onbekend
 * of niet-menselijk is (piston, mob-griefing); die vallen dan op
 * {@link Audience#PUBLIC}.
 */
public interface AudienceResolver {
    Audience resolve(Claim claim, UUID actorId);
}
