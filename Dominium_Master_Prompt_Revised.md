# Claude Code Master Prompt — Dominium (Block Claims Edition)

Je werkt in Claude Code/Claude CLI als senior Java- en Paper-pluginontwikkelaar. Bouw in deze repository de volledige productieklare Minecraft-plugin **Dominium**.

Dominium combineert GriefPrevention-achtige golden-shovel blockclaims met uitgebreide teams die in de spelers-UX **kingdoms** heten. Een kingdom is nadrukkelijk geen politieke simulatie en heeft geen regering, verkiezingen, burgerraad of ingewikkeld bestuur. Het is een hecht spelersteam met gezamenlijk territorium, een eenvoudige rolhiërarchie, bezoekers, een claim-blockpool, een geldbank, diplomatie en vooraf overeengekomen oorlogen.

Spelers die geen kingdom willen joinen moeten zelfstandig kunnen spelen met volwaardige persoonlijke claims. Kingdoms voegen samenwerking en gezamenlijke features toe, maar mogen nooit verplicht voelen.

De plugin mag niet aanvoelen als:
- een standaard claimplugin met later teams eraan vastgemaakt;
- een politieke Kingdoms-, Factions-, Towny-, Lands- of GriefPrevention-kloon;
- een systeem met regeringsvormen, verkiezingen, raden, wetten of onnodige roleplaybureaucratie;
- een verzameling losse systemen zonder samenhang.

De plugin moet aanvoelen als:
> Een betrouwbare survival-claimplugin waarin spelers solo kunnen bouwen of samen een uitgebreid kingdomteam vormen, grondgebied beheren, bezoekers toelaten, geld sparen en eerlijke oorlogen met duidelijke inzet afspreken.

## 1. Opdracht en werkwijze

Voer deze opdracht daadwerkelijk uit in de repository. Lever niet alleen een ontwerp, pseudocode of losse voorbeelden.

1. Inspecteer eerst de volledige repository, bestaande buildbestanden, documentatie en conventies.
2. Als er al code bestaat, behoud bruikbare code en migreer gecontroleerd. Overschrijf geen goed werk zonder reden.
3. Als de repository leeg is, maak een professionele multi-module projectstructuur.
4. Maak vóór implementatie:
   - `docs/ARCHITECTURE.md`
   - `docs/DATABASE.md`
   - `docs/GAME_DESIGN.md`
   - `docs/IMPLEMENTATION_STATUS.md`
   - `docs/TEST_PLAN.md`
5. Maak vervolgens de volledige plugin gefaseerd.
6. Stop niet na het ontwerp of na de MVP. De uiteindelijke opdracht omvat ook kingdoms, bezoekers, de kingdom bank, oorlogen, raids, rollback, verovering, integraties en admin-tools.
7. Na iedere fase:
   - compileer;
   - voer relevante tests uit;
   - herstel alle fouten;
   - update `docs/IMPLEMENTATION_STATUS.md`;
   - laat de repository in een buildbare toestand achter.
8. Houd in `docs/IMPLEMENTATION_STATUS.md` per feature exact bij:
   - `NOT_STARTED`
   - `IN_PROGRESS`
   - `IMPLEMENTED`
   - `TESTED`
   - `BLOCKED`
9. Markeer een feature alleen als `TESTED` wanneer de test daadwerkelijk is uitgevoerd.
10. Gebruik geen placeholders, lege handlers, nepimplementaties, hardcoded demoresultaten, `TODO` als vervanging voor functionaliteit, of methodes die stilletjes altijd `true`, `false`, `null` of een lege lijst teruggeven.
11. Vraag niet steeds om goedkeuring tussen fases. Neem bij normale ambiguïteit de veiligste, eenvoudigste en best onderhoudbare keuze en documenteer die.
12. Stel alleen een vraag wanneer uitvoering echt onmogelijk is zonder een ontbrekend geheim, extern account, serveradres of destructieve beslissing.
13. Gebruik Git om wijzigingen te inspecteren, maar commit, push of open geen pull request tenzij dat expliciet gevraagd wordt.
14. Wanneer de contextlimiet nadert, werk eerst de lopende verticale slice volledig af, zorg dat de build groen is en schrijf een exacte hervattingsinstructie in `docs/IMPLEMENTATION_STATUS.md`. Doe niet alsof de plugin af is.

## 2. Bronnen en conflictregels

Als de oorspronkelijke ontwerpdocumenten in de repository aanwezig zijn, lees ze volledig en gebruik ze als aanvullende bron.

Bij conflicten geldt deze prioriteit:

1. Deze master prompt.
2. Expliciete bestaande projectvereisten in de repository.
3. De oorspronkelijke ontwerpdocumenten.
4. Eigen aannames.

Belangrijke overrides:

- De volledige plugin moet uiteindelijk worden gebouwd; oorlogen blijven niet permanent “voor later”.
- Claims worden primair bediend met een **golden shovel**.
- Het spelergerichte woord is **kingdom**; `/team` mag als alias bestaan.
- Een kingdom is functioneel een uitgebreid team, geen staat of regeringssimulatie.
- Er bestaan geen regeringsvormen, verkiezingen, kingdomvotes, raden, wetten, opvolgingssystemen of politieke functies.
- De maker van een kingdom is de `LEADER`. De leader kan spelers uitnodigen, members promoveren tot `CO_LEADER`, co-leaders degraderen en het leiderschap bewust overdragen.
- De vaste standaardrollen zijn `LEADER`, `CO_LEADER` en `MEMBER`. Visitors zijn geen members en vormen een aparte toegangslijst.
- Iedere kingdom member, inclusief de leader, kan geld in de kingdom bank storten. Alleen bevoegde rollen mogen opnemen of uitgeven.
- Een war proposal bevat verplicht een positief geldbedrag als inzet. Zonder voldoende vrij geld in de kingdom bank kan een kingdom geen proposal indienen, accepteren of naar een hoger bedrag counteren.
- Spelers zonder kingdom kunnen volwaardig spelen met persoonlijke claims. Bij toetreden blijven bestaande persoonlijke claims beschermd, maar nieuwe persoonlijke claims kunnen standaard niet onbeperkt naast kingdomterritorium worden aangemaakt.
- Top-level territorium is **block-based en rechthoekig**, vergelijkbaar met GriefPrevention: twee exacte X/Z-hoeken met een golden shovel.
- Een claim kost exact het aantal horizontale blokken in de inclusieve rechthoek: `width × depth`. Eén claim block beschermt één X/Z-kolom over de volledige wereldhoogte.
- Claims worden niet naar chunkgrenzen afgerond en spelers claimen nooit verplicht een volledige chunk.
- Chunks mogen intern uitsluitend als spatial-indexbucket worden gebruikt; een chunk is nooit de eigendoms- of rekeneenheid.
- Persoonlijke claims en kingdomclaims gebruiken precieze rechthoekige regions. **Geen plots of subclaims binnen kingdomterritorium.**
- Claim blocks moeten verdiend kunnen worden via actieve speeltijd én door externe plugins zoals chatminigames via een publieke API en veilige consolecommands.

## 3. Technische basis

### 3.1 Versies

- Detecteer de Paper- en Java-versie uit de bestaande repository.
- Behoud de bestaande doelversie wanneer die bewust is ingesteld.
- Als geen doelversie bestaat, controleer de actuele officiële Paper-documentatie en kies een stabiele Paper-release met de daarbij vereiste Java-versie.
- Verzin geen versiecombinaties.
- Leg de exacte gekozen versies vast in `README.md` en `gradle.properties` of het equivalente buildbestand.
- Gebruik uitsluitend publieke Paper-, Adventure- en Java-API’s.
- Gebruik geen NMS, reflection naar serverinternals of afhankelijkheid van ongedocumenteerde implementatiedetails.
- Folia-ondersteuning is geen harde eis voor v1, maar de pure domeinlaag moet geen Bukkit-types bevatten en mag geen globale statische state gebruiken.

### 3.2 Build en modules

Als de repository leeg is, gebruik Gradle Kotlin DSL met minimaal:

- `dominium-api`
- `dominium-core`
- `dominium-storage`
- `dominium-paper`

Voorgestelde package root:

`dev.ankiesmp.dominium`

Verantwoordelijkheden:

- `dominium-api`: publieke interfaces, DTO’s, servicecontracten en custom events voor andere plugins.
- `dominium-core`: pure Java-domeinlogica zonder Bukkit/Paper-imports.
- `dominium-storage`: repositories, SQL, migraties, HikariCP, SQLite en PostgreSQL.
- `dominium-paper`: plugin bootstrap, listeners, commands, GUI’s, HUD, scheduler-adapters en integraties.

Gebruik bij een bestaande Maven- of andere structuur de bestaande tool wanneer omschakelen meer risico dan voordeel geeft.

### 3.3 Libraries

Gebruik waar passend:

- Paper API.
- Adventure en MiniMessage.
- HikariCP.
- Flyway of een gelijkwaardig versioned migratiesysteem.
- SQLite JDBC.
- PostgreSQL JDBC.
- JUnit 5.
- jqwik of een vergelijkbare property-testinglibrary.
- Testcontainers voor PostgreSQL-integratietests.
- MockBukkit uitsluitend waar het gekozen Paper-doel ermee compatibel is.
- Een officiële run-server Gradle-plugin of een lokale testservertaak voor handmatige tests.

Shade en relocate alleen libraries die niet door Paper worden geleverd. Maak geen onnodig grote jar.

## 4. Architectuurregels

Gebruik een modulaire domeinarchitectuur met handmatige constructor dependency injection.

Maak geen globale `DominiumManager`, geen god-class en geen statische service locator die overal direct wordt aangeroepen.

Gebruik minimaal domeinen voor:

- player profiles;
- activity;
- claim blocks en ledger;
- territory en claim index;
- claim validation;
- protection en flags;
- personal claims;
- kingdoms;
- membership;
- roles en permissions;
- visitors;
- kingdom claimpool;
- kingdom bank en geldledger;
- diplomacy;
- war proposals;
- war stake reservations en escrow;
- wars;
- raid windows;
- raid sessions;
- objectives;
- damage logging en rollback;
- conquest en occupation;
- progression;
- history;
- audit;
- disputes;
- admin;
- integrations.

Regels:

- Commands, GUI’s en listeners praten met application services.
- Application services gebruiken repositories en pure domeinobjecten.
- Repositories bevatten geen gameplayregels.
- Bukkit-objecten zoals `Player`, `World`, `Chunk` en `Location` mogen niet in de pure core worden opgeslagen.
- Gebruik UUID’s, world UUID’s, inclusieve rechthoeken (`minX`, `maxX`, `minZ`, `maxZ`) en immutable value objects. Chunkcoordinates mogen alleen voorkomen in de interne spatial index.
- Domeinmutaties gebeuren voorspelbaar op de serverthread.
- Database-I/O gebeurt nooit op de serverthread.
- Worldmutaties gebeuren nooit vanuit een asynchrone thread.
- Geef async taken immutable DTO’s of snapshots mee.
- Zorg bij shutdown voor flush, checkpointing en gecontroleerd sluiten van executors en connection pools.
- Houd claim-blockledger, kingdom-bankledger en war-escrow administratief strikt gescheiden.
- Vault/economy-mutaties zijn externe side effects: gebruik duurzame operation records, idempotency keys en compensatiepaden zodat crashes geen geld dupliceren of laten verdwijnen.
- Gebruik custom events zoals `ClaimCreateEvent`, `ClaimEnterEvent`, `KingdomCreateEvent`, `KingdomDisbandEvent`, `KingdomMemberRoleChangeEvent`, `KingdomVisitorChangeEvent`, `KingdomBankBalanceChangeEvent`, `DiplomacyChangeEvent`, `WarProposalCreatedEvent`, `WarAcceptedEvent`, `RaidWindowStartEvent` en `OccupationChangeEvent`.
- Publiceer alleen stabiele API-contracten in `dominium-api`.

## 5. Productmodel

Dominium heeft vijf samenhangende lagen:

1. **Solo en territorium**  
   Persoonlijke claims, kingdomclaims, claim blocks, protecties, toegang en inactiviteitsregels.

2. **Kingdomteam**  
   Leader, co-leaders, members, invites, visitors, gezamenlijke claimpool, kingdom home, bank, logs en eenvoudige teamprogressie.

3. **Diplomatie**  
   Neutrale relaties, allies, rivals, truces en wederzijds geaccepteerde afspraken zonder politieke simulatie.

4. **Conflict**  
   Geversioneerde oorlogsvoorstellen, verplichte geldinzet, voorbereiding, raid windows, objectives, schadeherstel en begrensde verovering.

5. **Geschiedenis en beheer**  
   Tijdlijnen, auditlogs, moderation, disputes, statistieken, integraties en admin-tools.

Iedere laag moet zonder de volgende laag kunnen functioneren. Persoonlijke claims moeten volledig bruikbaar blijven wanneer kingdoms of wars via config zijn uitgeschakeld. Implementeer uiteindelijk alle lagen, maar voeg geen government-, election-, law- of councilmodule toe.

## 6. Golden shovel claimtool

De golden shovel is de primaire fysieke claiminterface.

### 6.1 Tool-identiteit

- `/dominium tool` en `/claim tool` geven de speler een golden shovel.
- Markeer de tool met een `PersistentDataContainer`-key, bijvoorbeeld `dominium:claim_tool`.
- Intercepteer alleen interacties van een gemarkeerde tool. Normale golden shovels blijven vanilla werken.
- Maak naam, lore, glow, unbreakable-status en materiaal configureerbaar, met `GOLDEN_SHOVEL` als standaard.
- De lore toont:
  - huidige modus;
  - geselecteerde eigenaar;
  - beschikbare persoonlijke claim blocks;
  - beschikbare kingdom claim blocks;
  - korte bedieningsuitleg.

### 6.2 Modi

Minimaal:

- `PERSONAL_CLAIM`
- `KINGDOM_CLAIM`
- `INSPECT`

Bediening:

- Sneak + rechtsklik in de lucht: wissel modus of open een compacte modus-GUI.
- `PERSONAL_CLAIM` is beschikbaar voor spelers zonder kingdom. Bestaande persoonlijke claims blijven inspecteerbaar en beheerbaar nadat een speler joint.
- `KINGDOM_CLAIM` vereist een kingdomrol met claimpermission.
- Linksklik op een blok: inspecteer bestaand gebied en toon grenzen.
- Rechtsklik op een blok: zet punt A.
- Rechtsklik op een tweede blok: zet punt B en toon een preview.
- Bevestig via een duidelijke klikbare chatknop of GUI.
- Sneak + linksklik of een expliciete GUI-knop annuleert de selectie.
- Verwijderen/unclaimen is nooit één onbedoelde klik; gebruik een aparte unclaimmodus of bevestigingsflow.
- Selecties verdwijnen bij logout, world change, toolverlies en timeout.
- Ondersteun snelle selectie van een minimale claim en het vergroten/verkleinen van bestaande claims door een geselecteerde hoek te verplaatsen.
- Geef heldere fouten wanneer de speler geen rechten, claim blocks of geldige selectie heeft.

### 6.3 Block-based top-level claims

Persoonlijke en kingdomclaims zijn GriefPrevention-achtig:

- exacte rechthoekige X/Z-regions;
- full-column van wereldminimum tot wereldmaximum;
- inclusief beide geselecteerde hoekblokken;
- niet afgerond op chunks;
- opgeslagen als `minX`, `maxX`, `minZ`, `maxZ`, world UUID en owner/type;
- geselecteerd met twee exacte tegenoverliggende hoekblokken;
- visueel weergegeven met particles of tijdelijke client-side markers;
- te vergroten of verkleinen door met de golden shovel een bestaande hoek te selecteren en naar een nieuwe locatie te verplaatsen;
- alleen rechthoekig: geen polygonen, vrije vormen, gaten of losse individuele blokwolken.

Kostenformule:

`claimCost = (maxX - minX + 1) × (maxZ - minZ + 1)`

Eén claim block beschermt één horizontale X/Z-kolom over de volledige wereldhoogte. Bij resizing wordt alleen het verschil atomair geboekt of terugbetaald.

Voorbeeld: een claim van 10×10 kost 100 claimblocks. Vergroten naar 15×10 kost 50 extra. Verkleinen naar 8×10 geeft 20 claimblocks terug. Gebruik inclusieve coördinaten en `long`-rekenwerk om off-by-one- en overflowbugs te voorkomen.

Preview vóór bevestiging:

- breedte;
- diepte;
- oppervlakte in claim blocks;
- totale kosten of refund bij resize;
- resterend persoonlijk of kingdomsaldo;
- eigenaar;
- gebiedstype;
- overlap/conflicten;
- minimale-afmetingfouten;
- buffer- en adjacencyfouten;
- outposttoeslag;
- warlocks;
- waarschuwingen;
- exacte reden waarom een selectie ongeldig is.

Claiminteractie:

- eerste rechterklik zet hoek A;
- tweede rechterklik zet hoek B;
- een bestaande claimhoek aanklikken start resize mode;
- linksklik met de tool inspecteert de claim en toont alle vier de hoeken;
- claim creation/resizing heeft altijd een preview en bevestiging;
- snel dubbelklikken of herhaalde packets mogen nooit dubbel afschrijven;
- een selectie wordt bij bevestiging opnieuw volledig gevalideerd;
- een mislukte DB-transactie verandert de regionindex niet;
- resizing mag geen andere claim, adminzone, route of locked warregion overlappen.

### 6.4 Geen subclaims of plots

Dominium implementeert geen plots of subclaims binnen kingdomterritorium.

- Een kingdomclaim heeft één territoriale eigenaar: het kingdom.
- Toegang wordt geregeld via kingdomrollen, de kingdom visitorlist, diplomatieke relaties en expliciete individuele claimoverrides.
- Maak geen plot mode, plotcommands, plotdatabase-tabellen of verborgen subdivisionmodel.
- Een kingdom kan meerdere losse top-level claims bezitten binnen de normale adjacency- en outpostregels.
- Persoonlijke claims blijven aparte top-level claims en worden nooit automatisch een subclaim van een kingdom.
- Deze eenvoud is bewust: features moeten zitten in betrouwbare protectie, toegang, teamwork, bank en oorlogen, niet in extra bestuurslagen.

## 7. Territorium en claimregels

### 7.1 Gebiedstypen

Ondersteun:

- wilderness;
- persoonlijke claim;
- kingdomterritorium;
- kingdom core/home claim;
- kingdom outpost;
- safezone;
- warzone;
- publieke route;
- contested territory;
- occupied territory.

Maak geen steden-, dorpen-, provincies-, kolonies- of settlementhiërarchie.

### 7.2 Plaatsingsvalidatie

Voor persoonlijke en kingdomclaims geldt een block-based rechthoekmodel. Voor kingdomclaims geldt aanvullend standaard:

- nieuwe kingdomclaims moeten met minimaal een configureerbare lengte via een zijde grenzen aan bestaand eigen territorium;
- de eerste kingdomclaim vormt de core/home claim;
- losse gebieden zijn alleen toegestaan als geregistreerde outpost;
- configureerbaar maximumaantal outposts;
- outposts kosten standaard meer claim blocks;
- minimale afstand tot claims van anderen;
- geen outposts tijdens actieve oorlogslocks;
- configureerbare bufferzone;
- geen overlap met adminzones of publieke routes;
- geen volledige insluiting van andermans gebied;
- configureerbare minimum breedte, minimum diepte en minimumoppervlakte;
- geen extreem dunne claimstroken of claimarmen; standaard minimaal bijvoorbeeld 10×10, met aparte ruimere eisen voor kingdomterritorium;
- geen claimmutaties in locked of contested gebied;
- world allowlist/denylist;
- dimensiespecifieke regels.

Omdat rechthoekige blockregions de hoofdeenheid zijn, gebruik je exacte rectangle geometry voor overlap, afstand, adjacency en buffers. Gebruik alleen voor complexe insluitings- en connected-componentchecks een begrensde flood-fill over grove spatial buckets; valideer het resultaat daarna tegen de exacte regionranden.

### 7.3 Persoonlijke claims en kingdomlidmaatschap

- Een speler zonder kingdom mag een configureerbaar aantal persoonlijke claims bezitten.
- Persoonlijke claims bieden dezelfde technische protectiekwaliteit als kingdomclaims, maar geen kingdomfeatures zoals bank, gezamenlijke claimpool of oorlogen.
- Bij toetreden tot een kingdom blijven bestaande persoonlijke claims beschermd.
- Een kingdomlid kan standaard geen nieuwe persoonlijke claims maken zolang het lidmaatschap actief is.
- Bied gecontroleerde opties voor bestaande claims:
  - persoonlijk behouden;
  - vrijwillig aan het kingdom overdragen;
  - omzetten naar kingdomterritorium met correcte claim-blockboeking;
  - voor toetreding eerst verlaten.
- Overdracht vereist expliciete bevestiging van de speler en een bevoegde leader/co-leader.
- Nooit stil eigendom afpakken.
- Vertrek uit een kingdom verandert persoonlijke eigendom niet.

### 7.4 Inactiviteit

Standaard, volledig configureerbaar:

- persoonlijke eigenaar 60 dagen offline → claimstatus `EXPIRING`;
- 14 dagen grace period;
- bescherming blijft tijdens grace actief;
- login herstelt de claim;
- daarna vervalt bescherming en keren gebruikte claim blocks terug via de ledger;
- admins kunnen exemptions instellen;
- kingdomterritorium kijkt naar de activiteit van alle members;
- wanneer leader langdurig offline is, blijft het kingdom bestaan zolang een co-leader of voldoende members actief zijn;
- automatische leadership transfer gebeurt nooit stil: bied een configureerbare, expliciete inactivity-recoveryflow voor co-leaders en admins met noticeperiode en auditlog;
- bij volledige kingdominactiviteit start een duidelijke `DISSOLVING`-graceperiod voordat claims, bankreserveringen en claimpool worden afgehandeld.

## 8. Claim blocks en ledger

Maak claim blocks financieel correct en crashbestendig.

### 8.1 Append-only ledger

Alle mutaties zijn append-only ledgerentries met:

- holder type;
- holder id;
- delta;
- reason;
- reference;
- idempotency key;
- timestamp;
- actor;
- metadata.

Redenen omvatten minimaal:

- initial grant;
- active-play earn;
- admin grant/revoke;
- personal claim spend/refund;
- kingdom claim spend/refund;
- donation out/in;
- automatic donation allocation;
- pending withdrawal;
- withdrawal completion;
- maintenance correction;
- membership transfer correction;
- migration.

Houd daarnaast gematerialiseerde balansen bij binnen dezelfde transactie.

Invarianten:

- geen negatieve balans;
- één claim block bestaat nooit tegelijk bij speler en kingdom;
- retries met dezelfde idempotency key boeken nooit dubbel;
- iedere balans is reproduceerbaar uit de ledger;
- transfers schrijven beide zijden atomair;
- bevestig claimen pas aan de speler nadat de DB-transactie succesvol is;
- update daarna veilig de in-memory index op de serverthread.

### 8.2 Verdienen

Configureerbare voorbeelddefaults voor een block-based economie:

- 500 persoonlijke start-claimblocks;
- 100 claimblocks per actief gespeeld uur;
- maximum 500 playtime-claimblocks per dag;
- maximum 2.500 playtime-claimblocks per week;
- verminderde playtime-opbrengst boven 50.000 beschikbare persoonlijke claimblocks;
- configureerbare harde balanscap, bijvoorbeeld 200.000;
- aparte, hogere of formulegebaseerde kingdomcap.

Behandel deze getallen als goed gedocumenteerde defaults, niet als hardcoded constanten. Maak duidelijk hoeveel oppervlakte ermee kan worden geclaimed, bijvoorbeeld: 500 blocks is één claim van 20×25 of meerdere claims met samen dezelfde oppervlakte.

Actieve speeltijd wordt niet alleen door online tijd bepaald.

Gebruik vensters en activiteit uit minstens twee categorieën:

- betekenisvolle verplaatsing;
- bouwen of breken;
- damage geven of ontvangen;
- XP verkrijgen;
- craft- en containerinteractie;
- andere goedkope maar moeilijker te faken gameplayevents.

Chat en commandspam tellen niet. Cap iedere categorie zodat een AFK-pool of autoclicker niet voldoende is.

Sla dagelijkse totalen op, niet iedere beweging.

### 8.3 Externe claimblock-beloningen

Chatminigames, voteplugins, events, quests en andere systemen moeten claim blocks veilig kunnen uitdelen.

Lever drie integratiepaden:

1. Publieke Java API in `dominium-api`:
   - `ClaimBlockService#grant(UUID playerId, long amount, RewardSource source, String externalReference, UUID idempotencyKey)`
   - `ClaimBlockService#revoke(...)`
   - `ClaimBlockService#getBalance(...)`
   - async resultaat zonder Bukkit-objecten in het contract.

2. Console/admincommands:
   - `/dominium admin claimblocks give <player> <amount> <reason> [--reference <id>]`
   - `/dominium admin claimblocks take <player> <amount> <reason> [--reference <id>]`
   - `/dominium admin claimblocks set <player> <amount> <reason>`
   - `/dominium admin claimblocks balance <player>`
   - aliases mogen `claimblocks` en `claim-blocks` accepteren.

3. Optionele PlaceholderAPI- en eventhooks:
   - placeholder voor beschikbaar, totaal verdiend en totaal gebruikt;
   - `ClaimBlocksChangeEvent`;
   - `ClaimBlocksEarnEvent`;
   - `ClaimBlocksExternalRewardEvent`.

Eisen:

- externe rewards lopen altijd door dezelfde ledger;
- een external reference/idempotency key voorkomt dubbele rewards wanneer een chatminigame retryt;
- geef nooit onbeperkte publieke playercommands om blocks te genereren;
- consolecommands vereisen een reason en loggen actor/source;
- bedragen zijn positieve gehele getallen, overflow-safe en aan servercaps gebonden;
- laat config bepalen of externe rewards meetellen voor dagelijkse/weekcaps; standaard tellen minigame/eventrewards niet mee voor de playtimecap maar wel voor de harde balanscap;
- bied een duidelijke response/exitstatus zodat externe plugins weten of de reward gelukt is;
- documenteer voorbeeldintegraties voor een command-based chatminigame en voor de Java API.

### 8.4 Kingdom claim-blockpool

Kingdoms hebben een eigen claim-blockpool. Dit is **niet** hetzelfde als de geldbank.

Bronnen:

- vrijwillige directe donaties van persoonlijke claim blocks;
- een door het lid instelbaar percentage van nieuw verdiende claim blocks;
- admin- of eventbeloningen;
- optionele kingdomprogressierewards.

Standaarden:

- iedere member mag claim blocks doneren;
- nieuw lid wacht een configureerbare periode voordat automatische donaties meetellen;
- vertrek heeft cooldown;
- onbesteed persoonlijk gedoneerd tegoed kan via een vertraagde withdrawalflow worden teruggevraagd;
- gebruikt tegoed ondersteunt bestaand territorium en kan niet direct worden teruggetrokken;
- bij tekort gaan de laatst gemaakte of uitgebreide niet-coreclaims in `MAINTENANCE`, of wordt alleen het laatst toegevoegde uitbreidingsdeel teruggedraaid wanneer geometry history dit veilig toelaat;
- het kingdom krijgt een grace period om het tekort op te lossen;
- de core/home claim vervalt als laatste;
- disband start een `DISSOLVING`-periode en verwerkt claims en tegoeden transparant;
- alle boekingen zijn auditbaar;
- team-hop-duplicatie moet door transacties en cooldowns onmogelijk zijn.

Maak caps afhankelijk van actieve members met diminishing returns, volledig configureerbaar.

## 9. Kingdoms

### 9.1 Identiteit

Een kingdom is een uitgebreid team en heeft:

- unieke naam;
- unieke tag;
- kleur;
- optionele banner/vlag;
- motto;
- beschrijving;
- oprichtingsdatum;
- leader;
- co-leaders;
- members;
- visitors;
- core/home claim;
- kingdom home;
- kingdomclaims en outposts;
- kingdom claim-blockpool;
- kingdom bank;
- eenvoudige diplomatie;
- war history;
- progressie;
- auditbare activiteit.

Naam- en tagvalidatie moet impersonatie, control characters en onveilige MiniMessageinput voorkomen. Gebruikersinput wordt nooit ongeëscaped als MiniMessage geïnterpreteerd.

### 9.2 Vaste rollen en permissions

Standaardrollen:

1. `LEADER`
   - exact één per kingdom;
   - volledige kingdomcontrole binnen serverregels;
   - kan co-leaders en members beheren;
   - kan leadership overdragen;
   - kan disband starten;
   - kan bankopnames en war proposals beheren.

2. `CO_LEADER`
   - door de leader gepromoveerde member;
   - kan standaard invites, members, visitors, claims, diplomacy en wars beheren;
   - kan de leader nooit degraderen, kicken of het kingdom overnemen;
   - gevaarlijke permissions zoals disband, leadership transfer en onbeperkte bankwithdraw blijven standaard leader-only.

3. `MEMBER`
   - normale kingdomspeler;
   - kan standaard in kingdomgebied bouwen, containers gebruiken en geld of claim blocks doneren;
   - kan geen andere members beheren tenzij een expliciete permissionoverride dat toestaat.

`VISITOR` is geen role en geen membership. Visitors staan in een aparte kingdom-wide allowlist en krijgen alleen het visitor access preset.

Interne permission keys omvatten minimaal:

- claim create/resize/delete/settings;
- invite/create/cancel;
- member kick;
- member promote/demote;
- visitor add/remove;
- kingdom home set/teleport;
- bank deposit;
- bank balance view;
- bank ledger view;
- bank withdraw;
- claimpool spend;
- diplomacy propose/accept/terminate;
- war propose/edit/counter/accept/cancel;
- surrender/peace propose;
- logs view;
- build/break/container/redstone/animals.

Regels:

- Iedere member mag altijd geld storten, zolang de economytransactie slaagt.
- Alleen leader en bevoegde co-leaders mogen geld opnemen of reserveren voor oorlogen.
- De leader kan permissions van de co-leaderrol en memberrol binnen veilige servercaps aanpassen; de vaste hiërarchie zelf blijft bestaan.
- Een actor mag alleen lagere rollen beheren.
- De laatste leader kan het kingdom niet verlaten zonder leadership transfer of bevestigde disband.
- Gevaarlijke acties vereisen bevestiging, auditlog en waar passend een wachttijd of undo/graceperiod—not een stemming.

### 9.3 Invites, joinen en role management

- De leader en bevoegde co-leaders kunnen invites versturen.
- Invites hebben een configureerbare expiry en zijn kingdomgebonden.
- Een speler kan maar één kingdom tegelijk joinen.
- Joinen vereist expliciete acceptatie door de speler.
- De leader kan een member promoveren tot co-leader.
- De leader kan een co-leader degraderen tot member.
- Bevoegde co-leaders kunnen members kicken, maar standaard geen andere co-leaders beheren.
- Een member kan zelfstandig vertrekken buiten actieve warlocks en cooldowns.
- Tijdens actieve voorbereiding of oorlog gelden begrensde anti-abuseregels voor join, leave, kick en role changes.
- Iedere mutatie schrijft een history- en auditrecord.

### 9.4 Visitors

Een kingdom kan spelers als visitor toevoegen zonder ze member te maken.

Default visitor access:

- `ENTRY`: allow;
- `DOOR`, `TRAPDOOR`, `GATE`: allow;
- `BUTTON`, `LEVER`, `PRESSURE_PLATE`: configureerbaar, standaard allow voor veilige interacties;
- `BUILD`, `BREAK`, `PLACE_ENTITIES`, `BUCKET`, `REDSTONE_INTERACT`: deny;
- `CONTAINER`: altijd deny binnen het standaard visitor preset;
- `ITEM_PICKUP`: configureerbaar;
- `TELEPORT`: alleen via normale serverregels en eventuele kingdom home-inviteflows.

`CONTAINER` omvat alle storage-achtige blocks en inventories, waaronder minimaal chests, trapped chests, barrels, shulker boxes, furnaces, blast furnaces, smokers, hoppers, droppers, dispensers, decorated pots, crafters en andere inventory holders van de gekozen Minecraftversie.

Regels:

- visitor access is kingdom-wide en geldt standaard voor alle kingdomclaims;
- leader/co-leader kan een visitor toevoegen, verwijderen of optioneel een expiry geven;
- een individuele deny override wint altijd;
- bezoekers mogen nooit via indirecte mechanics storage uitlezen, leeghalen of transporteren;
- bezoekers erven geen memberrechten, claimblocks, bankrechten, warrechten of kingdomchatrechten;
- visitorwijzigingen zijn auditbaar.

### 9.5 Kingdom bank

De kingdom bank is een gezamenlijke geldrekening via een economy-adapter, standaard Vault-compatible.

Functionaliteit:

- iedere `MEMBER`, `CO_LEADER` en `LEADER` kan geld depositen;
- deposit haalt geld van de persoonlijke economyrekening en voegt het na succesvolle verwerking toe aan de kingdom bank;
- leader en bevoegde co-leaders kunnen opnemen binnen configureerbare limieten;
- toon `available_balance`, `reserved_balance` en `total_balance` apart;
- iedere storting, opname, reservering, release, escrowtransfer, payout, refund en admincorrectie krijgt een immutable ledgerentry;
- iedere entry bevat actor, kingdom, amount, type, reason, reference, idempotency key, timestamp en metadata;
- negatieve bedragen, NaN, infinity, overflow en bedragen onder economy precision zijn ongeldig;
- gebruik servercaps en optionele dagelijkse withdrawlimits;
- warstakes worden uit `available_balance` gereserveerd en kunnen niet tegelijk worden uitgegeven;
- bankmutaties en Vaultmutaties gebruiken een duurzame operation state machine, bijvoorbeeld `PENDING_EXTERNAL`, `EXTERNAL_APPLIED`, `COMMITTED`, `COMPENSATION_REQUIRED`, `COMPENSATED`;
- retries zijn idempotent en admins krijgen tooling voor vastgelopen operations;
- disband kan niet voltooien zolang open war escrow, unresolved bankoperations of disputes bestaan;
- configureer wat bij normale disband met vrij banksaldo gebeurt, bijvoorbeeld verdeling naar members op basis van netto deposits of transfer naar de leader; kies één veilige default en documenteer die.

GUI en commands tonen recente transacties met paginatie en duidelijke redenen.

## 10. Protecties en toegang

### 10.1 Eén uniforme flagmatrix

Gebruik geen intern tegenstrijdige stapel whitelists.

Effectieve permissionvolgorde:

1. admin bypass met expliciete inspect/bypassmodus;
2. individuele claimoverride;
3. persoonlijke claimowner of trusted player;
4. kingdomrol (`LEADER`, `CO_LEADER`, `MEMBER`);
5. kingdom visitorlist;
6. specifiek kingdomoverride;
7. diplomatieke relatie;
8. audience default;
9. veilige deny-default.

Bij gelijke specificiteit wint deny.

Audiences:

- personal owner;
- trusted player;
- kingdom leader;
- kingdom co-leader;
- kingdom member;
- kingdom visitor;
- ally;
- truce partner;
- neutral;
- rival;
- enemy;
- public.

UI-lijsten zoals trusted, visitor, allowlist en denylist zijn views op dit ene model.

Ondersteun presets:

- Public;
- Visitors;
- Members;
- Trusted;
- Allies;
- Private;
- No Access;
- Custom.

Overrides kunnen tijdelijk zijn met TTL.

Het standaard `Visitors`-preset staat entry en normale deurinteracties toe, maar weigert containers en storagegerelateerde indirecte interacties expliciet.

### 10.2 Flags

Implementeer minimaal:

- ENTRY;
- BUILD;
- BREAK;
- PLACE_ENTITIES;
- CONTAINER;
- DOOR;
- TRAPDOOR;
- GATE;
- BUTTON;
- LEVER;
- PRESSURE_PLATE;
- REDSTONE_INTERACT;
- HARVEST;
- FARM_REPLANT;
- TRAMPLE;
- BUCKET;
- BONE_MEAL;
- SHEARS;
- FLINT_AND_STEEL;
- ANVIL_ENCHANT;
- BEACON;
- BED_RESPAWN;
- PVP;
- PROJECTILES;
- ANIMAL_INTERACT;
- ANIMAL_DAMAGE;
- BREED;
- VILLAGER_TRADE;
- MOUNT;
- VEHICLE;
- PET_PROTECT;
- ITEM_PICKUP;
- ITEM_DROP;
- FISHING_PULL;
- TELEPORT;
- ENDER_PEARL;
- CHORUS_FRUIT;
- PORTAL;
- EXPLOSION_TNT;
- EXPLOSION_CREEPER;
- EXPLOSION_OTHER;
- FIRE_SPREAD;
- LIQUID_FLOW;
- PISTON;
- DISPENSER;
- HOPPER_BORDER_TRANSFER;
- MOB_GRIEFING;
- HOSTILE_MOB_SPAWN;
- VILLAGE_RAID;
- FROST_WALKER;
- SNOW_TRAILS;
- MISC_INTERACT.

Denk ook aan actuele mechanics van de gekozen Minecraftversie, zoals wind charges, crafters, decorated pots, vaults, sculk, respawn anchors, end crystals, chest boats en nieuwe entity-interacties.

### 10.3 Grensoverschrijdende bescherming

Bescherm tegen:

- pistons die naar binnen duwen of naar buiten trekken;
- vloeistoffen over claimgrenzen;
- TNT-kanonnen;
- falling-blockkanonnen;
- projectielen en fireballs;
- dispensers;
- hoppers;
- bomen en structure growth;
- bone meal;
- fire spread;
- wind charges;
- withers;
- endermen en mob griefing;
- portals die in een claim uitkomen;
- pearls en chorus naar verboden gebied;
- fishing rods;
- voertuigen;
- entityplaatsing;
- explosions buiten de claim met schade binnen de claim.

Filter bij explosions alleen beschermde blocks uit de blocklist in plaats van altijd de volledige explosion te annuleren.

Controleer pistonbron, alle bewegende blocks en bestemmingen.

Claimchecks mogen geen chunks laden.

### 10.4 No Access

No Access is uitzonderlijk, niet de standaard.

Werking:

- blokkeer grenspassage zonder velocityspam;
- stuur speler terug naar laatste veilige locatie;
- rate-limit meldingen;
- controleer lopen, vliegen, elytra, voertuigen, teleports, pearls, chorus, portals, login en respawn;
- geef spelers die al binnen zijn een waarschuwing en veilige vertrektijd;
- activeer niet onmiddellijk;
- nooit togglen tijdens combat of actieve raidlocks;
- publieke routes kunnen niet No Access zijn;
- stel een maximumpercentage No Access-territorium per kingdom in;
- bied configureerbare vrije overvlieghoogte;
- veilige ejectscan mag nooit lava, void, suffocation of een andere verboden claim kiezen;
- fallback is een ingestelde veilige spawn.

## 11. HUD, GUI en berichten

### 11.1 Territory HUD

Bij territoriumwissel verschijnt standaard vier seconden een actionbar.

Voorbeelden:

- `Wilderness`
- `7040K's claim`
- `NATO's Kingdom`
- `NATO — Core Claim`
- `NATO — Outpost`
- `NATO's Kingdom • Ally`
- `NATO's Kingdom • Enemy Territory`
- `Contested Territory`
- `Occupied by NATO`

Gebruik MiniMessage en configureerbare templates.

Stuur niet iedere tick. Detecteer eerst blockwijziging en gecachte regioncontext en vergelijk met de gecachte huidige territory context.

Een optionele persistente actionbarmodus mag bestaan, maar staat standaard uit. Gebruik tijdens oorlogen een bossbar voor persistente raidstatus zodat actionbarkanalen niet voortdurend botsen.

### 11.2 GUI-principe

Commands voor simpele acties; GUI’s voor matrices, verschillen en gevaarlijke keuzes.

GUI’s voor minimaal:

- claim preview en bevestiging;
- access/flagmatrix;
- kingdom members en roles;
- invites;
- visitors;
- kingdom bank en ledger;
- kingdom claim-blockpool;
- diplomacy;
- war proposal editor;
- proposal version diff;
- war acceptatie en stakebevestiging;
- raidstatus;
- disputes;
- admin inspect.

GUI’s moeten:

- click-spam veilig zijn;
- permissions opnieuw controleren bij iedere klik;
- stale state herkennen;
- paginatie ondersteunen;
- inventory cleanup correct doen;
- geen itemduplication veroorzaken;
- alle user input sanitiseren;
- bij bank- of stakeacties nooit alleen op lore vertrouwen, maar server-side proposal- en balancesnapshots valideren.

## 12. Kingdomactiviteit en geschiedenis

Ondersteun eenvoudige, niet-politieke kingdomcommunicatie:

- kingdom announcements;
- join- en leaveberichten;
- role changes;
- visitor changes;
- claim changes;
- bank deposits, withdrawals en war reservations;
- diplomacy changes;
- war events;
- raid outcomes;
- territory changes;
- disband en leadership transfer.

Maak een append-only history timeline met zichtbare gebeurtenissen, onder andere:

- kingdom opgericht;
- core/home claim ingesteld;
- member gejoined of vertrokken;
- member gepromoveerd of gedegradeerd;
- visitor toegevoegd of verwijderd;
- grote bankmutatie;
- ally/truce gesloten of beëindigd;
- war proposal gemaakt, gecounterd, geaccepteerd of verlopen;
- raid gewonnen;
- territory occupied;
- vrede gesloten;
- leadership overgedragen;
- kingdom opgeheven.

Beschikbaar via `/kingdom history` en een paginated GUI. Ontwerp het datamodel zodat later een webinterface of BlueMap-popup dezelfde data kan gebruiken.

Voeg geen wetten, regeringsbesluiten, verkiezingen, burgerstemmen of politieke titels toe.

## 13. Diplomatie

Relaties:

- `NEUTRAL`;
- `ALLY`;
- `RIVAL`;
- `TRUCE`;
- `AT_WAR`.

Regels:

- `RIVAL` mag eenzijdig als label, maar activeert nooit grief- of raidrechten.
- `ALLY` en `TRUCE` vereisen een voorstel en acceptatie door een bevoegde leader/co-leader van beide kingdoms.
- `AT_WAR` ontstaat alleen uit een geldig geaccepteerd war proposal.
- Proposal expiry en cooldowns zijn configureerbaar.
- Het beëindigen van `ALLY` kan een korte no-war cooldown starten tegen backstab-exploits.
- Iedere relatieverandering is auditbaar.
- Relationship defaults voeden de claimflagmatrix.
- Allies krijgen standaard geen build-, container- of bankrechten.
- Visitors en allies zijn verschillende concepten: allystatus geeft niet automatisch entry wanneer een claim `No Access` gebruikt, tenzij de accessmatrix dat expliciet toestaat.
- Proxy-oorlogen zijn verboden: alleen expliciet opgenomen kingdoms en geldige warparticipants krijgen warrechten.
- Geen vassalage, overlords, trade governments of uitgebreide treatyconstituties.

## 14. Oorlogen

Oorlogen zijn volledig gecontroleerd en standaard consensueel. Een admin kan serverbreed aparte eventwars starten, maar normale kingdoms kunnen niet met één commando griefrechten activeren.

### 14.1 Templates

Implementeer gecureerde templates:

1. `SKIRMISH`
   - PvP en objectives;
   - geen block damage;
   - kleine escrowed stake;
   - geen landverlies.

2. `SIEGE`
   - kernmodus;
   - raid windows;
   - siege charges;
   - War Core/capture objectives;
   - tijdelijke block damage met regeneratie;
   - escrowed stakes;
   - optionele tijdelijke occupation.

3. `HARDCORE`
   - standaard uit en alleen admin-enabled;
   - leader-only dubbele bevestiging door beide kingdoms;
   - beperkte permanente damage of territory transfer;
   - harde caps;
   - extra waarschuwingen en wachttijd.

Presenteer in de UI roleplaynamen zoals Friendly War, Border Conflict, Resource War, Conquest War en Total War als presets/varianten die op bovenstaande veilige rulesets mappen. Voeg geen onbeheersbare vrije combinatie van tientallen flags toe.

### 14.2 War proposals

Een proposal heeft immutable versies.

Iedere inhoudelijke edit:

- maakt een nieuwe versie;
- verhoogt `version_no`;
- bewaart de auteur en het author kingdom;
- bewaart canonical JSON;
- berekent SHA-256;
- maakt eerdere acceptaties structureel ongeldig;
- toont een diff in de GUI;
- herberekent en valideert alle bankreserveringen.

Goedkeuring verwijst altijd naar:

- proposal id;
- hoogste version number;
- exacte hash;
- kingdom;
- actor;
- actorrole;
- timestamp.

Een oorlog kan alleen naar `ACCEPTED` wanneer beide kingdoms exact dezelfde hoogste hash hebben geaccepteerd en de vereiste geldinzet volledig is gereserveerd.

Statusmachine:

- `DRAFT`;
- `PROPOSED`;
- `NEGOTIATING`;
- `ACCEPTED`;
- `PREPARATION`;
- `SCHEDULED`;
- `ACTIVE`;
- `COMPLETED`;
- `CANCELLED`;
- `EXPIRED`;
- `DISPUTED`.

Definieer toegestane transities expliciet en test iedere combinatie.

Verplichte velden:

- target kingdom;
- template;
- positief stakebedrag in servercurrency;
- voorbereidingstijd;
- minimaal één raid date/window;
- raid duration;
- objectives;
- duidelijke winconditie.

Onderhandelbare velden binnen servercaps:

- naam en beschrijving;
- templatevariant;
- stakebedrag;
- voorbereidingstijd;
- raid dates;
- raid duration;
- contested border claims/regions;
- minimum online roster;
- max participants;
- siege charge budget;
- keepInventory policy;
- respawn delay;
- objectives;
- temporary occupation;
- surrender/peace terms.

Niet vrij onderhandelbaar:

- ledgercorrectheid;
- bankreserveringscorrectheid;
- proposal versioning;
- idempotency;
- denial precedence;
- crash recovery;
- serverbrede veiligheidslimieten;
- onbegrensde offline raiding;
- onbeperkte grief;
- deathbans.

Bij het indienen van een proposal:

- valideer dat het proposer kingdom bestaat en niet tegen zichzelf voorstelt;
- valideer actorpermission;
- valideer dat het stakebedrag binnen min/max ligt;
- valideer `available_balance >= stake`;
- reserveer het proposerbedrag duurzaam voordat het proposal zichtbaar wordt;
- als reserveren faalt, wordt geen proposal aangemaakt;
- bij expiry, reject of geldige cancel wordt de reservering idempotent vrijgegeven;
- bij counteren naar een ander bedrag worden reserveringen veilig aangepast of wordt de counter geweigerd.

### 14.3 Acceptatie zonder voting

Er zijn geen kingdomvotes, ballots, electorate snapshots, quorumregels of dual-key signatures.

Acceptatieregels:

- `LEADER` mag altijd namens het eigen kingdom accepteren binnen serverregels;
- een `CO_LEADER` mag accepteren wanneer de leader deze permission heeft ingeschakeld;
- acceptatie is alleen geldig voor de exacte hoogste proposalhash;
- het ontvangende kingdom moet op dat moment voldoende `available_balance` hebben;
- bij acceptatie wordt ook de stake van het ontvangende kingdom duurzaam gereserveerd;
- wanneer de tweede reservering of enige validatie faalt, blijft de oorlog ongeaccepteerd;
- wijzigingen na acceptatie vereisen een nieuwe proposalversie en nieuwe expliciete acceptatie door beide kingdoms;
- leader/co-leader beslissingen zijn zichtbaar in history en auditlog;
- gevaarlijke acties gebruiken confirm-GUI’s en cooldowns, niet stemmen.

### 14.4 Geldinzet, reservering en escrow

Iedere normale oorlog heeft een verplichte gelijke geldinzet per kingdom.

Voorbeeld bij een stake van €10.000:

- Kingdom A reserveert €10.000 bij proposal creation.
- Kingdom B reserveert €10.000 bij acceptatie.
- Bij start worden beide reserveringen in war escrow vastgezet.
- De winnaar krijgt de eigen €10.000 terug plus de €10.000 van de verliezer.
- De verliezer verliest dus exact het afgesproken bedrag uit de kingdom bank.

Regels:

- zonder succesvolle proposer reservation geen proposal;
- zonder succesvolle reservation van beide kingdoms geen `ACCEPTED` oorlog;
- bedragen komen uitsluitend uit de kingdom bank, nooit stil van individuele spelers;
- `reserved_balance` kan niet worden opgenomen, gedoneerd, uitgegeven of opnieuw gereserveerd;
- payout/refund is idempotent;
- draw of admin-cancel zonder schuld geeft standaard beide stakes terug;
- forfeit behandelt het ontwijkende kingdom als verliezer, binnen duidelijke serverregels;
- surrender gebruikt de proposalvoorwaarden en betaalt maximaal de gereserveerde stake uit;
- een dispute kan payout tijdelijk blokkeren maar nooit dubbel uitvoeren;
- normale raidloot komt niet uit privécontainers;
- alleen objective loot die expliciet in veilige serverpresets bestaat en de geldstake vormen oorlogsbuit;
- de war module vereist een werkende economy-adapter wanneer money stakes aanstaan; zonder provider blijven claims en kingdoms werken, maar kunnen money wars niet worden gestart;
- alle reservation-, escrow-, release-, payout- en refundoperaties hebben duurzame states, correlation IDs en admin recovery tooling.

### 14.5 Preparation

Bij `ACCEPTED`:

- snapshot roster;
- snapshot contested claims/regions;
- lock contested unclaim/transfer;
- block kingdom disband/rename/leadership transfer;
- beperk roster kicks en role changes;
- blokkeer withdrawal of spending van gereserveerd banksaldo;
- beperk riskante claimpool withdrawals;
- lock relevante diplomacywijzigingen;
- laat bouwen en normale gameplay zoveel mogelijk doorgaan;
- log belangrijke acties in contested territory;
- nieuwe members zijn geen automatische warparticipants;
- substituties zijn beperkt en vereisen regels uit het proposal;
- bevestig dat beide escrowreserveringen nog volledig en consistent zijn voordat scheduling doorgaat.

### 14.6 Raid windows

- Gebruik absolute instants in opslag.
- Toon tijden in servertijd en duidelijke spelerweergave.
- Test Europe/Amsterdam DST-overgangen.
- Waarschuw vooraf op configureerbare momenten.
- Minimum online telt alleen geldige rosterleden.
- Gebruik grace en aanwezigheids-na-ijl om snelle logout te voorkomen.
- Implementeer beperkte postponements.
- Implementeer een vaste forfaitladder bij herhaald ontwijken.
- Combatlogout laat een veilige combat proxy of strafmechaniek achter zonder itemduplication.
- Pauzeer automatisch bij ernstige TPS/MSPT-problemen.
- Geplande restart/maintenance kan windows gecontroleerd verplaatsen.
- Na crash herstart een actieve sessie als `PAUSED`, nooit blind actief.

## 15. Raid engine

### 15.1 Objectives

Minimaal voor v1:

- War Core;
- Capture Point;
- Kill score;
- Breach score.

Ontwerp uitbreidbaar voor:

- multi-capture;
- fort control;
- bank-vault control objective dat alleen score geeft en nooit vrij banksaldo plundert;
- caravan/escort;
- command post.

Winnen mag niet uitsluitend om PvP draaien. Objectives, territory control, defense en teamwork wegen zwaarder dan losse kills.

### 15.2 Siege charges

Gebruik siege charges in plaats van een simplistische materiaalwhitelist.

- Charges zijn alleen bruikbaar door geldige participants;
- alleen tijdens active windows;
- alleen in contested regions;
- binnen budget;
- ieder geraakt/geplaatst/verwijderd block wordt gelogd;
- containers en inventarissen volgen aparte veilige regels;
- geen onbeperkte chain explosions;
- geen block entities dupliceren;
- tile entity data wordt herstelbaar opgeslagen;
- placement door defenders tijdens raid wordt eveneens correct gelogd wanneer nodig voor rollback;
- block physics en neighbor updates worden gecontroleerd.

### 15.3 Rollback en regeneratie

- Log originele blockdata, block entitydata, actor, tijd en oorzaak.
- Maak writes async in batches, maar world changes sync.
- Checkpoint minimaal iedere 30 seconden.
- Flush eventbuffer periodiek en op windoweinde/shutdown.
- Rollback in omgekeerde volgorde.
- Spreid herstel over ticks met configureerbaar budget.
- Laad geen enorme gebieden tegelijk.
- Maak rollback idempotent.
- Na crash moet rollback nog mogelijk zijn.
- Verifieer in tests dat before/after gelijk is.
- Voorkom dat spelers items dupliceren door containers tijdens restore.
- Claim niet dat rollback perfect is zonder scenario- en crashtests.

### 15.4 Score en einde

Configureerbare scorebronnen:

- core control;
- objective completion;
- capture duration;
- successful breach;
- defense;
- kills met lage of afnemende waarde;
- forfait.

Na alle windows:

- bepaal winnaar;
- betaal stakes;
- start recovery;
- herstel damage;
- pas tijdelijke occupation toe;
- schrijf history;
- start cooldown;
- unlock roster en territory;
- maak disputeperiode beschikbaar.

## 16. Conquest en occupation

Standaard geen vernietigende permanente annexatie.

Normale Siege:

1. alleen vooraf gekozen grensclaims of vooraf uitgesneden rechthoekige grensregions zijn contested;
2. winnaar kan een beperkt aantal tijdelijk occupyen;
3. originele eigenaar behoudt onderliggende geschiedenis;
4. occupation verloopt automatisch;
5. bewoners verliezen niet direct al hun persoonlijke spullen;
6. peace terms kunnen herstel of beperkte overdracht regelen.

Hardcore, standaard uit:

- permanent transfer alleen binnen harde caps;
- alleen border territory;
- expliciete leader-only bevestiging door beide kingdoms;
- een tweede bevestigingsscherm na een configureerbare wachttijd;
- admin-enabled serverpreset;
- duidelijke waarschuwing;
- geen core/home claim in één oorlog;
- geen volledige eliminatie;
- rollback/undo voor admins bij bugs.

## 17. Progressie

Maak lichte kingdomprogressie die samenwerking beloont zonder politieke of onoverbrugbare machtslagen te creëren.

Mogelijke meetwaarden:

- actieve members;
- ontwikkeld territorium;
- claim-blockdonaties;
- gezonde kingdom bankactiviteit;
- gewonnen of voltooide objectives;
- diplomatieke activiteit;
- gezamenlijke bouwmilestones;
- kingdomleeftijd en activiteit zonder AFK-farming.

Mogelijke unlocks:

- extra kingdomclaimcapaciteit met diminishing returns;
- extra outpostslot;
- extra visitorcapaciteit;
- meer history-retentie of cosmetische timelinebadges;
- cosmetische tags, banners en particles;
- conveniencefeatures zoals extra kingdom homes binnen harde caps;
- extra war templatevarianten binnen dezelfde veiligheidsregels.

Vermijd permanente grote health-, damage- of pay-to-winbonussen. Progressie geeft geen governmentvormen, stemrechten, politieke titels of laws.

## 18. Commands

Gebruik Paper’s moderne command API/Brigadier wanneer beschikbaar voor de gekozen doelversie.

Minimaal:

```text
/claim tool
/claim info
/claim show
/claim map
/claim list
/claim access
/claim flags
/claim trust <player>
/claim untrust <player>
/claim abandon
/claim transfer

/kingdom create <name> [tag]
/kingdom info [kingdom]
/kingdom invite <player>
/kingdom invites
/kingdom join <kingdom>
/kingdom leave
/kingdom kick <player>
/kingdom members
/kingdom promote <player>
/kingdom demote <player>
/kingdom transferleader <player>
/kingdom visitors
/kingdom visitor add <player> [duration]
/kingdom visitor remove <player>
/kingdom roles
/kingdom permissions
/kingdom claims
/kingdom pool balance
/kingdom pool donate <amount>
/kingdom pool withdraw <amount>
/kingdom bank balance
/kingdom bank deposit <amount>
/kingdom bank withdraw <amount> <reason>
/kingdom bank history [page]
/kingdom announce <message>
/kingdom diplomacy
/kingdom history
/kingdom progress
/kingdom home
/kingdom sethome
/kingdom disband

/diplomacy propose ally|truce <kingdom>
/diplomacy list
/diplomacy view <kingdom>
/diplomacy accept <proposal-id>
/diplomacy reject <proposal-id>
/diplomacy terminate <kingdom>
/diplomacy rival <kingdom>

/war propose <kingdom>
/war list
/war status [id]
/war view <id>
/war edit <id>
/war counter <id>
/war accept <id>
/war reject <id>
/war cancel <id>
/war surrender <id>
/war peace <id>

/raid status
/raid objective
/raid participants

/dispute open <war-id> <reason>
/dispute status <id>

/dominium tool
/dominium reload
/dominium version
/dominium debug
/dominium admin claimblocks give|take|set|balance ...
/dominium admin bank inspect|adjust|recover ...
/dominium admin ...
```

Maak aliases alleen waar ze geen verwarring veroorzaken. `/team` mag naar `/kingdom` verwijzen, maar primaire help en UX gebruiken kingdom.

Permissies:

- `dominium.use`
- `dominium.claim.*`
- `dominium.kingdom.*`
- `dominium.war.*`
- `dominium.admin.*`
- specifieke bypasspermissions met zichtbare bypassmodus.

Er bestaan geen `/kingdom government`, `/kingdom vote`, `/kingdom law`, `/kingdom succession`, `/kingdom settlement` of `/plot` commands.

`/dominium reload` mag alleen veilige configuratie herladen. Database- of schemamutaties vereisen restart.

## 19. Database

Ondersteun standaard SQLite in WAL-mode en optioneel PostgreSQL.

Minimale tabellen/aggregaten:

- schema versions;
- worlds;
- player profiles;
- player activity days;
- claim-block ledger;
- claim-block balances;
- claims met inclusieve rectangle geometry (`min_x`, `max_x`, `min_z`, `max_z`);
- claim geometry revisions voor veilige resize/audit/refund;
- claim spatial buckets die iedere intersecterende Minecraftchunk aan candidate claim IDs koppelen;
- claim flags;
- claim overrides;
- kingdoms;
- kingdom members;
- kingdom role permissions;
- kingdom invites;
- kingdom visitors;
- kingdom claimpool balances en ledger;
- kingdom bank accounts;
- kingdom bank ledger;
- kingdom bank operation journal;
- kingdom bank reservations;
- diplomacy proposals;
- diplomacy relations;
- war proposals;
- immutable proposal versions;
- direct kingdom approvals;
- wars;
- war rosters;
- raid windows;
- raid sessions;
- objectives;
- war events;
- money stakes en escrow operations;
- occupations;
- history events;
- audit log;
- disputes.

Maak geen tabellen voor plots, governments, laws, councils, elections, electorate snapshots, ballots, settlements of succession.

Gebruik:

- foreign keys;
- unique constraints;
- check constraints waar ondersteund;
- indexes op alle lookup- en foreign-keyvelden;
- world UUID, niet alleen worldnaam;
- idempotency keys;
- optimistic version of duidelijke locking bij gevoelige mutaties;
- transactions per aggregate use case;
- operation journals voor Vault/economy side effects;
- constraints waardoor `reserved_balance` nooit groter dan `total_balance` en geen balans negatief kan zijn;
- migratietests vanaf lege database en vanaf iedere ondersteunde eerdere schema-versie.

Gameplayprotecties lezen uitsluitend uit RAM/indexen. Bezit, claim-blockmutaties en interne geldreserveringen zijn pas succesvol na commit. Externe economy-mutaties volgen de gedocumenteerde duurzame operation state machine.

## 20. Performance

Doelen:

- duizenden claims;
- honderden spelers;
- meerdere gelijktijdige wars;
- geen databasecall in hot listeners;
- geen per-speler repeating tasks;
- geen chunkloads door claimchecks;
- geen opslag van één database- of geheugenrecord per beschermd block; rectangles en spatial buckets zijn verplicht.

Claimindex:

- per world een primitive long-key map waarvan de key een Minecraftchunk X/Z is;
- iedere bucket bevat een kleine immutable candidate set van claim-ID’s die die chunk geometrisch snijden;
- de chunkbucket is alleen een spatial index en bepaalt nooit ownership;
- lookup: bepaal bucket in O(1), controleer daarna exacte inclusieve rectangle containment op X/Z;
- bij create/resize/delete worden alleen geraakte buckets copy-on-write bijgewerkt;
- grote claims slaan niet ieder block op;
- overlapchecks gebruiken de union van candidates uit alle geraakte buckets en dedupliceren IDs;
- bordersegmenten en hoekpunten worden per region gecachet voor particles, adjacency en crossingchecks;
- - voeg benchmarks toe voor grote claims, veel kleine claims, overlappende bucketcandidates en beweging langs borders.

`PlayerMoveEvent`:

- return als blockcoordinate niet veranderde;
- territorylookup wanneer de speler een blockcoordinate verandert; gebruik de gecachte region en borderinformatie om binnen dezelfde rectangle vroeg te returnen;
- cache huidige territory context;
- no-access alleen bij grenspassage.

Events:

- vroeg returnen;
- geen zware streams/allocaties in hot paths;
- geen synchronisatie op ieder event;
- geen `BlockRedstoneEvent`-listener voor algemene permissionchecks;
- batch war-eventwrites;
- gespreide rollback;
- verwijder alle player session caches bij quit.

Voeg `/dominium debug timings` toe en documenteer een spark-profielprocedure.

## 21. Integraties

Optioneel en zonder harde runtime dependency:

- PlaceholderAPI;
- BlueMap;
- Dynmap;
- DiscordSRV;
- generieke Discord webhooks;
- Vault of een andere expliciete economy-adapter;
- LuckPerms voor adminpermissies;
- CoreProtect als aanvullend bewijs;
- WorldGuard als adminzone-coëxistentie;
- EssentialsX teleportintercept;
- TAB-coëxistentie.

Regels:

- Kingdomrollen worden niet in LuckPerms gemodelleerd.
- Raidrollback hangt nooit af van CoreProtect.
- Zonder optionele integratie blijft corefunctionaliteit werken.
- Zonder economyprovider blijven persoonlijke claims, kingdoms, visitors en claim-blockpool werken; money war proposals en kingdom-bank moneytransfers worden duidelijk uitgeschakeld.
- Iedere hook moet veilig detecteren of de plugin aanwezig en compatibel is.
- Voeg PlaceholderAPI-placeholders toe voor claimowner, kingdom, role, visitorstatus, relation, beschikbare/gebruikte/totaal verdiende claim blocks, kingdom claimpool, bank available/reserved/total balance, warstatus en raidscore.
- Economybedragen worden uitsluitend via de provider-API aangepast, nooit via commands of parsing van chatoutput.

## 22. Configuratie en lokalisatie

Maak minimaal:

- `config.yml`
- `database.yml`
- `claims.yml`
- `kingdoms.yml`
- `access.yml`
- `economy.yml`
- `diplomacy.yml`
- `war.yml`
- `raid.yml`
- `progression.yml`
- `gui.yml`
- `integrations.yml`
- `messages_en.yml`
- `messages_nl.yml`

Alles wat een balansgetal of gameplaydefault is moet configureerbaar zijn, waaronder visitorrechten, banklimits, minimum/maximum war stake, proposal expiry, reserveringtime-outs en disbandafhandeling.

Niet configureerbaar wanneer correctheid zou breken:

- claim-blockledgerinvarianten;
- bankledger- en reservationinvarianten;
- proposal versioning;
- idempotency;
- deny-wins op gelijk niveau;
- vaste rolehiërarchie `LEADER > CO_LEADER > MEMBER`;
- visitors hebben nooit standaard storage access;
- databaseconsistentie;
- crashherstelvereisten.

Maak geen `governments.yml`, `laws.yml`, `elections.yml`, `settlements.yml` of plotconfig.

Gebruik typed configmodellen en valideer bij startup. Geef duidelijke padgebonden foutmeldingen. Stop de relevante module veilig bij ongeldige kritieke config; start niet half met corrupte defaults.

## 23. Admin en moderation

Adminfuncties:

- inspectmode;
- claim info/history;
- claim freeze/unfreeze;
- transfer/delete;
- claimblocks grant/revoke met verplichte reden;
- kingdom inspect/modify;
- member, role en visitor inspect/correctie;
- leadership recovery bij langdurige inactiviteit;
- kingdom bank inspect;
- bank balance adjust met verplichte reden en dubbele bevestiging;
- vastgelopen economyoperation recover/compensate;
- reservation en escrow inspect;
- war ban;
- proposal inspect;
- war pause/resume/cancel;
- window reschedule;
- raid invalidate;
- manual rollback;
- score correction;
- stake refund/payout;
- occupation remove;
- safezone/warzone/route create;
- auditlog search;
- dispute review.

Iedere adminmutatie logt:

- actor;
- target;
- old value;
- new value;
- reason;
- timestamp;
- correlation id.

Disputes verzamelen automatisch:

- TPS/MSPT samples;
- join/leave tijden;
- roster;
- proposalhash en directe approvals;
- bank reservation- en escrowrecords;
- checkpoints;
- war event extract;
- score events;
- adminmutaties;
- relevante logs.

## 24. Teststrategie

### 24.1 Unit en property tests

Minimaal:

- claim-blockledgerinvarianten;
- bankledgerinvarianten;
- bank available/reserved/total invariant;
- idempotency;
- economy operation retries en compensatie;
- claim-block donate/withdraw/leave/kick/disband;
- bank deposit/withdraw/disband;
- claim placement;
- adjacency;
- buffer;
- outpost;
- enclosure flood-fill;
- flag precedence;
- deny-wins;
- vaste rolepriority;
- invite/join/leave/kick/promote/demote;
- visitor entry en door access;
- visitor storage denial voor iedere inventory-holdercategorie;
- indirecte visitor storage-exploits via hopper, minecart, crafter, dispenser en redstone;
- diplomacy state en cooldowns;
- war state machine;
- proposal hashing/canonicalisatie;
- versioningfuzzer;
- direct approval op exacte hash;
- proposal creation zonder voldoende bankgeld faalt;
- counter naar hoger stakebedrag zonder saldo faalt;
- reservering/release/escrow/payout/refund;
- raid score;
- occupation;
- DST scheduling.

### 24.2 Database-integratie

Test:

- SQLite;
- PostgreSQL via Testcontainers;
- lege migratie;
- upgrades;
- rollback bij transactiefout;
- unique constraints;
- concurrent-achtige retries;
- crashrecoveryrecords;
- bank operation journal;
- dubbele economycallback;
- reservation die bij restart intact blijft;
- payout die na crash niet dubbel uitvoert.

### 24.3 Listener- en testserverscenario’s

Maak een handmatige checklist en waar mogelijk geautomatiseerde tests voor:

1. Golden shovel:
   - exacte punt A/B;
   - inclusieve oppervlakteberekening;
   - géén chunkafronding;
   - selectie die midden in een chunk begint/eindigt;
   - minimale breedte/diepte;
   - overlap;
   - resize door hoekverplaatsing;
   - uitbreiding boekt alleen het verschil;
   - verkleining betaalt alleen het verschil terug;
   - overflow en maximumzijde;
   - preview;
   - cancel;
   - world change;
   - invalid selection;
   - permission loss tussen preview en confirm;
   - dubbelklik;
   - inventory/offhand;
   - normale shovel blijft vanilla;
   - speler zonder kingdom kan personal claim maken;
   - kingdommember kan standaard geen nieuwe personal claim maken.

2. Griefmatrix:
   - piston beide richtingen;
   - lava/water;
   - TNT;
   - falling blocks;
   - dispenser;
   - hopper;
   - pearl;
   - chorus;
   - portal;
   - bone meal;
   - tree growth;
   - frost walker;
   - wither;
   - projectiles;
   - wind charge;
   - vehicles;
   - armor stands/item frames.

3. Visitors en No Access:
   - visitor kan normaal kingdomgebied in;
   - visitor kan deuren, trapdoors en gates gebruiken;
   - visitor kan geen chest, barrel, shulker, furnace, hopper, decorated pot of crafter openen;
   - visitor kan storage niet via indirecte mechanics manipuleren;
   - visitor removal werkt direct en veilig;
   - visitor expiry;
   - individuele deny wint;
   - lopen;
   - vliegen;
   - elytra landing;
   - vehicle;
   - login;
   - respawn;
   - teleport;
   - pearl;
   - opsluitscenario;
   - veilige eject.

4. Kingdom lifecycle en claimpool:
   - create/invite/join;
   - promote/demote;
   - kick/leave;
   - leadership transfer;
   - claim-block earn;
   - donate;
   - spend;
   - delayed withdrawal;
   - maintenance;
   - disband;
   - crash/retry.

5. Kingdom bank:
   - member, co-leader en leader kunnen depositen;
   - onbevoegde member kan niet withdrawen;
   - withdrawlimit;
   - Vault failure vóór en na externe mutatie;
   - retry zonder duplicate;
   - available/reserved/total blijven consistent;
   - auditledger;
   - disband met open operation wordt geblokkeerd.

6. War end-to-end:
   - proposal zonder saldo faalt;
   - proposer stake wordt gereserveerd;
   - target zonder saldo kan niet accepteren;
   - counter past reservering veilig aan;
   - exacte hashacceptatie door leader/co-leader;
   - geen votingflow bestaat;
   - beide stakes naar escrow;
   - prep locks;
   - raid windows;
   - objectives;
   - rollback;
   - winnaar ontvangt eigen stake terug plus stake verliezer;
   - draw/refund;
   - forfeit;
   - occupation;
   - peace;
   - history.

7. Restart en hard crash mid-raid:
   - sessie komt terug als paused;
   - budget en score kloppen;
   - rollback blijft beschikbaar;
   - reserveringen blijven correct;
   - geen dubbele payout.

8. Performance:
   - seed 5.000–10.000 synthetische rechthoekige claims van wisselende afmetingen en bucketdensiteit;
   - twee gelijktijdige wars;
   - hot listener p95-doel documenteren;
   - TPS stabiel op realistische hardware;
   - memoryleaks bij join/quit en GUI-open/close.

## 25. Uitvoeringsfases

Werk in deze volgorde. Iedere fase eindigt buildbaar en getest.

### Fase 0 — Fundament

- repositoryinspectie;
- build;
- modules;
- CI;
- config;
- service registry;
- databaseconnectie;
- migrations;
- claim-blockledger;
- bank operation model;
- basisdocumentatie.

Gate:
- clean build;
- ledger property tests groen;
- plugin start/stop zonder errors.

### Fase 1 — Claims en golden shovel

- claimindex;
- claim entities;
- placement validator;
- golden shovel PDC;
- personal/kingdom/inspect modes;
- selection state;
- exact block-based rectangle preview;
- region resize door hoekverplaatsing;
- claimblockkosten als inclusieve `width × depth`;
- confirmation;
- `/claim` commands;
- particles;
- maps;
- persistence.

Gate:
- alle shoveltests;
- personal-versus-kingdomregels;
- placementtests;
- lookupbenchmark.

### Fase 2 — Protecties

- build/interact/entity/environmentlisteners;
- flagmatrix;
- overrides;
- presets;
- cross-border protection.

Gate:
- volledige griefmatrix;
- geen merkbare hot-path regressie.

### Fase 3 — Visitors, No Access, HUD en activiteit

- kingdom visitor audience;
- visitor deurpreset en storage denial;
- no-access;
- safe eject;
- actionbar;
- player session cache;
- active play;
- earn caps;
- inactivity expiry.

Gate:
- visitor exploitmatrix;
- no-accessscenario;
- 7-daagse earnsimulatie;
- restartpersistency.

### Fase 4 — Kingdom lifecycle en rollen

- kingdom create/disband;
- invites;
- membership;
- vaste roles;
- permissions;
- promote/demote;
- leadership transfer;
- kingdom home;
- kingdomclaims;
- history/audit.

Gate:
- role matrix tests;
- join/leave/kick flows;
- geen government-, vote-, plot- of settlementcode.

### Fase 5 — Claimpool en kingdom bank

- kingdom claim-blockpool;
- claim-blockdonaties;
- delayed withdrawals;
- maintenance;
- bank account;
- member deposits;
- authorized withdrawals;
- bankledger;
- durable economy operations;
- available/reserved/total balances;
- recovery tooling.

Gate:
- geen duplication in property tests;
- economy failuretests;
- iedere member kan depositen;
- onbevoegden kunnen niet withdrawen.

### Fase 6 — Diplomatie

- relations;
- ally/truce proposals;
- rival label;
- cooldowns;
- GUI;
- relation-to-flag integration.

Gate:
- state/cooldowntests;
- ally privileges correct;
- geen vassalage of politieke treaties.

### Fase 7 — War proposals en stake reservations

- templates;
- immutable versions;
- canonical hash;
- diff GUI;
- directe leader/co-leader approval;
- mandatory money stake;
- proposal-time proposer reservation;
- accept-time target reservation;
- counter reservation adjustments;
- expiry/reject releases;
- escrow.

Gate:
- versioningfuzzer;
- onvoldoende saldo blokkeert create/accept/counter;
- geen accepted state met verschillende hashes;
- geen votingtabellen of votingflow.

### Fase 8 — Scheduling en preparation

- raid windows;
- timezone/DST;
- roster;
- contested regions;
- prep locks;
- bank reservation locks;
- notifications;
- bossbar;
- restart recovery.

Gate:
- schedulingtests;
- alle lock bypasspogingen falen.

### Fase 9 — Raid engine

- raid sessions;
- War Core;
- capture objective;
- siege charges;
- combat rules;
- score;
- TPS/MSPT recorder;
- checkpoints;
- eventlog.

Gate:
- end-to-end raid;
- crash mid-raid;
- geen duplication.

### Fase 10 — Rollback, outcome en conquest

- block restore;
- block entities;
- payout/refund;
- winner/loser stake payout;
- temporary occupation;
- peace/surrender;
- Hardcore bounded transfer;
- disputeperiod.

Gate:
- before/after worldcomparison;
- idempotent restore;
- idempotent payout;
- verliezer verliest exact de afgesproken stake.

### Fase 11 — Progressie, admin en integraties

- lichte teamprogression;
- history polish;
- admin suite;
- disputes;
- PlaceholderAPI;
- map hooks;
- Discord;
- Vault/economy adapter;
- CoreProtect evidence;
- docs.

Gate:
- integraties zijn soft behalve economyvereiste voor money wars;
- adminacties auditbaar;
- dispute evidence compleet.

### Fase 12 — Releasehardening

- volledige regressietest;
- performanceprofiling;
- config validation;
- migrationtests;
- language review;
- player guide;
- admin guide;
- API docs;
- changelog;
- release jar.

Stop niet bij de MVP. Fase 0–5 vormt een vroege speelbare release, maar de opdracht eindigt pas na fase 12.

## 26. Verplichte documentatie

Lever:

- `README.md`
- `CHANGELOG.md`
- `LICENSE` alleen wanneer repositorybeleid/licentie bekend is; verzin geen licentie bij twijfel
- `docs/ARCHITECTURE.md`
- `docs/GAME_DESIGN.md`
- `docs/DATABASE.md`
- `docs/CONFIG_REFERENCE.md`
- `docs/PLAYER_GUIDE_NL.md`
- `docs/ADMIN_GUIDE_NL.md`
- `docs/API.md`
- `docs/TEST_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/MIGRATIONS.md`
- `docs/SECURITY_AND_ABUSE.md`

Documenteer installatie, database, permissions, commands, golden shovelbediening, persoonlijke claims, kingdomrollen, visitors, storage denial, kingdom bank, war stakes, backups, upgrades, rollback, recovery en troubleshooting.

Documentatie mag kingdoms niet beschrijven als regeringen of staten. Leg expliciet uit dat het uitgebreide teams zijn met één leader, optionele co-leaders en members.

## 27. Definition of Done

De opdracht is pas voltooid wanneer:

- de volledige repository compileert;
- alle geautomatiseerde tests groen zijn;
- een release-jar is gebouwd;
- plugin enable en disable schoon werken;
- SQLite en PostgreSQL ondersteund en getest zijn;
- alle migraties aanwezig zijn;
- geen database-I/O op de main thread plaatsvindt;
- geen worldmutatie async plaatsvindt;
- claims via de gemarkeerde golden shovel volledig werken;
- claims exacte block-based rectangles zijn en nooit naar hele chunks worden afgerond;
- create, inspect, resize, expand, shrink en delete correct met claim blocks verrekenen;
- claim blocks via actieve speeltijd, admincommands en idempotente externe API-rewards kunnen worden verdiend;
- persoonlijke claims voor spelers zonder kingdom volledig werken;
- kingdomclaims zonder plots of subclaims werken;
- protecties en cross-border exploits zijn afgedekt;
- visitors kingdomgebied en deuren kunnen gebruiken, maar geen storage blocks of indirecte storageflows;
- no-access werkt zonder opsluiten of rubberbandspam;
- kingdoms met `LEADER`, `CO_LEADER` en `MEMBER` lifecycle volledig werken;
- invites, promote, demote, kick, leave en leadership transfer correct werken;
- geen government-, election-, voting-, law-, succession-, settlement- of plotfunctionaliteit aanwezig is;
- kingdom claimpool en ledger geen duplication toelaten;
- iedere member geld in de kingdom bank kan depositen;
- alleen bevoegde rollen kunnen withdrawen of warstakes reserveren;
- kingdom bankledger en economy operation recovery crashbestendig zijn;
- diplomacy met neutral, ally, rival, truce en at-war werkt;
- ieder war proposal verplicht een stakebedrag bevat;
- proposal creation zonder voldoende proposerbanksaldo faalt;
- acceptance zonder voldoende targetbanksaldo faalt;
- war proposal versioning en directe exacte-hashacceptatie correct werken;
- raid windows, objectives, siege charges en rollback werken;
- stake reservations, escrow, payouts en refunds idempotent zijn;
- de winnaar de eigen stake terug plus de stake van de verliezer ontvangt;
- occupation en bounded conquest werken;
- history, audit en disputes werken;
- configs en NL/EN messages compleet zijn;
- admin- en playerdocumentatie aanwezig zijn;
- geen kritieke `TODO`, placeholder of nepimplementatie overblijft;
- bekende beperkingen eerlijk in documentatie staan;
- `docs/IMPLEMENTATION_STATUS.md` ieder onderdeel als `TESTED` of expliciet onderbouwd `BLOCKED` toont;
- je in je eindrapport exact noemt welke commands je hebt uitgevoerd en welke tests werkelijk zijn geslaagd.

## 28. Start nu

Begin nu met:

1. repositoryinspectie;
2. versie- en builddetectie;
3. architectuur- en statusdocumenten;
4. fase 0;
5. daarna alle fases op volgorde.

Werk rechtstreeks in de bestanden. Geef geen lang theoretisch antwoord voordat je begint te implementeren. Meld alleen kort wat je hebt aangetroffen, welke technische basis je kiest en voer daarna het werk uit.
