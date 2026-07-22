package dev.ankiesmp.dominium.core.claim;

import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detecteert bij startup owners die na V6 nog meerdere claims hebben.
 * Verwijdert niets — logt alleen zodat een admin ze via
 * {@code /dominium claims inspect} en {@code /dominium claims delete}
 * bewust kan opschonen.
 */
public final class DuplicateOwnerAudit {

    private DuplicateOwnerAudit() {}

    public static List<OwnerConflict> scan(ClaimIndex index) {
        Map<Key, List<Claim>> grouped = new HashMap<>();
        for (Claim c : index.all()) {
            if (c.owner().type() == ClaimType.ADMIN) continue;
            grouped.computeIfAbsent(new Key(c.owner().type(), c.owner().id()),
                    k -> new ArrayList<>()).add(c);
        }
        List<OwnerConflict> out = new ArrayList<>();
        for (var e : grouped.entrySet()) {
            if (e.getValue().size() > 1) {
                out.add(new OwnerConflict(e.getKey().type(), e.getKey().ownerId(),
                        e.getValue().stream().map(Claim::id).toList()));
            }
        }
        return out;
    }

    private record Key(ClaimType type, UUID ownerId) {}

    public record OwnerConflict(ClaimType type, UUID ownerId, List<UUID> claimIds) {}
}
