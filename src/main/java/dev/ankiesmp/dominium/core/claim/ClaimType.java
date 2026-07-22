package dev.ankiesmp.dominium.core.claim;

/**
 * Top-level types van beschermd territorium. Kingdom sub-types (core/
 * outpost) worden op een kingdomclaim geregistreerd via aparte velden;
 * er zijn géén plots of subclaims (§6.4).
 */
public enum ClaimType {
    PERSONAL,
    KINGDOM,
    ADMIN
}
