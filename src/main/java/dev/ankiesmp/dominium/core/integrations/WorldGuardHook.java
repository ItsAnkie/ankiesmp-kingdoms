package dev.ankiesmp.dominium.core.integrations;

import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.common.WorldRef;

import java.util.List;
import java.util.Optional;

/**
 * Soft-dep hook naar WorldGuard. Concrete Bukkit-implementatie leeft in
 * fase 5.x; hier alleen de interface + {@link #NO_OP}-variant zodat
 * Dominium zonder WorldGuard blijft werken.
 */
public interface WorldGuardHook {

    boolean available();

    /**
     * @return optionele region-ID die overlapt met de voorgestelde geometry.
     *  Empty betekent "geen conflict → claim/uitbreiding mag door".
     */
    Optional<String> firstBlockingRegion(WorldRef world, List<ClaimRectangle> proposed,
                                         boolean ignoreGlobalRegion);

    WorldGuardHook NO_OP = new WorldGuardHook() {
        @Override public boolean available() { return false; }
        @Override public Optional<String> firstBlockingRegion(WorldRef w, List<ClaimRectangle> p,
                                                              boolean ignoreGlobal) {
            return Optional.empty();
        }
    };
}
