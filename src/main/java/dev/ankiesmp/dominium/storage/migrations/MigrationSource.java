package dev.ankiesmp.dominium.storage.migrations;

import java.util.List;

/**
 * Bron van de geordende lijst {@link Migration}s die op de database
 * toegepast moeten worden. Implementaties zijn deterministisch: ze
 * geven bij iedere aanroep dezelfde lijst in dezelfde volgorde, en
 * gooien een startup-exception zodra een geregistreerde migratie niet
 * geladen kan worden.
 */
public interface MigrationSource {

    /**
     * @return alle bekende migraties, gesorteerd op {@link Migration#version()}.
     * @throws IllegalStateException bij een ontbrekend of dubbel versienummer.
     */
    List<Migration> load();
}
