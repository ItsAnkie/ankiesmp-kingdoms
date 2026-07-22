package dev.ankiesmp.dominium.core.protection;

/**
 * Rauwe uitkomst van één laag in de effectieve-permission-stack.
 *
 * <p>{@link #PASS} betekent: deze laag doet geen uitspraak; delegeer
 * naar de volgende laag. Bij gelijke specificiteit wint deny —
 * {@link AccessResolver} verwerkt dat.
 */
public enum Decision {
    ALLOW,
    DENY,
    PASS
}
