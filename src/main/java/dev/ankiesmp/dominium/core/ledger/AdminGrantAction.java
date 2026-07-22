package dev.ankiesmp.dominium.core.ledger;

import dev.ankiesmp.dominium.core.player.PlayerTargetResolver;
import dev.ankiesmp.dominium.core.player.ResolvedPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Bindt de resolver aan de ledger-admin-operatie. Volledig pure — geen
 * Bukkit-imports, geen threadaannames. De Paper-command wrapt dit en
 * levert alleen de user-facing schil (permissiecheck, async dispatch,
 * response-formatting).
 */
public final class AdminGrantAction {

    private final PlayerTargetResolver resolver;
    private final ClaimBlockAdminOps adminOps;

    public AdminGrantAction(PlayerTargetResolver resolver, ClaimBlockAdminOps adminOps) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.adminOps = Objects.requireNonNull(adminOps, "adminOps");
    }

    public Result run(String rawTarget, long amount, String actor, UUID idempotencyKey) {
        Objects.requireNonNull(rawTarget, "rawTarget");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (amount <= 0) {
            return Result.invalidAmount(
                    "Amount must be positive (got " + amount + ").");
        }
        Optional<ResolvedPlayer> target = resolver.resolve(rawTarget);
        if (target.isEmpty()) {
            return Result.unknownPlayer(
                    "Unknown player. The player must have joined this server before.");
        }
        ResolvedPlayer resolved = target.get();
        PostingOutcome outcome = adminOps.grantToPlayer(
                resolved.uuid(), amount, actor, idempotencyKey);
        return Result.granted(resolved, outcome);
    }

    public static final class Result {
        public enum Kind { GRANTED, UNKNOWN_PLAYER, INVALID_AMOUNT }

        private final Kind kind;
        private final String message;
        private final ResolvedPlayer target;
        private final PostingOutcome outcome;

        private Result(Kind kind, String message, ResolvedPlayer target, PostingOutcome outcome) {
            this.kind = kind;
            this.message = message;
            this.target = target;
            this.outcome = outcome;
        }

        public static Result granted(ResolvedPlayer target, PostingOutcome outcome) {
            return new Result(Kind.GRANTED, null,
                    Objects.requireNonNull(target),
                    Objects.requireNonNull(outcome));
        }
        public static Result unknownPlayer(String message) {
            return new Result(Kind.UNKNOWN_PLAYER, message, null, null);
        }
        public static Result invalidAmount(String message) {
            return new Result(Kind.INVALID_AMOUNT, message, null, null);
        }

        public Kind kind() { return kind; }
        public String message() { return message; }
        public ResolvedPlayer target() { return target; }
        public PostingOutcome outcome() { return outcome; }
    }
}
