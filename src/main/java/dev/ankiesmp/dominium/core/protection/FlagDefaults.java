package dev.ankiesmp.dominium.core.protection;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-brede standaardwaarden voor {@code (audience, flag)} paren.
 * Latere fases kunnen dit via config overrulen en per-claim overrides
 * toevoegen; in fase 2 gebruiken we deze veilige defaults.
 *
 * <p>De invulling volgt master-prompt §9.4 (visitors), §10.1 (audience
 * volgorde en deny-default) en §13 (allies geen build-/containerrecht
 * standaard).
 */
public final class FlagDefaults {

    private final Map<Audience, Map<Flag, Decision>> matrix;

    private FlagDefaults(Map<Audience, Map<Flag, Decision>> matrix) {
        this.matrix = matrix;
    }

    public Decision get(Audience audience, Flag flag) {
        Map<Flag, Decision> row = matrix.get(audience);
        if (row == null) return Decision.PASS;
        return row.getOrDefault(flag, Decision.PASS);
    }

    public static FlagDefaults standard() {
        EnumMap<Audience, Map<Flag, Decision>> m = new EnumMap<>(Audience.class);

        // PERSONAL_OWNER: alles toegestaan (owner heeft volledig recht binnen server-caps).
        m.put(Audience.PERSONAL_OWNER, allowAll());

        // TRUSTED_PLAYER: bouwen, breaken, containers, interacties.
        Map<Flag, Decision> trusted = allowSet(EnumSet.of(
                Flag.ENTRY, Flag.BUILD, Flag.BREAK, Flag.PLACE_ENTITIES, Flag.CONTAINER,
                Flag.DOOR, Flag.TRAPDOOR, Flag.GATE, Flag.BUTTON, Flag.LEVER, Flag.PRESSURE_PLATE,
                Flag.REDSTONE_INTERACT, Flag.HARVEST, Flag.FARM_REPLANT, Flag.BUCKET,
                Flag.BONE_MEAL, Flag.SHEARS, Flag.FLINT_AND_STEEL, Flag.ANVIL_ENCHANT,
                Flag.BEACON, Flag.BED_RESPAWN, Flag.ANIMAL_INTERACT, Flag.BREED,
                Flag.VILLAGER_TRADE, Flag.MOUNT, Flag.VEHICLE, Flag.ITEM_PICKUP, Flag.ITEM_DROP,
                Flag.FISHING_PULL, Flag.MISC_INTERACT));
        m.put(Audience.TRUSTED_PLAYER, trusted);

        // PERSONAL_VISITOR: alleen simpele entry + eenvoudige interacties. Nooit
        // container/build/bucket/redstone-interact — visitor-invariant zit centraal
        // hier zodat hij niet over listeners hoeft te verspreiden.
        Map<Flag, Decision> personalVisitor = new EnumMap<>(Flag.class);
        personalVisitor.put(Flag.ENTRY, Decision.ALLOW);
        personalVisitor.put(Flag.DOOR, Decision.ALLOW);
        personalVisitor.put(Flag.TRAPDOOR, Decision.ALLOW);
        personalVisitor.put(Flag.GATE, Decision.ALLOW);
        personalVisitor.put(Flag.BUTTON, Decision.ALLOW);
        personalVisitor.put(Flag.LEVER, Decision.ALLOW);
        personalVisitor.put(Flag.PRESSURE_PLATE, Decision.ALLOW);
        personalVisitor.put(Flag.CONTAINER, Decision.DENY);
        personalVisitor.put(Flag.BUILD, Decision.DENY);
        personalVisitor.put(Flag.BREAK, Decision.DENY);
        personalVisitor.put(Flag.PLACE_ENTITIES, Decision.DENY);
        personalVisitor.put(Flag.BUCKET, Decision.DENY);
        personalVisitor.put(Flag.REDSTONE_INTERACT, Decision.DENY);
        personalVisitor.put(Flag.HOPPER_BORDER_TRANSFER, Decision.DENY);
        personalVisitor.put(Flag.ANIMAL_INTERACT, Decision.DENY);
        personalVisitor.put(Flag.ANIMAL_DAMAGE, Decision.DENY);
        personalVisitor.put(Flag.VILLAGER_TRADE, Decision.DENY);
        personalVisitor.put(Flag.MOUNT, Decision.DENY);
        personalVisitor.put(Flag.VEHICLE, Decision.DENY);
        personalVisitor.put(Flag.PVP, Decision.DENY);
        m.put(Audience.PERSONAL_VISITOR, personalVisitor);

        // KINGDOM_LEADER / CO_LEADER: gedragen zich effectief als owner voor kingdomclaims.
        m.put(Audience.KINGDOM_LEADER, allowAll());
        m.put(Audience.KINGDOM_CO_LEADER, allowAll());

        // KINGDOM_MEMBER: build/container/interacties, geen PvP standaard binnen eigen gebied.
        Map<Flag, Decision> member = allowSet(EnumSet.of(
                Flag.ENTRY, Flag.BUILD, Flag.BREAK, Flag.PLACE_ENTITIES, Flag.CONTAINER,
                Flag.DOOR, Flag.TRAPDOOR, Flag.GATE, Flag.BUTTON, Flag.LEVER, Flag.PRESSURE_PLATE,
                Flag.REDSTONE_INTERACT, Flag.HARVEST, Flag.FARM_REPLANT, Flag.BUCKET,
                Flag.BONE_MEAL, Flag.SHEARS, Flag.FLINT_AND_STEEL, Flag.ANVIL_ENCHANT,
                Flag.BEACON, Flag.BED_RESPAWN, Flag.ANIMAL_INTERACT, Flag.BREED,
                Flag.VILLAGER_TRADE, Flag.MOUNT, Flag.VEHICLE, Flag.ITEM_PICKUP, Flag.ITEM_DROP,
                Flag.FISHING_PULL, Flag.MISC_INTERACT));
        m.put(Audience.KINGDOM_MEMBER, member);

        // KINGDOM_VISITOR: entry + veilige door/button/lever, expliciet géén container en géén
        // storage-achtige indirecte interacties (master-prompt §9.4).
        Map<Flag, Decision> visitor = new EnumMap<>(Flag.class);
        visitor.put(Flag.ENTRY, Decision.ALLOW);
        visitor.put(Flag.DOOR, Decision.ALLOW);
        visitor.put(Flag.TRAPDOOR, Decision.ALLOW);
        visitor.put(Flag.GATE, Decision.ALLOW);
        visitor.put(Flag.BUTTON, Decision.ALLOW);
        visitor.put(Flag.LEVER, Decision.ALLOW);
        visitor.put(Flag.PRESSURE_PLATE, Decision.ALLOW);
        visitor.put(Flag.CONTAINER, Decision.DENY);
        visitor.put(Flag.BUILD, Decision.DENY);
        visitor.put(Flag.BREAK, Decision.DENY);
        visitor.put(Flag.PLACE_ENTITIES, Decision.DENY);
        visitor.put(Flag.BUCKET, Decision.DENY);
        visitor.put(Flag.REDSTONE_INTERACT, Decision.DENY);
        visitor.put(Flag.HOPPER_BORDER_TRANSFER, Decision.DENY);
        m.put(Audience.KINGDOM_VISITOR, visitor);

        // ALLY: entry, maar géén build/container/PvP (master-prompt §13).
        Map<Flag, Decision> ally = new EnumMap<>(Flag.class);
        ally.put(Flag.ENTRY, Decision.ALLOW);
        ally.put(Flag.DOOR, Decision.ALLOW);
        ally.put(Flag.TRAPDOOR, Decision.ALLOW);
        ally.put(Flag.GATE, Decision.ALLOW);
        ally.put(Flag.BUILD, Decision.DENY);
        ally.put(Flag.BREAK, Decision.DENY);
        ally.put(Flag.CONTAINER, Decision.DENY);
        ally.put(Flag.PVP, Decision.DENY);
        m.put(Audience.ALLY, ally);

        // TRUCE: als ally, iets strikter — geen aanvallen, wel entry.
        Map<Flag, Decision> truce = new EnumMap<>(Flag.class);
        truce.put(Flag.ENTRY, Decision.ALLOW);
        truce.put(Flag.PVP, Decision.DENY);
        truce.put(Flag.BUILD, Decision.DENY);
        truce.put(Flag.BREAK, Decision.DENY);
        truce.put(Flag.CONTAINER, Decision.DENY);
        m.put(Audience.TRUCE_PARTNER, truce);

        // NEUTRAL / RIVAL: entry standaard toegestaan, verder alles deny-default.
        Map<Flag, Decision> neutral = new EnumMap<>(Flag.class);
        neutral.put(Flag.ENTRY, Decision.ALLOW);
        m.put(Audience.NEUTRAL, neutral);
        m.put(Audience.RIVAL, neutral);

        // ENEMY: entry expliciet toegestaan (anders hebben oorlogen geen zin);
        // build/break/container blijven deny tenzij in raid-window.
        Map<Flag, Decision> enemy = new EnumMap<>(Flag.class);
        enemy.put(Flag.ENTRY, Decision.ALLOW);
        enemy.put(Flag.PVP, Decision.ALLOW);
        m.put(Audience.ENEMY, enemy);

        // PUBLIC: entry toegestaan, alles verder deny.
        Map<Flag, Decision> pub = new EnumMap<>(Flag.class);
        pub.put(Flag.ENTRY, Decision.ALLOW);
        m.put(Audience.PUBLIC, pub);

        return new FlagDefaults(m);
    }

    private static Map<Flag, Decision> allowAll() {
        Map<Flag, Decision> row = new EnumMap<>(Flag.class);
        for (Flag f : Flag.values()) row.put(f, Decision.ALLOW);
        return row;
    }

    private static Map<Flag, Decision> allowSet(Set<Flag> allowed) {
        Map<Flag, Decision> row = new EnumMap<>(Flag.class);
        for (Flag f : allowed) row.put(f, Decision.ALLOW);
        return row;
    }
}
