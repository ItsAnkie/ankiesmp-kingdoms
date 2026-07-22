package dev.ankiesmp.dominium.paper.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.util.Objects;

/**
 * Compileert een MiniMessage-template met {@code <owner>} / {@code <kingdom>}
 * placeholders. Fout in de template zet de plugin NIET uit — we tonen een
 * plain fallback en loggen de fout één keer.
 */
public final class HudMessageTemplate {

    private final String template;
    private final Logger log;
    private volatile boolean warned;

    public HudMessageTemplate(String template, Logger log) {
        this.template = Objects.requireNonNull(template);
        this.log = Objects.requireNonNull(log);
    }

    public Component render(String ownerName, String kingdomName) {
        TagResolver resolvers = TagResolver.resolver(
                Placeholder.parsed("owner",   ownerName == null   ? "" : ownerName),
                Placeholder.parsed("kingdom", kingdomName == null ? "" : kingdomName),
                TagResolver.resolver("player", (args, ctx) -> Tag.preProcessParsed(
                        ownerName == null ? "" : ownerName)));
        try {
            return MiniMessage.miniMessage().deserialize(template, resolvers);
        } catch (RuntimeException ex) {
            if (!warned) {
                warned = true;
                log.warn("Malformed MiniMessage template '{}': {}", template, ex.getMessage());
            }
            String plain = template
                    .replace("<owner>", ownerName == null ? "" : ownerName)
                    .replace("<kingdom>", kingdomName == null ? "" : kingdomName);
            return Component.text(plain);
        }
    }

    public String raw() { return template; }
}
