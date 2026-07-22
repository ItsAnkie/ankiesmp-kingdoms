package dev.ankiesmp.dominium.core.protection;

/**
 * Doelgroep waarop een flag-instelling geldt. De volgorde weerspiegelt
 * ruwweg de specificiteitsvolgorde van master-prompt §10.1 — een meer-
 * specifieke audience wint van een minder-specifieke, en bij gelijkspel
 * wint deny (afgehandeld in {@link AccessResolver}).
 */
public enum Audience {
    /** Persoonlijke claim eigenaar (owner van type PERSONAL). */
    PERSONAL_OWNER,
    /** Individueel getrust persoon op een persoonlijke claim. */
    TRUSTED_PLAYER,
    /** Visitor op een persoonlijke claim (aparte lijst; nooit lid). */
    PERSONAL_VISITOR,
    /** Leader van het bezittende kingdom (fase &gt;2). */
    KINGDOM_LEADER,
    /** Co-leader van het bezittende kingdom (fase &gt;2). */
    KINGDOM_CO_LEADER,
    /** Gewone member van het bezittende kingdom (fase &gt;2). */
    KINGDOM_MEMBER,
    /** Visitor van het bezittende kingdom (fase &gt;2). */
    KINGDOM_VISITOR,
    /** Kingdom met ALLY-relatie tot bezitter (fase &gt;5). */
    ALLY,
    /** Kingdom met TRUCE-relatie tot bezitter (fase &gt;5). */
    TRUCE_PARTNER,
    /** Kingdom met NEUTRAL-relatie tot bezitter (fase &gt;5). */
    NEUTRAL,
    /** Kingdom met RIVAL-relatie tot bezitter (fase &gt;5). */
    RIVAL,
    /** Kingdom in ACTIEVE oorlog met bezitter (fase &gt;5). */
    ENEMY,
    /** Alle overige actors, incl. anonymous. */
    PUBLIC
}
