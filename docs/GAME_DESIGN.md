# Dominium — Game design

## 1. Kernproposities

- **Solo eerst.** Spelers zonder kingdom kunnen volledig spelen met
  persoonlijke claims, verdienen claim blocks door actief te spelen, en
  hebben dezelfde technische beschermingskwaliteit als kingdomclaims.
- **Kingdoms = uitgebreide teams.** Één leader, optionele co-leaders,
  members, en een aparte visitorlijst. Geen politiek. Geen wetten. Geen
  verkiezingen. Geen opvolgingssystemen.
- **Claims zijn rechthoekig en block-based.** Twee X/Z-hoeken met een
  gemarkeerde golden shovel. Kosten = `width × depth` claim blocks. Geen
  chunk-afronding. Geen plots of subclaims.
- **Bank en war-inzet zijn geld, geen aparte token.** Elke war heeft een
  verplichte geldstake. Zonder saldo geen proposal, geen accept, geen
  counter-verhoging.
- **Visitors zijn geen members.** Ze kunnen binnenkomen en deuren gebruiken,
  maar krijgen **nooit** storage-toegang, ook niet via indirecte
  hopper/dispenser/minecart-omwegen.

## 2. Rollen

| Rol         | Beperking             | Kerntaken                                                       |
|-------------|-----------------------|-----------------------------------------------------------------|
| `LEADER`    | exact 1 per kingdom   | disband, transfer, bankwithdraw, war propose/accept, alles      |
| `CO_LEADER` | door leader benoemd   | invites, members, visitors, claims, diplomacy, wars, deposits   |
| `MEMBER`    | normaal lidmaatschap  | bouwen/containers/deposits/donaties                             |

`VISITOR` is **geen rol** maar een aparte kingdom-wide access-lijst.

## 3. Claim blocks

Verdiend via:
- actieve speeltijd (caps per dag/week, cross-category-eisen);
- adminbeloningen;
- externe systemen (chatminigames etc.) via
  `ClaimBlockService#grant(...)` met verplichte `idempotencyKey`.

Verbruikt door persoonlijke of kingdomclaim-creatie/-resizing. Ledger is
append-only en idempotent — zie [`DATABASE.md`](DATABASE.md).

## 4. War-inzet

Elke oorlog (behalve admin event-wars) heeft een verplichte, gelijke
geldstake per kingdom. Reservering bij proposal creation door proposer,
tweede reservering bij accept door target. Falen van reservering → geen
proposal / geen accept. Winnaar krijgt eigen stake + verliezerstake. Draw
of admincancel → refund. Reservering is atomair via een operation-state-
machine (`PENDING_EXTERNAL → EXTERNAL_APPLIED → COMMITTED` /
`COMPENSATION_REQUIRED → COMPENSATED`).

## 5. Wat we bewust **niet** bouwen

- plots of subclaims;
- governments, elections, laws, kingdomvotes, councils;
- opvolgings-/successionsystemen;
- WorldGuard-achtige flag-DSL waarmee eindgebruikers zelf raidregels
  kunnen samenstellen;
- vassalage of trade governments;
- automatische stille leadership-overname.
