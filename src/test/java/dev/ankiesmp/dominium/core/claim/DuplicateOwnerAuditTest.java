package dev.ankiesmp.dominium.core.claim;

import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateOwnerAuditTest {

    private final WorldRef world = new WorldRef(UUID.randomUUID());

    private Claim c(UUID owner, ClaimType type, int minX, int minZ, int maxX, int maxZ) {
        var o = type == ClaimType.KINGDOM ? ClaimOwner.kingdom(owner)
                : type == ClaimType.PERSONAL ? ClaimOwner.personal(owner)
                : ClaimOwner.admin(owner);
        return new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(minX, minZ, maxX, maxZ), o, Instant.now());
    }

    // DA-001 — schone index → geen conflict.
    @Test
    void cleanIndexReturnsEmpty() {
        var idx = new ClaimIndex();
        idx.add(c(UUID.randomUUID(), ClaimType.PERSONAL, 0, 0, 9, 9));
        idx.add(c(UUID.randomUUID(), ClaimType.KINGDOM, 100, 100, 109, 109));
        assertTrue(DuplicateOwnerAudit.scan(idx).isEmpty());
    }

    // DA-002 — twee claims voor dezelfde owner → één conflict met beide IDs.
    @Test
    void detectsPersonalDuplicates() {
        UUID owner = UUID.randomUUID();
        var idx = new ClaimIndex();
        var a = c(owner, ClaimType.PERSONAL, 0, 0, 9, 9);
        var b = c(owner, ClaimType.PERSONAL, 100, 100, 109, 109);
        idx.add(a); idx.add(b);
        var conflicts = DuplicateOwnerAudit.scan(idx);
        assertEquals(1, conflicts.size());
        assertEquals(owner, conflicts.get(0).ownerId());
        assertEquals(2, conflicts.get(0).claimIds().size());
    }

    // DA-003 — ADMIN-claims worden nooit als conflict gemarkeerd.
    @Test
    void adminClaimsAreExempt() {
        var idx = new ClaimIndex();
        idx.add(c(UUID.randomUUID(), ClaimType.ADMIN, 0, 0, 9, 9));
        idx.add(c(UUID.randomUUID(), ClaimType.ADMIN, 100, 100, 109, 109));
        assertTrue(DuplicateOwnerAudit.scan(idx).isEmpty());
    }
}
