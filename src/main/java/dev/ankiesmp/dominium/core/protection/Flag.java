package dev.ankiesmp.dominium.core.protection;

/**
 * Gedragflag die een claim per {@link Audience} kan toestaan of weigeren.
 *
 * <p>De set komt uit master-prompt §10.2. Sommige flags treden pas in
 * werking wanneer bijbehorende listeners bestaan; ze zijn hier alvast
 * gedefinieerd zodat latere fases geen enum-schema hoeven te breken.
 */
public enum Flag {
    // Beweging / entry
    ENTRY,
    // Bouwen
    BUILD,
    BREAK,
    PLACE_ENTITIES,
    // Interactie met blocks
    CONTAINER,
    DOOR,
    TRAPDOOR,
    GATE,
    BUTTON,
    LEVER,
    PRESSURE_PLATE,
    REDSTONE_INTERACT,
    HARVEST,
    FARM_REPLANT,
    TRAMPLE,
    BUCKET,
    BONE_MEAL,
    SHEARS,
    FLINT_AND_STEEL,
    ANVIL_ENCHANT,
    BEACON,
    BED_RESPAWN,
    // Combat
    PVP,
    PROJECTILES,
    // Entities
    ANIMAL_INTERACT,
    ANIMAL_DAMAGE,
    BREED,
    VILLAGER_TRADE,
    MOUNT,
    VEHICLE,
    PET_PROTECT,
    ITEM_PICKUP,
    ITEM_DROP,
    FISHING_PULL,
    // Teleport
    TELEPORT,
    ENDER_PEARL,
    CHORUS_FRUIT,
    PORTAL,
    // Environment / cross-border
    EXPLOSION_TNT,
    EXPLOSION_CREEPER,
    EXPLOSION_OTHER,
    FIRE_SPREAD,
    LIQUID_FLOW,
    PISTON,
    DISPENSER,
    HOPPER_BORDER_TRANSFER,
    MOB_GRIEFING,
    HOSTILE_MOB_SPAWN,
    VILLAGE_RAID,
    FROST_WALKER,
    SNOW_TRAILS,
    MISC_INTERACT
}
