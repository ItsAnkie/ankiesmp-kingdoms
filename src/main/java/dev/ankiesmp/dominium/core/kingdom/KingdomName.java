package dev.ankiesmp.dominium.core.kingdom;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure naam-validatie + normalisatie. Display name blijft zoals de speler
 * hem typt; {@link #normalize} produceert de unique-key voor de DB.
 */
public final class KingdomName {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_\\- ]+");

    private KingdomName() {}

    public static ValidationResult validate(String raw, int minLen, int maxLen) {
        if (raw == null) return ValidationResult.error("Name is required.");
        String trimmed = raw.trim();
        if (trimmed.length() < minLen) {
            return ValidationResult.error("Name must be at least " + minLen + " chars.");
        }
        if (trimmed.length() > maxLen) {
            return ValidationResult.error("Name must be at most " + maxLen + " chars.");
        }
        if (!SAFE.matcher(trimmed).matches()) {
            return ValidationResult.error(
                    "Name may only contain letters, digits, spaces, underscore or dash.");
        }
        return ValidationResult.ok(trimmed);
    }

    /** case-insensitive dedupe: `KingLy` en `kingly` normaliseren beide naar `kingly`. */
    public static String normalize(String display) {
        return display.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public record ValidationResult(boolean ok, String displayName, String error) {
        public static ValidationResult ok(String display) { return new ValidationResult(true, display, null); }
        public static ValidationResult error(String msg)  { return new ValidationResult(false, null, msg); }
    }
}
