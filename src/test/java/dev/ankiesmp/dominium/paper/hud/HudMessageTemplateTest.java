package dev.ankiesmp.dominium.paper.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class HudMessageTemplateTest {

    private final org.slf4j.Logger log = LoggerFactory.getLogger("test");

    // HM-001 — <owner> placeholder resolveert.
    @Test
    void ownerPlaceholderResolves() {
        var tpl = new HudMessageTemplate("<green><owner>'s claim <gray>(Owner)", log);
        assertTrue(flatten(tpl.render("7040K", null)).contains("7040K's claim (Owner)"));
    }

    // HM-002 — <kingdom> placeholder resolveert.
    @Test
    void kingdomPlaceholderResolves() {
        var tpl = new HudMessageTemplate("<green><kingdom> <gray>(Member)", log);
        assertTrue(flatten(tpl.render(null, "Green")).contains("Green (Member)"));
    }

    // HM-003 — malformed template valt terug op plain string, geen exceptie.
    @Test
    void malformedTemplateFallsBackGracefully() {
        var tpl = new HudMessageTemplate("<green<<>><owner>", log);
        var comp = tpl.render("7040K", null);
        assertNotNull(comp);
        assertTrue(flatten(comp).contains("7040K"));
    }

    private static String flatten(Component c) {
        StringBuilder sb = new StringBuilder();
        appendPlain(c, sb);
        return sb.toString();
    }

    private static void appendPlain(Component c, StringBuilder sb) {
        if (c instanceof TextComponent tc) sb.append(tc.content());
        for (Component child : c.children()) appendPlain(child, sb);
    }
}
