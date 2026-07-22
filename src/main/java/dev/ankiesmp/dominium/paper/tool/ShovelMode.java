package dev.ankiesmp.dominium.paper.tool;

/**
 * Actieve modus van de gemarkeerde golden shovel. Wordt in de PDC van de
 * tool bewaard zodat een speler geen aparte GUI hoeft te openen voor een
 * modewissel.
 *
 * <p>Kingdom-modi zijn nog niet aangesloten — fase 4 activeert
 * {@link #KINGDOM_CLAIM}. Tot die tijd geeft de listener een duidelijke
 * fout wanneer een speler naar KINGDOM_CLAIM schakelt.
 */
public enum ShovelMode {
    PERSONAL_CLAIM,
    KINGDOM_CLAIM,
    INSPECT
}
