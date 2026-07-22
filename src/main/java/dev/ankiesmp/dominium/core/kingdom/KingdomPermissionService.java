package dev.ankiesmp.dominium.core.kingdom;

import java.util.Objects;

/**
 * Centrale rolcheck. Alle commandhandlers en application-services roepen
 * dit aan; niemand doet zelf een {@code role == LEADER}-vergelijking.
 */
public final class KingdomPermissionService {

    private KingdomPermissionService() {}

    public enum Action {
        INVITE,
        KICK_MEMBER,           // gewone member kicken (niet co-leader)
        KICK_CO_LEADER,        // co-leader kicken
        PROMOTE_TO_CO_LEADER,  // member → co-leader
        DEMOTE_CO_LEADER,      // co-leader → member
        TRANSFER_LEADERSHIP,
        DISBAND,
        MANAGE_VISITORS,
        MANAGE_KINGDOM_CLAIMS,
        MANAGE_KINGDOM_BANK
    }

    public static boolean allowed(KingdomRole actor, Action action) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(action);
        return switch (action) {
            case INVITE, MANAGE_VISITORS, MANAGE_KINGDOM_CLAIMS ->
                    actor == KingdomRole.LEADER || actor == KingdomRole.CO_LEADER;
            case KICK_MEMBER ->
                    actor == KingdomRole.LEADER || actor == KingdomRole.CO_LEADER;
            case KICK_CO_LEADER, PROMOTE_TO_CO_LEADER, DEMOTE_CO_LEADER,
                 TRANSFER_LEADERSHIP, DISBAND, MANAGE_KINGDOM_BANK ->
                    actor == KingdomRole.LEADER;
        };
    }
}
