# Changelog

Every release of Sonar, newest first. The version number is also shown at the bottom of the app's left rail.

## 2.7.0

**Updating works again, and so do the character list and exaltation stats**
- Every packaged build since 2.0.0 shipped a Java runtime trimmed of `jdk.crypto.ec`, which left it with no elliptic-curve provider: 17 cipher suites and not one of them ECDHE. GitHub accepts nothing else, so the update check died on "handshake_failure" every time, and said so in a dialog.
- It was never only the updater. The same failure hit the game's own server, so the character list and the exaltation figures Sonar fetches after login had been failing silently on every install as well.
- The module is back in the runtime. It costs 91 KB. A build made from source on an ordinary JDK was always fine, which is why this survived four releases.
- An install from before this release cannot update itself to it, because updating is the broken part. Install this one over the top once and the updater works from here on.

**The Live page shows what there is to show**
- Standing in the Nexus, the vault, a guild hall, the pet yard, the quest room or a bazaar, the main window now lists the players around you - which fills the moment the sniffer starts. The damage list sat empty in those places until a dungeon began.
- Step into the realm or any dungeon and it becomes the damage list again. Your character's stat block takes the place of the fight panel while there is no fight.

**The Players page is about the player**
- The damage bars run along the bottom, under the roster and the gear: clicking a bar picks that player, and the numbers sit beneath what explains them.
- The right-hand column now holds the selected player and where their damage came from, together, instead of the per-item list sitting under a roster it had nothing to do with.

**Friends, out of the page and into the header**
- The party list was never about the instance you were standing in: it is kept between runs and is the same wherever you go, while every window around it described one dungeon. It is now a Friends button in the header, reachable from every page, carrying the count without being opened.

**The Instance page is for looking back**
- Rebuilt around reviewing a finished run: the list of runs down the left, then the summary of the one you picked, what was in it, and where the damage went.

**Reading the bars**
- The bars start after the names rather than under them. A name on a bar had to hold its own against a different colour on every row; on the backdrop it needs nothing but the theme's own text colour.
- Enchant glows are a real glow now - blurred, fading out - rather than four rings a pixel apart, which read as a thick border. They are deepened rather than brightened so they stay darker than the class bar underneath instead of merging into it, and Divine is gold rather than orange.

**Sharper figures**
- The big numbers were drawn with subpixel anti-aliasing, which only lands true on the screen's own pixel grid - and every panel draws through the zoom. That left 251 colour-fringed pixels on a three-digit number at normal size and 338 when zoomed. They are drawn with grayscale anti-aliasing now, which is correct under a transform.
- On the lit themes the bloom behind those numbers began one pixel from the glyph, putting a copy of the number against its own edge. It starts clear of it now, so the figure is lit rather than blurred.

## 2.6.0

**What a Crucible or Blood Ritual is doing to a character, said plainly**
- The stats panel carries a line naming the crucible and the ritual step the chosen player has on, and what each one changes: "S31 Crucible: VIT x1.30 WIS x1.10  ·  Blood ritual 1: health x0.80 damage x1.24".
- The figures are the server's own, read from the table it sends. They are shown as multipliers rather than folded into the stats above, because the game sends stats with none of it applied - a maxed Huntress reads her bare class maximum whatever she is wearing - and quietly scaling them here would invent a total that nothing else agrees with.
- The demo carries a real captured payload, so its ids decode to real figures rather than to nothing.

## 2.5.1

**Potions to max was wrong for exalted characters**
- The exalt figure the game sends is the bonus itself, not a level to convert, and it was being multiplied again - so a fully exalted health showed as +125 rather than +25 and was measured against a ceiling a hundred points too high.
- Worse, exaltation was being added to the potion ceiling at all. It never belonged there: potions stop at the class maximum, exaltation sits above it, and the figure the game reports does not include it either. A Huntress maxed on all eight stats was being told she wanted twenty-five more life potions.
- Found by reading a crowded server. The quiet one the client opens on had nobody maxed and exalted, so nothing there could have shown it.

**The sum is now checked against players Sonar really saw**
- Three characters read off the wire, with the numbers exactly as the game sent them, kept as a standing check: a Huntress finished on all eight stats, a Knight twenty life and four mana short, and a Druid seven speed short while wearing a blood ritual. Twenty-four assertions, run against the same code the stats panel uses rather than a copy of it.

## 2.5.0

**A run you joined partway through is kept rather than thrown away**
- Starting the sniffer after a dungeon has begun used to leave Sonar with nothing: the one packet that names an instance arrives at the map load and never again, and without it the run was collected, shown, and then dropped when the instance ended. It is kept now, marked as started late, and the damage it did see is real damage.
- Whether a fight was watched from its beginning is judged per fight, not per run. An enemy first seen at full health was fought from the start, so its record is complete and its numbers mean what they say - and since bosses are fought at the end, a run joined during trash usually holds a complete record of the fight people actually care about.
- A boss already part-beaten when Sonar arrived says how much it missed instead of reporting a share of health that would flatter whoever turned up late.
- The instances list keeps the two apart: a late run is marked "Started late" and coloured differently, and the count reads "5 finished · 2 late".

**Sonar works out who you are when it was not told**
- Which player is you arrives once, with the map load. Join late and nothing was ever marked as you: your own row was not yours and the You panel stayed empty. Every character on an account carries the account's name, so the name Sonar saw last time it did know is enough to find you again.

**The Players page fills in without a dungeon**
- The roster is built from who has been seen, reading their gear straight off them, but it was thrown away whenever the instance was unknown. Standing in the Nexus with the sniffer just switched on now lists everyone there, with their gear and stats, rather than showing nothing.

**Potions to max, rather than points to max**
- The stats panel showed how many points short of maxed a stat was. It shows potions now, which is the only unit anybody counts in: a life potion is worth five, every other potion one.
- Exaltation sits above the potion ceiling and never moves it, so an exalted character is measured against the same maximum as anyone else.

**Seasonal, Crucible and Blood Ritual, read off anyone in sight**
- The two marks the game draws beside a name - green for seasonal, red for crucible - now appear in Sonar's roster in the same two colours, so there is no second convention to learn.
- And the Blood Ritual besides, which the game knows about and shows nobody. Hovering says what it costs and pays: health, damage and loot.
- The figures are read from the table the server itself sends rather than written down here. They change between seasons - this season's live loot values are already half what the patch notes said, and the notes leave out the Crucible's wisdom bonus entirely.

**Fixes**
- The damage ledger switched itself off whenever the instance was unknown, which is exactly the run that most needs checking. It follows a joined run now.
- The self-test says what it needs instead of throwing. It drives the real interface, so it wants demo data to act on; run without it, it reported three ordinary-looking failures and then fell over an empty list, which reads as a broken app rather than a missing flag.

## 2.4.0

**Neon: a new look, and the one Sonar starts on**
- A seventh theme, built around a black-violet ground with cyan and magenta over it, and the one a fresh install now opens in. The figure each panel exists to show is drawn in the accent with a soft bloom behind it, rather than every panel opening with the same white number.
- Panel edges and dividers are part of the theme now. They were a fixed white wash, which suits a grey ground and reads as grime on a vivid one; every border in the interface follows the theme without any panel being touched.
- Two accents to go with it, cyan and magenta, and cyan is what a fresh install highlights with.
- The old look is kept exactly as it was: choose Plum for the ground and Peach for the accent. Nothing was removed.
- An install sitting on the previous default moves across once and is marked, so choosing Midnight again sticks. An install that had deliberately picked anything else is left alone.

**Two things that ignored the theme, fixed**
- The look and feel had the plum hue written into it, so the widgets it paints for itself - scrollbars, tooltips, popups, combo lists - stayed plum whichever theme was chosen. They follow the chosen ground and accent now.
- A filled button's ink was a fixed plum-black rather than the theme's own ground showing through.

**The damage calculator's colours are unchanged, deliberately**
- Its build colours were already constants and never followed the theme. The enchant level colours are pinned now too, so a level means the same colour on every ground: a legend has to mean the same thing in every screenshot.

**Where the MVP placed on each of the run's bosses**
- Small icons along the top right of the MVP card, one per main boss of the run, each with where that player finished on it. First place carries a gold edge.
- Which fights count is worked out from the size of the fight inside the instance actually being run. The game's own labels cannot answer it: they call two of the Shatters' three bosses MINIBOSS, and they call Marble Colossus, Malus and the Void Entity all BOSS out of one shared file. Size gets the Shatters' three and Lost Halls' one right.
- A boss the player never damaged is still shown, dimmed with a dash, so a card with a gap in it says the run had a gap rather than looking like a different dungeon.

**The MVP card's loadout, rewritten**
- Two across and two down, with every enchant nested under the item it is actually on and drawn with the game's own icon for that enchant rather than a gem for its rarity.
- Nothing is cut any more. Item and enchant names wrap, and the card measures its own height from what it will really draw, so a heavily enchanted kit grows the card instead of running off it.

**Demo mode is a real account now**
- Every character wore a skin id that was not a skin. It was the Rogue class entry for a third of them and nothing at all for the rest, which is where the grey box on every character in every screenshot came from. Skins come from the game's own data, matched to the class, with dyes - a path that had never once run.
- Characters, Collection, Party finder and Fame used to show their empty state, and the Calculator had nothing equipped, so its curve was flat and its colour-coded build bars never drew. There is a consistent six-character account behind all of them now, built through the same code the live path uses.
- The demo checks itself the way it already checked equipment: a character wearing a skin its class cannot wear stops it outright, rather than quietly drawing a box.

## 2.3.0

**Share the MVP on their own, gear and all**
- A second share button on the damage board, a star leaving rather than the plain arrow. It copies one player as a card: the character as they look in game, their class, what they were holding, and the enchants on each piece.
- Who it picks is whoever did the most damage to the final boss - the fight that actually mattered - with their total across every boss in the run printed underneath as the figure that backs the choice up rather than makes it. Somebody who turns up for one phase and bursts it does not quietly outrank the player who carried the whole run.
- Also on the card: damage on that boss and what share of its health that was, damage per second, biggest hit and hit count. Enough to answer how they got there, not just that they did.
- A run with no boss falls back to whoever did the most damage overall, and says "Top damage" rather than "MVP".
- Copies to the clipboard and saves to disk, the same as the board share, so it can go straight into a Discord.

**The chat keywords can be edited again**
- The list of words that ring the private-message ping has always been read and acted on, but the window that edited it went with the old interface, so entries could be used and not changed. Settings has a "Words to watch for" section now.
- A word in quotes only counts as a whole word, which is what the matcher has always keyed on. The section shows that as a "whole word" tag rather than making anyone remember the quoting.

**"join" counts as a dungeon call**
- Said on its own it means the same as "j", and the ping did not fire for it. It does now, with the same gating: party chat, and not while you are already inside one. "join my guild" and "joining" still do not, being a recruit and a sentence rather than a call.

**A fresh install rings for both the things worth hearing**
- The dungeon call used to be off by default while the white bag ping was on. They do two different jobs - getting into a run, and not missing a drop - and both are on now. An install that has already been through Settings keeps whatever it was set to.

---

216 self-test assertions pass.

## 2.2.0

**Unpack it and run it. That is the install.**
- Sonar brings its own Java now. There is no JDK to find, no version to get right, and no setup script to run before it will open - unpack the zip and double-click `Sonar.exe`.
- The download is bigger because of it, about 36 MB instead of 8. That seemed a fair trade for removing the step most people got stuck on.
- The first time it opens it offers to put a shortcut on your desktop and in the Start menu, so you do not have to come back to the folder.
- Item and enemy names and pictures still come from the game's own files, read on first open. That takes a moment once and is then kept.

**Npcap is the only thing left to install**
- It is the driver that lets Sonar see network packets, and its licence does not allow Sonar to carry it. If it is missing Sonar says so when it opens, with the link, rather than letting Start sniffer fail with no explanation. Everything else works without it.

**Updating from 2.1.0 moves your files for you**
- Sonar's settings, characters, history and assets used to live in a `run` folder beside the jar. They now sit next to the program. Updating moves them up to meet it, so there is nothing to do and nothing to lose.

---

209 self-test assertions pass.

## 2.1.0

Groundwork for a one-click install. The next release is the one you unpack and run
with no Java and no setup script; this one is what lets your copy update into it
without being reinstalled by hand.

**Sonar stops scattering files**
- Every file it read or wrote resolved against whatever folder it happened to be started from. Launch it from somewhere unexpected and settings, saved characters and extracted assets were written there, or it quietly failed to find data it had already extracted. It now keeps one folder and uses it wherever it is run from.

**It can update into the new shape**
- The next release puts Sonar's files beside the program instead of in a `run` folder. This version understands both, and moves your settings, characters, history and assets up to meet them when that happens - so the change costs you nothing and needs nothing doing by hand.

**It says when Npcap is missing**
- Rather than letting Start sniffer fail with no explanation, Sonar now says so when it opens, and offers the download page. Everything else works without it.

---

209 self-test assertions pass.

## 2.0.0

Sonar was built on someone else's app. It is not any more: the interface it inherited
and never drew is gone, the packet library is part of the project rather than a
dependency bolted to it, and every line is Sonar's own package. Nothing here says
Tomato or RealmShark. That is what the 2 is for.

**Sonar starts about ten seconds faster**
- It was asking a server for crucible data at every launch, and that server has been switched off. Each start sat waiting for it to time out before carrying on. The request is gone; crucible bonuses come from the game's own packets, which is where they came from anyway once the wait finished.
- The chat spam list was fetched from the same dead server. Your own `block.txt` still works as before.

**The settings file is now called sonar.properties**
- It was still `realmShark.properties`, named after the project Sonar grew out of. Your settings move across by themselves the first time you open this version - window layout, theme, accent, starred party, wardrobe, sound pings, all of it. Nothing to do and nothing to lose.

**Nothing says Tomato or RealmShark any more**
- The last of the old names are out of the app, the setup, the instructions and the window. The licence file stays, as it must: Sonar is built on MIT-licensed work and that notice ships with every copy.

**Everything behind this, from 1.71.0**
- The interface Sonar inherited and never drew was removed, along with the packet library's separate build. It is one project now, which is why this release builds to `Sonar.jar` directly instead of a differently-named jar that got copied.
- Three things that lived inside the old interface were moved rather than lost: starting and stopping the sniffer, the loot bag pings, and the chat pings. Bag pings now ring from the moment you open Sonar; they used to stay silent until the game had sent your exalt data.
- Gone with it: the keypop and dungeon-pop sounds, which needed a dungeon list only the old hidden menu could set, so they never rang. The custom chat-ping keywords still ring but can no longer be edited. `dungeon.stats` and `FameSessions` are no longer written; nothing read them back.

---

209 self-test assertions pass.

## 1.71.0

**The rename is finished everywhere, not just on screen**
- 1.64.0 changed the name and the icon. Everything underneath still said Peach: the program file, the folder it installs into, the zip you download, the settings it saves. That is all Sonar now — `Sonar.exe`, `Sonar.jar`, `Sonar-<version>-windows.zip`, and the install folder.
- Your saved settings move with it. The window layout, the theme, the accent, the starred party, the wardrobe and the Shatters overlay's position are all stored under a name that has changed, so the app renames them for you the first time it opens. Nothing resets and nothing is left behind, and it only does this once.
- The code underneath still says `peach` in places on purpose, and that is staying. This is a fork of RealmShark, and renaming its packages would turn every future update from upstream into a fight over something you would never see.

**Midnight is what a fresh install starts on**
- The deep navy is the ground the Sonar mark was drawn against, so it is what the app opens with now instead of the old plum. A theme already picked in Settings is kept exactly as it is — this only changes what a new install looks like on its first run.

**This one update has to be done by hand**
- Older versions look for a file called `Peach-1.71.0-windows.zip`, and this release is called `Sonar-1.71.0-windows.zip`, so **Check for updates** cannot fetch it. Download the zip below, unpack it to its own folder and run `Setup.bat`. Updating from inside the app works normally again after this.

---

209 self-test assertions pass.

## 1.70.0

**The Graveyard tab is gone; the Characters page says it instead**
- A whole page for something that happens a few times a month was the wrong shape. A character the account has stopped listing now stays on the Characters page, sorted to the bottom, faded, tagged **LOST**, with how long ago it went: *"Rogue - Level 20, 3,570 fame, lost 2 hours ago"*. It keeps its gear, stats and pet, so the card still opens.
- It says "lost" rather than "died" on purpose. The account list looks identical whether a character died or was deleted, and Peach cannot tell those apart.

**A loss is noticed when you log in**
- The full character list is the only thing that reveals a death, and it was only ever asked for in the Pet Yard and the Daily Quest Room - so a loss went unseen until you happened to wander into one. There is now one quiet attempt per login as well. The server is said to refuse this elsewhere; if it does, nothing is lost and nothing is said, and those two rooms work exactly as before.

---

209 self-test assertions pass. Update from inside the app with **Check for updates**, or download the zip below.

## 1.69.2

**The window icon was one 256px image, scaled down by Windows**
- The .ico fixed in 1.69.1 is what Windows uses for the shortcut. The taskbar button of a *running* app uses the window's own icon, and that was being set from a single 256px PNG - so Windows scaled 256 down to 24 itself, badly, and the mark came out soft however crisply it had been drawn. Verified first that the new icon really was embedded in the installed exe, byte for byte, which ruled the exe out and left this.
- Swing is handed the whole set now - 16, 24, 32, 48, 64, 128 - each cut for its own size, so Windows picks an exact match and scales nothing.

---

208 self-test assertions pass. Update from inside the app with **Check for updates**, or download the zip below.

## 1.69.1

**The taskbar icon was one 16px frame stretched to fit**
- My fault, introduced with the rename in 1.64.0. An .ico holds several sizes, and Pillow builds one by resizing whatever image it is handed - so handing it the 16px cut produced a file with a single 16px frame in it. Windows then scaled that up to fill the taskbar, which is what made it look smushed. The file is now built from the 256px master with each hand-cut size appended, and the build fails outright if any frame goes missing rather than shipping quietly.
- The mark is cut three ways instead of two, so it stays readable all the way down. Above 32px it is the full three arcs fading outward. Between 21 and 32 it drops to two arcs, heavier, with the gaps widened. At 16px and below it is one arc - at that size a second is noise rather than detail - and the whole thing is scaled up to fill the box instead of sitting small in the middle.
- Every size is downsampled with a box filter at a whole-number ratio now. The old LANCZOS resample softened the edges and closed what little separation there was.

---

208 self-test assertions pass. Update from inside the app with **Check for updates**, or download the zip below.

## 1.69.0

**The minimap is not always centred on you**
- This was the real fault behind every "it moves but it is not accurate". The minimap follows the player, but it will not scroll off the edge of the map - so on anything small, the Nexus or a short dungeon, the whole map sits in the frame and you move about inside it. The overlay assumed you were in the middle always, which put every mark out by however far from the middle you actually were. On a realm it looked fine, because a realm is thousands of tiles wide and the clamp never bites.
- Marks are placed against where the view is really looking now: on you in a realm, on the map's own middle in the Nexus, and somewhere between the two when you are near an edge. While calibrating, the gold diamond is drawn where the game actually draws you rather than always at the centre.

**Calibrate the scale with one right-click**
- Scrolling until it looks right was guesswork. Peach knows exactly how many tiles away each mark is; what it cannot know is how many pixels the minimap gives a tile. **Right-click the game's own icon** for something and it has both halves - pixels clicked divided by tiles away - and sets the scale exactly, in one go. The mark used is whichever lies nearest the direction clicked, and a click nowhere near one is ignored rather than taken as a measurement.

**Your portrait showed the character you used to play**
- Damage rows are built from hit records, which carry no skin, so they fall back to how that name last looked. Someone who dies on a Rogue and rolls a Wizard kept being drawn as the Rogue - including you. A remembered look now only applies while it is still the same class; otherwise the plain class sprite stands in until the game describes them again.

---

208 self-test assertions pass. Update from inside the app with **Check for updates**, or download the zip below.

## 1.68.1

**The purple survived every theme**
- Two things were hardcoded plum from back when there was only one theme: the ambient glow in the top corner, and the lift on every panel's top edge. Neither looked at the theme, so choosing Obsidian or Midnight still left a purple wash over the header and down every card - which reads as the theme being broken rather than as a colour being wrong.
- Both are taken from the chosen ground now. Obsidian's glow comes out at hue 220 against its ground's 223, Midnight's at 225 against 222, and Plum and Wine stay exactly as purple as they are supposed to be. The gradient is the same shape in every theme; only its hue moves.
- If it is deep blue you are after, **Midnight** was already in the list and is within a few points per channel of a hue-shifted Wine.

---

200 self-test assertions pass. Update from inside the app with **Check for updates**, or download the zip below.

## 1.68.0

**The overlay's scale was about six times too small**
- It started at a flat 0.6 pixels per tile whatever the screen, whatever the zoom. On a 1440p minimap the real figure is close to 4, so every mark collapsed into a heap around the middle - which reads as the overlay failing to find enemies and portals rather than as a scale being wrong. Nothing was being missed; it was all being drawn in the wrong place.
- The starting scale is derived now. Each press of the game's zoom halves how many tiles fit, so the steps run 480, 240, 120 tiles across; dividing the overlay's own width by that gives the scale. A 479px window on the middle step comes out at **3.99 px per tile**, which is what measuring a real minimap gives.
- A saved scale identical to the old flat 0.6 is treated as never calibrated and gives way to the derived figure, so this reaches anyone who already had the bad value stored. The ceiling on the scale went from 8 to 64, which the top two zoom steps were previously pinned under.

**Recentre goes where you put it**
- Pressing **Done** after calibrating records that placement, and Recentre returns to it from then on. Before, it only ever went to a computed guess.
- The computed guess is better too, for a first run: measured against where the minimap actually sits rather than estimated. It is flush to the right edge, three pixels down, and sized off the screen's height so an ultrawide does not get a huge one.

---

198 self-test assertions pass. Update from inside the app with **Check for updates**, or download the zip below.

## 1.67.0

**The overlay follows the minimap's zoom**
- It was calibrated at one zoom and stayed there, so zooming the minimap left every mark at the wrong distance. The minimap's zoom level is stored nowhere Peach can read - it is in no packet and no game file, and the registry keeps only the camera's - so it cannot be followed directly. The key bindings can be read, though, and that is enough.
- Peach now watches **the same two keys the game does**, read from Exalt's own settings. Press minimap zoom and both step together. There is no second shortcut to learn, and rebinding the keys in game moves Peach with them.
- Each zoom step keeps **its own calibration**. Zooming does not move the minimap or resize it, only how many tiles fit, so the overlay never needs re-aiming - only re-scaling. Calibrate a step once and stepping back to it restores an exact figure instead of a guess. The calibration readout says which step is being set.
- Ctrl+shift+plus and ctrl+shift+minus step the overlay alone, for nudging the two back into line. One known cost: the game's binding is a bare key, so typing it into chat steps the overlay too - one press puts it back.

---

195 self-test assertions pass. Update from inside the app with **Check for updates**, or download the zip below.

## 1.66.0

**The Shatters overlay can be tested in a realm**
- The dungeon is rare, so whether the overlay was lined up could only be answered by getting into one. **Portals: on** switches it to marking portals and nearby enemies instead, anywhere at all - so it is answerable in a realm, or the Nexus, in ten seconds.
- Two references, because they answer different questions. **Portals** never move, and the game draws each on the minimap, so they settle position and scale: if Peach's mint ring sits on the game's portal icon, the overlay is right. **Enemies** always move - a realm god wanders constantly - so the coral dots settle whether a mark actually follows something, rather than only being right while everything stands still.
- Only the ten nearest enemies are marked, or a populated realm is a wall of dots that proves nothing. The sidebar reads "test: 4 portals · 10 enemies", and each mark carries its distance in tiles while calibrating.

---

191 self-test assertions pass. Update from inside the app with **Check for updates**, or download the zip below.

## 1.65.0

**A Graveyard page**
- The game sends no death message that can be tied back to a character, but it does answer with the whole account list - so a character that was there and is now gone has been lost, and everything already remembered about it becomes its gravestone: class, level, fame, pet, and the four items it was carrying. A character lost while the app was closed still turns up the next time the list arrives.
- Scoped to the seasonal flags actually present in the answer, because the game can send seasonal and regular separately and comparing one against the other would bury every character on the other side. It cannot tell a death from a deliberate delete, so it says "lost" rather than "died". The lost no longer clutter the Characters page.

**Skins and dyes were being forgotten within hours**
- The remembered-looks cache held 500 people, sized on the idea that a guild is smaller than that. One max-pop realm puts over a hundred strangers in front of you, so it filled in a couple of sessions and then threw somebody out every time anybody new walked past. It holds 10,000 now - about a megabyte at worst.

**Your own stats show the total again**
- Your card fell back to listing only what your equipment adds, because a row built from hit records carries no stats - on the one card where the total is certainly known. The last live reading fills it in, so the figure leads and the equipment's part sits beside it in brackets.

**Smaller things**
- A **share** button on the Damage panel itself, where you are when you decide to post the board, rather than two pages away.
- Boss segment blocks no longer run off the right edge. Each block had a flat 54px floor so a one-second segment still had room for its name, which meant enough short segments pushed the row past the panel.
- The left rail's tooltips are gone. Each one repeated the word already written under the cursor and covered the item below it.

---

188 self-test assertions pass. Update from inside the app with **Check for updates**, or download the zip below.

## 1.64.0

**It is called Sonar now**
- The name and the icon, and nothing else. The palette, the layout, the wording and every page are exactly as they were - "Sniffer live" still says sniffer, and the accent colour is still called Peach, because that is a colour rather than the app.
- The mark is a listening point with arcs arriving at it rather than radiating from it. Passive sonar never transmits; the thing that pings is the thing that gets found, which is a fair description of a tool that reads the wire and injects nothing. Below 32px it drops to two heavier arcs, because three thin ones turn to mush and the faintest disappears on a dark taskbar.

**What deliberately did not change**
- Every `peach.*` setting key, so your saved layout, wardrobe and overlay calibration survive the upgrade untouched.
- The install folder, `Peach.jar`, `Peach.exe` and the release file names, so updating from inside the app still works and this release installs over the last one.
- The repository and the update feed.

---

184 self-test assertions pass. Update from inside the app with **Check for updates**, or download the zip below.

## 1.63.1

**"jv" counts as a dungeon call**
- The join-call ping only recognised messages made of `j`, and the vial call is `jv`, so those went unheard while waiting and - worse - were not muted on the way into a dungeon either. A `v` now counts alongside a `j`, so `jv`, `JV!` and `jjv` all ping and all fall silent inside a dungeon like the plain call does.
- A `v` on its own is still not a call, and words that merely contain those letters - `jive`, `vault` - never were.

---

184 self-test assertions pass. Update from inside Peach with **Check for updates**, or download the zip below.

## 1.63.0

**The Shatters overlay starts over the minimap**
- It opened at 100,100 - the top left, which is the one corner of the screen the game never puts the minimap in. So the first thing anyone saw was a circle at the opposite side of the screen from the thing it is meant to cover. It now opens in the top right at about a third of the screen's height, square, which is roughly where and what size the minimap is. The same position is used when a saved one turns out to be off-screen.
- **Recentre** sits beside Calibrate. A position saved before this is still saved, so a better default would never have reached anyone already using the overlay; this puts it back over the minimap, resets the scale and drops straight into calibration.

---

182 self-test assertions pass. Update from inside Peach with **Check for updates**, or download the zip below.

## 1.62.0

**One header, not two**
- Peach sat under a grey system title bar carrying the name and buttons a second time, in a palette that matched nothing below it. The window is borderless now and carries its own minimise, maximise and close, so there is one header and it is Peach's. Dragging the header moves the window, double-clicking it maximises, and every edge and corner resizes. `-Dpeach.borderless=false` puts the system bar back.
- The buttons along the header lost their outlines. Nothing is drawn behind them until the pointer is on one, so a row of them reads as a row of words rather than a row of boxes.

**A "Near me" tab on the Players list**
- A max-pop realm lists well over a hundred names, which is no use when what you want is who is actually with you. The tab cuts the list to everyone within 22 tiles - about a screen - and the count reads "12 within 22 tiles" instead of the usual seen-and-dealt line.
- Someone whose position has not arrived is not known to be near, so they drop out rather than being assumed close.

**The shared image's headline counts what is on the page**
- A boss page reported the whole run - every scrap of trash between the fights - so a Plagued Nest showing 3.1M against the Killer Bee Queen was headed **9,041,448**. It now reads **BOSS DAMAGE** and sums only the fights on the page.

---

180 self-test assertions pass. Update from inside Peach with **Check for updates**, or download the zip below.

## 1.61.0

**The marks are the game's own sprites**
- The Void Phantasm is drawn as the Void Phantasm, the Stone Idol as the Stone Idol, and so on, cut from the game's own sheets at the frame each one stands still on. The coloured ring stays around it, carrying how much the thing matters; the sprite inside says what it is, so there is no symbol legend to learn.
- The Derelict Monument needed a second route to its texture - the id table has no entry for it, so the Shatters data is read directly for anything the table cannot answer.

**Objects the game draws almost black fall back to a mark**
- The nine void cracks are cracks in a floor, drawn nearly black on purpose, and on a dark minimap they came out as nothing at all. Rather than keep a hand-written list of which sprites are too dark - which would go stale the first time one changed - the pixels are counted, and a sprite needs a twentieth of itself to be solid and brighter than the dark around it. The cracks measure **0-1%** by that rule and everything else **20-45%**, so it separates them without a judgement call. Anything that fails it keeps its drawn diamond.

---

175 self-test assertions pass. Update from inside Peach with **Check for updates**, or download the zip below.

## 1.60.0

**The Void Phantasm is on the minimap**
- It sits in the first room, and the game flags it `NoMiniMap` - it is deliberately hidden, which is the whole reason finding it is the puzzle. Peach already had it in the entity list; only the minimap had been told not to draw it. It is now the largest mark on the overlay, a coral ringed cross, and it leads the sidebar.
- **Eighteen Shatters objects are marked**, not three: the Phantasm, the Stone Idol, the Derelict Monument and the Source as full-strength objectives; the nine void cracks, the void crystal and the Void Tendril as faint violet diamonds; and the three invisible logic objects as bare ticks, in case they ever reach the client at all.
- The Source's four generators are gone. They were never what answers "where is it" - the Phantasm is.

**Every watched object is verified by the name the game actually uses**
- These are matched on the internal name, not the display name, and the difference is not cosmetic: most of them have no display name at all, and the two that do are not named what you would guess - the Derelict Monument is a "Village Switch" internally, and the Source is an "Experimental Generator". A check against display names would have found almost none of them.
- All eighteen ids are confirmed against the game's own data on startup and repaired if one has drifted, with anything that cannot be found named in the sidebar rather than silently never appearing.

---

173 self-test assertions pass. Update from inside Peach with **Check for updates**, or download the zip below.

## 1.59.0

**A sound when the realm starts shaking**
- Oryx closing the realm now rings its own ping, once per realm, so you can be looking elsewhere and still make the castle. It listens for both of the things the server sends at that moment - the close-realm timer turning on, and Oryx's own broadcast - so whichever arrives first sets it off and a change to either one does not take the feature with it. Pick the sound under the ping sounds like any other.

**The Shatters overlay can be aimed exactly**
- The overlay's centre is where you are, so lining it up is the whole of calibration, and a dot near the middle was not something you could aim with. It now draws a **gold diamond the size of your own minimap icon**: put the two on top of each other and they become one shape. Hairlines run the full width and height through it, holding shift while dragging moves an eighth as far per pixel of mouse, and the corner reads back the scale, the window size and whether your position is known yet.
- Each mark shows **how many tiles away it is** while calibrating, so it can be matched against the game's own icon for the same thing and the scale scrolled until the two sit together. Shift-scroll steps the scale finely.

**The Source's four generators were never name-checked**
- Every watched Shatters object is verified against the game's own data on startup, so an id the game quietly moves gets repaired rather than silently finding nothing. The four generators were exempt from that: they carry no display name at all, only an internal one, so the check would have called them missing every time and was skipped. They are now verified against that internal name, and the self-test asserts all four still name the corner they stand in.

**Your stats are recorded with the run again**
- Every instance saved to history had blank stats and no character on it, which is what the damage model needs to check itself against. The frozen copy a run is saved from does not always carry them; the last live reading does, and now fills in what is missing.

---

172 self-test assertions pass. Update from inside Peach with **Check for updates**, or download the zip below.

## 1.58.0

**Export said "Nothing saved yet" with months of runs saved**
- The saved instances have been gzipped since they were made to take less disk, and the export was still only looking for plain .json files. It found none, every time, however much history there was. It takes both now, the way the two readers beside it always did.

**The Enemies list leads with the biggest fight**
- It was in the order the run happened in, so a boss sat wherever it fell among the trash. Biggest first now, judged on the larger of what an enemy reported and what it was actually seen to lose - a boss that never announces a maximum, as Oryx does not, would otherwise have sorted as nothing. Two of a size keep the order they happened in.

## 1.57.0

**A full Shatters fits, all three bosses, everyone on it**
- The sixty-line backstop was still cutting people at a packed party: eighty-five players on the Bridge Sentinel came out "+ 25 more". There is no line limit now at all.
- The layout will not settle on a shape that leaves anyone off. It tries up to seven columns, and a shape that drops people is never preferred to one that does not, however square it would have been.
- A maxed Shatters - 85 on the Sentinel, 78 on the Archmage, 66 on the King, 229 lines - comes out 1658 by 1952 with every name on it and each boss carrying on under its own heading.

## 1.56.0

**Walls are not bosses**
- A Plagued Nest run came out with "EH Destructible Wall" listed among its fights. The game's own files are clear about these: a wall's class is Wall, and nothing of that class is labelled a boss anywhere in the data - so they are told apart by what the game calls them rather than by guessing from the name. Portals, pets, summons and player-placed objects go the same way.
- Statues and towers stay. Eighty-two real bosses are GameObjects rather than Characters, so that whole class could not simply be excluded.

**The shared image is not signed any more**
- The footer keeps the date and time of the run and drops the "Generated by Peach".

## 1.55.0

**The shared image stops leaving people out**
- It showed sixteen names a fight and counted the rest as "+ 7 more". Everyone who hit the boss is on the page now.
- Raising the limit alone would have made it worse: a run with one boss and two dozen players was a single block, and a block could not be split, so the page could only grow downwards into a strip. A fight too long for one column now carries on at the top of the next under its own name again, marked "continued".
- A twenty-three player Plagued Nest comes out 822 by 618 with every name on it, where before it was sixteen names and a footnote. Forty players is 822 by 850; eighty is 822 by 1140.

## 1.54.0

**Every stat was being shown as maxed**
- The Gear panel painted all eight totals gold whether they were at their ceiling or not, so a Rogue six short on speed and one short on vitality read as finished in all eight. Nothing was being compared to anything: the colour was simply always gold.
- A stat is gold now only when what it is worth on its own - the total with the equipment taken back off - has reached the class ceiling, raised by whatever that character has exalted. Everything else is plain, and carries the shortfall the way the game writes it: "72|6" is six points short.
- Checked against Username0's own panel: speed 72 with 13 from gear is 59 against a ceiling of 65, so six short; vitality 56 with 17 from gear is 39 against 40, so one short; the other six really are at their ceilings.

## 1.53.0

**Spider Den and Spectral Penitentiary were paying each other's reward**
- The per-dungeon completion counts were gathered into a list in roughly alphabetical order, and the rewards are looked up by a list in the game's own order. The two were almost the same - which is why this hid - but not quite: those two sat the wrong way round, so an easy low-level dungeon was rewarded as a hard one and the hard one as the easy one.
- The counts are gathered by name now, so they cannot drift again whatever order the fields happen to be declared in. Checked in the self-test by putting completions into one dungeon and insisting they turn up under that dungeon and nowhere else.
- The character statistics are per character, not account-wide: each one's figures are read from inside its own entry in the character list. That was worth confirming while looking at this.

## 1.52.0

**The character statistics were being read in the wrong order**
- This is the cause of the Fame page's nonsense, and of everything built on it. The statistics arrive one after another in ascending stat id, each one present only if its bit is set. Peach read them in a hand-written order that was not that order: from the ninth value on, **93 of the 123 reads took the wrong stat's number**, and every read after the first mismatch was shifted as well. That is where "677,545 dungeon completions" came from, and with it a fame on death of 333,714 for a character the game valued at 6,490.
- They are read in the order they were written now. That needs no table of offsets and cannot drift: a statistic Peach has no field for is still read and only then discarded, so the ones behind it stay aligned.
- The self-test builds a statistics blob the way the server builds one and reads it back, including a pair the old reader provably swapped - stat 8 and stat 16, which it asked for in the opposite order to the one they are sent in.

**The rarity mark counts slots, not rarity**
- It was weighing how rare an item's best enchant was. The game counts the enchants: one is green, two blue, three violet, four gold. A ring holding a single unique enchant shows one green diamond however rare that enchant is on its own, and a weapon with one Divine enchant shows one green diamond too - not four gold ones.

**The "j" ping stops once you are in the dungeon**
- A popped dungeon called out in party chat is worth hearing while you are waiting for one. Inside one it is somebody else's call, and a ping in the middle of a fight. It now only sounds in the Nexus, the realm, the vault, a guild hall, the pet yard, the daily quest room and the bazaar.

**No more crosses on the windows**
- The windows already could not move or resize; the cross was the last thing that rearranged a page. It is gone, along with the resize grip that no longer did anything, and the title bar gets the width back.

## 1.51.0

**The calculator counts your ability**
- It only ever counted the weapon, which for half the classes is most of the damage missing. Abilities are now read out of the game's own equipment data: what they fire, how hard, how many shots, how often, and what armour they pierce.
- Different abilities on the same class scale off different stats, and the game says which, one activate at a time - so a Wooden Shield reads as a 55-90 shot gaining two damage a point of wisdom past thirty-four, which is exactly what it does in game. Nothing here is scraped out of the wording of a description.
- The card shows how much of a build's damage per second is the ability rather than crediting the weapon with all of it.
- A shot that only fires while some condition holds - Lethal Strike's two extra projectiles in the moment after sneak - is a burst, not a rate. Counted at its own cooldown it came to a hundred damage a second for a whole run. Those are listed and left out of the total, marked "ability not counted", rather than given a number Peach made up.
- The damage tracker has its own reader of the same scaling data, and the two are now held against each other in the self-test, because two readers of one file that disagree would mean one of them is wrong.

## 1.50.0

**Zoom**
- Everything can be drawn larger or smaller, from 80% to 200%, in steps of ten. Hold Ctrl and scroll over any page, or use the new control in Settings. It is remembered between sessions.
- It is a real zoom rather than a bigger font: every page paints at its natural size and is scaled on the way out, so bars, portraits, gear, sprites and text all grow together and nothing has to be re-laid out. The pointer is divided back into the same space, so what you click is what you are looking at.

**The windows stay put**
- No more moving, no more resizing. The arrangement a page gives its windows is the arrangement it keeps, which is one you can learn - and a layout nudged half an inch by a stray click was never worth putting back. What used to be done by dragging a window bigger is done by the zoom, which grows every page at once.

## 1.49.0

**The game's own enchant rarity marks**
- One green diamond for Uncommon, two blue for Rare, three violet for Legendary, four gold for Divine - the game's own 16x16 sprites, in the bottom-right corner of every gear icon Peach draws, with the glow already in that rarity's colour.
- They were not in anything Peach extracts. The game keeps them in a 4096x2048 GUI atlas whose pixels live in a streamed sidecar file, and the code that would have read it was commented out, unfinished, in the library Peach inherited. The atlas also carries a table of 658 named sprites, which is where the four turned out to be: RarityIcon_1 through RarityIcon_4. They ship with Peach now rather than being hunted for at startup.
- Hovering an item still lists each enchant with its own symbol, as it did; the corner now says the rarity rather than repeating the first enchant.

## 1.48.0

**The shared image is readable now**
- It was one long column: a Shatters run came out six times taller than it was wide, a strip nobody could get through. The fights are measured first, split across however many columns bring the picture closest to square, and read down one column then the next. The same run is now about as wide as it is tall.
- Each player is one line at half the height, so a whole fight fits without the page running away: rank, how they look in game, their name, their gear and their damage with its share of the enemy's health beside it.
- Sixteen players a fight rather than twenty-four, with the rest counted underneath.

## 1.47.0

**Bosses were never sorted from minibosses at all**
- The fights are merged by name before the list is built, and the merged copies were not carrying what the game calls each one. Every anchor came out `boss = false`, so every fight of every run - the Forgotten King included - was filed under MINIBOSSES and the BOSSES heading never appeared. The label comes across now.
- An enemy Peach promoted only for having an outlying health pool is no longer called a miniboss. The game labels its staged fights BOSS and MINIBOSS; obelisks, generators, tablets and idols are labelled neither, and they now sit under their own TOUGH ENEMIES heading instead of among the real ones.

**The shared image is the dungeon's bosses, and nothing else**
- It had a section for every fight, including every generator and obelisk - a Shatters run ran to twelve sections and was unreadable. It is now only the fights the game itself stages, in the order they were fought: for the Shatters that is three, the Bridge Sentinel, the Twilight Archmage and the Forgotten King.

**The calculator reads the enchants you are wearing**
- It only ever recognised enchants that change damage per second, which is right when picking one and wrong when showing what is already on. Anything else - an armour's Defense Bonus, a ring's Life Bonus - was dropped, so a fully enchanted character came up bare. Everything worn is listed now; what it adds to the damage is still nothing unless it really does.

**Gear marks**
- The enchant mark sits in the corner of the item rather than the corner of the tile, where it was half again too big.
- The rarity glow is a halo around the item rather than a wash underneath it. Filled, its innermost ring covered the item's own plate, so a divine enchant turned the whole tile gold.

**Fame on death, honestly**
- 1.46.0 claimed to take this from the game. It does not: fame on death is not among the stats the game sends, and the stat tried read 52,909 on a character the game valued at 6,490. The claim is withdrawn. The client works the figure out itself from the same packed statistics Peach still misreads - the page says so, and getting that decoding right is the fix.

## 1.46.0

**The Exaltation page kept almost nothing**
- The account's own exaltation only arrives with a character list, and the game answers one in the Pet Yard and the Daily Quest Room and nowhere else. Peach held it in memory alone, so every session it started again from nothing and the page read "1 of 19 started" for an account with eighteen. It is written down now and put back at startup, and the page says which day the figures are from and that the Pet Yard brings them up to date.
- Progress earned in front of Peach was also being thrown away: the update packet was ignored for any class not already known, which on a session without a Pet Yard visit was every class. It is taken as it comes now.
- A character carries what exaltation *adds* to a stat, not the level, and life and mana go up five at a time. Read straight across, a Rogue with the usual +10 life showed "L5 max" at level two and added up to 7/40 when the truth was 4.

**A page could be closed and left blank**
- Every single-window page - Exaltation, Wardrobe, Vault, Characters, Collection, Party finder, Fame, Puzzle - could be emptied by one click on the window's cross, leaving a page with nothing on it and no sign of how to get it back. The cross is no longer offered where there is only one window, and any page already left blank comes back the next time Peach starts.

**Fame on death is the game's figure now**
- The game does this sum itself, bonuses and all, and sends the answer. Peach was adding the bonuses up on its own and reaching the hundreds of thousands for a character the game valued at six thousand. Wherever a character has been played in front of Peach, its own figure is what the page shows; Peach's working stays underneath as the explanation, and the two are shown side by side when they disagree.

**Enchants, everywhere gear is drawn**
- The mark in the corner of an item is now the game's own symbol for the *best* enchant on it rather than whichever happened to be listed first, and that symbol carries its rarity the way the game draws it.
- An enchanted item glows in the colour of its rarity on every page that shows gear - the damage bars, the breakdown, the gear panel and the shared image - where before only the damage bars did.
- The full list of enchants, each with its own symbol and coloured by rarity, appears on hover and only on hover.
- Rarity is read from the game's own files rather than guessed from the numeral on the end of the name, so enchants that are not numbered are no longer treated as having no rarity at all.

**Demo data**
- The demo's enchants were invented names the game has never heard of, which meant nothing to look at and nothing to check. They are the game's own now.

## 1.45.0

**The Wardrobe**
- A page of its own for how Peach pictures each class. Until now it drew whichever character of that class you last played, which is a sensible default and a poor preference - the one you happened to log in on is rarely the one you think of the class as.
- Every class down the left, as Peach now draws it. Pick one and change any part of it: its skin, its clothing, its accessory. Everything the game has is offered - every skin that class can wear, all 143 dyes and all 256 cloths for each part, patterns drawn as the game itself lays them on. Nothing is a short list of favourites.
- A look takes effect the moment it is clicked; there is nothing to apply. Hovering anything says what it is.
- **Outfits.** Save what a class has on and it becomes a chip to switch back to. Outfits are named after the skin they lead with, and a second outfit on the same skin takes the name of its colour instead, so two Bandit Rogues read "Bandit Rogue" and "Lush Camo" rather than "Bandit Rogue" and "Bandit Rogue 2".
- **Putting it all back.** Undress one class, or every class at once. Either leaves the saved outfits where they are, so nothing is lost by changing your mind.
- **One colour, every class.** Whatever the class you are on is dyed can be put on all nineteen in a click.
- None of this reaches the game. It is how Peach draws, kept in Peach's own settings; no character is touched and nothing is sent.

**Switching outfits from the Exaltation cards**
- The cards never fill the window exactly, and the column of space they leave now holds the wardrobe. Click any card to put the column on that class, then switch between its saved outfits without leaving your progress.
- The column takes whatever width the cards leave over, so it costs no card its place; on a narrow window it sits above them instead.

**The Exaltation page in the demo**
- The demo never filled it, so neither that page nor anything built against it could be looked at before shipping. It now carries one real account's own figures - eighteen of nineteen classes started, with the raw progress the game showed for each stat - and works the levels, totals and proficiency bonuses out from that the same way the live page does.

## 1.44.0

**The calculator knows the newer weapons now, for real this time**
- 1.40.0 claimed to have fixed this and had not. The game spreads its equipment over several files, and the fix taught the wrong reader about them: the calculator does not use the shared one, it reads the files itself, and it was still reading only the main one. So a Sweettreats Dagger stayed "no weapon" however plainly it was equipped, and the check written at the time passed anyway because it asked the reader that had been fixed rather than the one the page uses.
- The calculator's own reader now takes all of them. The check now asks the calculator directly, and fails if the page cannot find a weapon the game added recently - which is what should have been written the first time.

## 1.43.1

- The Party finder sorts by fullest first out of the box. A party already filling up is the one worth joining; an empty one usually means nobody else thought so either.

## 1.43.0

**Getting into a party, the honest way**
- Peach cannot join one for you. The stream from the client to the server is RC4 encrypted and stateful, the game client owns that state, and anything Peach injected into it would desync the cipher and disconnect you mid-run. Listening only is staying true.
- What it can do is take the time out of the step that actually costs it. Clicking a listing copies its name, ready to paste into the game's own filter box - the real Join button is then one click away, and the row says so rather than leaving it to be discovered.
- Parties can be starred. A watched one is kept by the name it runs under rather than its id, since an id lasts one party while the people who run the same thing every night tend to call it the same thing every night. Watched parties sort to the top however the list is otherwise ordered, are marked in gold wherever they turn up, and have a filter of their own.

## 1.42.0

**The Party finder was never going to work**
- A packet only reaches Peach once it has been asked for by name, and the two party packets never were - so the page could not have filled however long the finder was left open. Both are now asked for, and the listings land as they are browsed.

**Faces in the lists**
- The Players and Party lists draw each person as they look in game, skin and dyes, rather than leaving a name to do all the work. Recognising someone from the list is now the same as recognising them on screen.

**The enchant gems are the game's own**
- They were a shape drawn here, and they looked it - too large, and nothing like the real thing. The game keeps a picture for every one of its enchantments; Peach now cuts the right one out of that sheet and draws it at the proportion the game itself uses. 925 of them are matched. An enchant Peach does not recognise draws nothing rather than the wrong gem.

**A damage breakdown worth the name**
- The page listed six columns and little else. It now opens with the fight in figures - party damage, party dps, what it came to against the enemy's health, hits landed, damage taken and armour guarded - and gives each player a bar in their own class colour so the gaps between people are visible rather than buried in a column of numbers.
- Added per player: damage per second, biggest hit, and how much of the enemy's health they accounted for, beside what was already there.
- Clicking a line opens it out: every weapon and ability they landed a hit with, what each accounted for as a bar and a share, how many hits, and anything the server reported without naming a source. Abilities, pets and unattributed damage are each coloured apart.

**Damage taken was never reaching the boss views**
- Taken, guarded and blocked were filled in on the enemies and the whole instance, but the boss summary and the grouped fights are built from copies made before that happened, so those columns were empty there however much had been recorded. They are filled in now.

## 1.41.0

**New Party finder page**
- Every party the game's own Party Finder has shown, on one page. The game lists six at a time across a dozen pages behind a single text box, which makes comparing them impossible; here they are all together, with filters for the ones that still have room or are open to anyone, and sorting by free places, by how full they are, or by server.
- Each listing shows what the game shows - type, description, server, power level, maxed-stat requirement - plus how full it is as a bar, since "43 / 50" and "1 / 50" read very differently at a glance. Parties open to anyone are marked apart from the ones that ask first.
- The party you are actually in is named at the top, read from the game's own roster rather than kept by hand.
- Peach only ever listens, so this fills as the finder is browsed in game rather than refreshing itself. The page says so plainly rather than letting an empty list look like a fault.

**The damage breakdown moved in with the enemies**
- It had a page of its own, which meant leaving the instance to look at it. It now sits under the Enemies list on the Instance page and opens out over the whole page when the full table is wanted, then retracts to its own corner when it is not.

## 1.40.0

**The game's own enchant gems**
- Every enchanted item now carries the small coloured gem the game draws on it, in the corner, alongside the glow that was already there. The glow says an item is enchanted from across the window; the gem says how good the enchant is up close. Gear with no enchants is left plain.

**Characters on the damage bars**
- Each bar now shows the player as they look in game - their own skin and dyes - between the rank and the party button. The area that counts as a click on the party button follows the drawing, so adding someone still works where it always did.

**Recent dungeons for a player**
- The Player panel had a column of empty space and the history on disk already knew the answer; it was only ever stored the other way up, by day rather than by person. Picking a player now lists the earlier runs they turn up in, with when it was and what they did. Clicking one opens that run with them already picked out, so you land on the person rather than hunting for them.

**Items the calculator had never heard of**
- The Sweettreats Dagger, and everything the game shipped alongside it, read as "no weapon". The equipment data is spread across several files and only the main one was being read. All of them are now, so recent items work in the calculator like any other.

**Clearing saved instances**
- A button in Settings removes the instance history, damage audits and exported images. It takes two clicks, and everything goes to the Recycle Bin rather than being deleted, so a wipe made in haste can be undone from Windows. Settings, the party and saved characters are left alone.
- Saved instances are now compressed. One busy day had grown to eleven megabytes of plain text; days already on disk are squeezed on the next launch.

**The update window**
- Smaller, with properly rounded corners, and drawn in whichever theme is chosen rather than a palette fixed at build time.

## 1.39.0

**Everyone is drawn with their own skin and dyes, everywhere**
- The Players page has always drawn people correctly because its rows are built from the players themselves, which carry a skin and two dyes. Damage rows are built from hit records, which carry neither - so every other page, every export and every shared image drew blank plates. The look is now copied from the one to the other as the instance is read, so one fix covers all of them. What they are holding comes across the same way.
- Remembering how someone looked was running before that list existed, so it never learned anything and reviewed runs stayed blank. It now runs after, and recognises people across runs as intended.

**A five-health pirate no longer claims to have taken 5,660% of its health**
- Some enemies report a health pool far smaller than the damage they visibly absorb. Rather than print a figure that cannot be true, the share is left off that fight.

## 1.38.0

**The update loop, and why it happened**
- 1.36.0 would not update to 1.37.0: it offered the update, installed it, and offered it again forever. The version is compiled into the app, and the 1.37.0 download was packaged from a build made moments before the version was raised - so the zip said 1.37.0 and the app inside it said 1.36.0. The updater saw a mismatch every time and asked again.
- The release tool now reads the version out of the built app and refuses to package when it disagrees with the release being made. This could not have reached anyone if that check had existed; it does now.

**Updating looks like part of Peach**
- The grey Windows panel that appeared while the files were swapped is gone, replaced by one in Peach's own colours with no system chrome. It said very little and looked like something had gone wrong.
- The update prompt itself is now a Peach dialog rather than a system one, with the same buttons used everywhere else in the app.

**The shared image**
- Class names and percentages were too small and too dim to read against a bright bar; both are heavier now.
- How each player looks is filled in from what Peach remembers of them before the image is drawn, so figures are no longer blank in a shared picture or a run being reviewed.

## 1.37.0

**Bosses that never announced their health are found anyway**
- Oryx's Sanctuary was reporting no bosses at all. A fight earns its own entry either from the game's own label or from having an outsized health pool, and the second test was reading only the maximum the enemy *reports*. Oryx never reports one, so a boss worth nearly two million health was read as an ordinary enemy. The health an enemy was actually seen to lose counts now, which is the figure that always exists.
- A chest is no longer mistaken for a fight. "O3 Potion Chest" was turning up in the list of bosses on a technicality; anything the game labels a container is left out.

**Looking back at a finished run fills itself in again**
- A finished instance is kept whole, and what comes back from disk was not what went in: which enemies counted as fights, and how they grouped, did not survive. A run being reviewed would show no bosses however plainly it had fought one. It now works them out again from the enemies themselves, which are the part that does survive - so older recordings come back right too.
- How each player looked is remembered between runs and filled back in. The game only describes someone's skin and dyes while they are in sight, so reviewed runs and shared images were drawing blank figures; the same people are now recognisable across the runs they appear in.

**Getting around**
- The two buttons on the side of a mouse now step back and forward through the pages you have visited, the way they do in a browser.
- The instance arrows work on every page, so every page now shows which finished run it is on. Stepping through them on the Analysis page used to look like it was doing nothing at all.

## 1.36.0

**Names stand out, and everyone is pictured**
- Names were white on every bar, which reads well on a Necromancer's deep violet and badly on a Paladin's yellow. The colour underneath now decides: near-black ink on a bright bar, white on a dim one, each with a matching halo, so a name reads the same whatever class it belongs to.
- Every line now carries the player as they look in game - their own skin and dyes - and the four items they were holding.
- Merged rows were losing their gear: the copy was keyed on the item's name, and a row built from ids alone kept none of it. It now keeps anything with either.

**The shared image reads like the damage board, not a receipt**
- Every player on a boss now gets a bar in their own class colour, as long as their share of the best damage in that fight. A column of numbers hides the gaps between people; a row of bars does not.
- Everyone who hit the fight is listed rather than the top eight, and each line says what their damage came to against that enemy's own health.
- Lesser bosses are included alongside the headline ones, and each fight carries the adds that spawned during it rather than scattering them across the page.
- A run with damage on record can no longer produce a page saying nothing happened. When the game gives no boss label to anything in a dungeon - or when looking back at a run recorded by an older build - the biggest fights are shown instead, and the page says plainly that is what it is doing.

## 1.35.0

**The Enemies list reads like the run**
- Every enemy now carries the game's own picture of itself, so a boss is recognisable at a glance instead of being a line of text.
- The list is ordered by progression - the order the fights actually happened in - with the bigger fight first when two began together. It used to be ordered by whichever was hit most recently, which told you nothing about how the run went.

**Every boss said "Stage 0 of 0"**
- A real bug, and a plain one: fights that are really one encounter get folded into a single entry, and that folding produces merged copies rather than the enemies themselves. The label was looking each enemy up in that folded list by identity, which could never find it, so every boss in every run read stage zero.
- Each fight now carries its own number, written onto the enemy itself when the folding happens, so the label cannot drift from the list again. Three self-test assertions hold it there.

## 1.34.1

**The demo looks like a real run now**
- Demo mode was showing a twelve-player party doing a hundred thousand damage each against a boss with a tenth of its real health, with the damage taken, blocked and guarded columns empty and most gear missing. Nothing about it resembled a run, which defeats the point of a harness meant for seeing the interface as it will really look. It is now built from real figures: twenty players, a top board of 1.1 million damage, hit counts in the thousands for bow users and the hundreds for casters, Oryx's real ten-million health pool, and damage taken and guarded on every line.
- Every demo player's gear is now chosen out of the game's own equipment data - the highest-tier item their class is actually allowed to hold in each slot - instead of being written down by hand. The hand-written version was wrong on its first outing, handing a Samurai a dagger; the check that caught it is the same one that now picks the kit.
- Waves are numbered by which gap they are rather than which gap they could have been, so a run where nothing died between two bosses no longer leaves a hole in the numbering.
- The list of fights is a little wider, so a long boss name is not cut short.

## 1.34.0

**Pick the fight you want to look at**
- The Damage window now has the run itself down its left side: the whole instance, then every boss, every lesser boss, and every wave of ordinary enemies in between - each with the enemy's own picture beside it and its damage underneath. Clicking one is what the bars show. The Overall / Enemy / Bosses switch still works exactly as before; the list only takes over once a particular fight is picked.
- A boss is rarely alone. Adds spawn during its phases and die inside its own fight, and counting those as separate enemies scattered one fight across a dozen entries. An enemy whose fight overlaps a boss's is now folded into that boss, and only what is left - the enemies killed in the gaps - becomes a wave. The list reads the way a run actually went: wave, boss, wave, boss.
- Lesser bosses are kept apart from headline ones, using the game's own MINIBOSS label rather than a guess. Anything Peach promoted only for having an outsized health pool is counted as a lesser boss too, since calling an unlabelled tanky enemy a boss outright would overstate it.

**Reviewing a finished instance no longer strands you**
- Opening one used to throw you onto the Live page showing old numbers with no obvious way back. There is now a Live / Review switch on the Instance page, and opening a finished instance keeps you where you are and turns Review on. Whichever run was last opened is held, so moving between then and now is one click either way instead of hunting through the list again.

**Sharing to Discord**
- Both share buttons now carry the arrow that leaves - up out of a tray and away to the right - so it is obvious at a glance that the instance in front of you can be sent somewhere. Share by boss is the prominent one, since that is the one worth posting: it covers every boss and lesser boss with its own picture and its own ranking, and nothing else.

## 1.33.0

**New Breakdown page**
- Everyone in the instance on one line each, the way a damage table should read: rank, who they are, raw damage, what that came to against the enemy's health, hits landed, damage taken, damage blocked, what their armour guarded, their gear, and how the figure was arrived at. It follows whatever the Damage window is set to, so the two always agree.
- Damage taken, guarded and blocked are new throughout. Taken is the server's own figure for every player. Guarded - what armour absorbed - is worked out by holding what landed against what the shot was worth before defence, which the game only reveals in the enemy's own projectile; where a shot went unseen the figure is a floor rather than exact. Blocked is the user's alone, because the server never announces a hit on someone else that dealt nothing - for everyone else it stays a dash rather than being reported as zero.
- Columns give way one at a time as the window narrows, widest first, so the table stays readable at any size instead of overlapping itself. Anyone who left before the dungeon was finished is dimmed, the same rule the leaderboard uses.
- Another player's damage now reads "Observed" rather than "Server-checked": it is the server's own figure reported as it happened, and only the user's own is worked out by the client first and then held against the server.

**Gear notes on the damage bars actually appear**
- Hovering a weapon, ability, armor or ring beside a player's name now shows a note, which it never did before. The look and feel Peach is built on quietly replaces every tooltip with its own, and that one never came up over the custom painted pages. Peach now owns the note outright: it decides when it opens, where it sits and what it looks like, so nothing outside Peach can take it away. It also keeps to whichever screen the pointer is on rather than being pushed onto another.
- The note says what the item is and what it does, not just its name: its tier or whether it is untiered or set, the damage its shot deals and how many it fires, its rate of fire when that is not the usual one, what it costs in mana, and anything it adds to the wearer's stats. All of it read from the game's own data rather than written down by hand. Underneath, each enchant on the item with the game's wording for what that enchant changes.

**Stats no longer claim to be maxed when they are not**
- A character wearing anything at all read as maxed in every stat, because what the game sends already includes what equipment is lending. Peach was comparing that against the class base maximum, so a Rogue at 959 life showed "959/750" and "8/8 maxed". Maxing is now judged on the stat itself, with what the gear adds shown separately in green beside it, so the two add up to the figure the game shows.
- Exaltation raises the ceiling as well as the stat, which Peach was not allowing for either: an exalted character could pass the class base without having maxed anything. Each stat is now measured against the ceiling that character actually has.
- The fame page counted those same stats as maxed, and no longer does.

**Hover a stat to see what is making it up**
- On the Characters page, hovering a stat that something is adding to now opens a note listing each worn item that lends it, with its picture and how much it gives, then exaltation on its own line. What the items account for is read from the game's own data; whatever is left over is named as enchants and other sources rather than being quietly folded in or guessed at.

**The fame page says when not to trust it**
- The reward table behind it is verified, but the counts fed into it are read out of a packed run of bits whose layout Peach has not got right yet - some come out far too large. Rather than let a confident looking total stand in for a wrong one, the page now says so plainly and prints the count behind every line, so each can be checked against the game's own Fame Overview. The totals will be presented as fact again once the counts are right.

**Collection counts against one vault at a time**
- Seasonal and regular are kept apart by the game, and a seasonal character cannot reach what a regular one has stored, so counting both together matched neither. The Collection page now has a seasonal and regular switch, seasonal first, and says which vault it is still waiting on rather than showing an empty list with no reason.

## 1.32.0

**New Fame page**
- What each character would be worth if it died now, and where every point comes from. Base fame and bonuses side by side, with each bonus listed largest first so what is worth chasing is at the top. Percentage bonuses from maxed stats are marked as such, since they grow with the character rather than being a fixed amount.
- The whole table is there: cartography, quests, combat, party level-ups, potions, ability uses and teleports; monster, god, lesser god, Oryx, encounter, hero and cube kills; all eight creature types; biome kills; dungeon completions across 69 dungeons; and the eight maxed stats. Each tier pays as it is passed, the top tier pays again per further multiple up to twenty times, and each percentage is rounded up on its own line - the way the game's own Fame Overview adds up.
- Six dungeons have no published reward (the legacy Heroic pair, Trials of Cronus and the three White Snake Invasions). Rather than quietly leaving them out, a character that has finished any of them is marked "at least", so the figure is honestly a floor instead of passing as exact.
- The biome kill tiers follow what the game actually pays, not what it should: the live game completes each even biome tier one threshold early, a known fault on Deca's side. Since this is meant to predict real fame, it mirrors the real payout.

**The puzzle fills its window**
- The grid now grows to fit the page instead of sitting small in a corner, and the heading no longer collides with the label beside it. The grid is always drawn, dimmed, even before you reach the Observatory - so the page looks the same whether the towers are being read from the game or clicked in by hand, and there is something to click straight away.

## 1.31.0

**New Collection page**
- What every dungeon can drop, against what you already hold. Items you own are lit and outlined, the rest sit dimmed, and each dungeon shows how far along it is. Dungeons closest to finished come first, so the next run worth doing is at the top; click one to see its items. Counted from both vaults, the gift chest, potion storage and every character's inventory and worn gear - the same tally the Daily Quests page marks requirements off against, so opening your vault fills this in too.
- 80 dungeons and 944 items, drawn with the game's own pictures.

**Fungal Cavern knows its final boss**
- Overall DPS now stops at the Crystal Worm Mother in Fungal Cavern, the way it already did for The Forgotten King in The Shatters, so time spent after the dungeon is finished no longer waters the figure down. Confirmed against the game's own data, where she is the only entity in the dungeon carrying the BOSS label - the Crystal Worm Father and the other four Crystal Worm Mother entities are all marked BOSS_SPAWNED instead.

## 1.30.0

**Boss phases are gathered into one fight**
- A big fight is rarely one enemy: a boss that changes phase swaps itself for another entity, and a pack of the same enemy arrives as several. The Tesseract Goddess alone is twenty-four entities in the game's own data, which is how one fight came out as "15 bosses". Segments with the same name are now shown as one, marked with how many they stand for - "Tesseract Goddess ×3", with a "3 PHASES GROUPED" badge on the card. Every number is carried over: the damage, the time and each player's share all add up exactly as before.

**The puzzle can be entered by hand**
- Press Enter by hand on the Puzzle page to clear the grid, click the towers that are lit, and press Solve. For when the towers are not being read from the game for any reason, so the solver is never useless - only slower to set up. Live hands it back to the game.

## 1.29.0

**The Shatters Observatory puzzle solves itself**
- A new Puzzle page. The Observatory puzzle is Lights Out: nine towers, shooting one flips it and the towers above, below and either side of it, and the room opens once all nine are lit. Peach already receives each tower's state and position, so unlike the solvers on the web there is nothing to type in - walk in and the answer is already there, drawn with the game's own tower sprites so the grid reads the same as the room. The ones to shoot are ringed and crossed.
- The answer given is always the shortest one. Every one of the 512 possible boards was checked: each is solvable, each answer lights the whole grid, and each uses the fewest shots possible.

## 1.28.1

**Fixed every class reading 0/40 while the game showed exalt bonuses**
- Exaltation was read only from the account's own list of progress, and that list only arrives in the Pet Yard and the Daily Quest Room. Anywhere else it was simply missing, so the page showed nothing exalted even on a character the game was plainly showing a bonus for. The levels are also sent with the character you are playing, so they are taken from there when the account list is not loaded.
- Each stat now shows what exaltation actually adds to it, in the same blue the game and the Gear window use: five life or mana per level, one of anything else.
- When the levels came from your character rather than the account list, the card says so, and progress towards the next level is left blank rather than shown as zero - it genuinely is not known until the list loads.

## 1.28.0

**Exaltation, rebuilt**
- One compact card per class instead of a tall block of text, each led by how your own character of that class looks, skin and dyes included. The eight stats sit as tiles the way the game lays them out - a small label over the exalt level - gold while climbing, green once that stat is fully exalted, with a hairline showing how far into the current level it is. A fully exalted class is ringed in gold.
- Classes you have never touched are folded away by default; one click shows every class.

**Shatters finder: the Source is found properly now**
- It was watching the object that stands in the Source room, which you only meet once you have already found it. The game marks the real quadrant with its own Source Generator and the other three with a Dormant Generator, so those four are what it watches now - and each says which corner it is.
- Objectives are listed with a direction and distance from where you stand ("84 tiles NE"), which needs no overlay and cannot be misaligned.

**Shatters overlay lines up properly**
- The overlay spread the whole map flat across its window, but the game's minimap is a circle centred on you that you can zoom. Those two could never match, whatever you did with calibration. Marks are now placed relative to where you stand, the way the minimap does it, and anything off the edge is pinned to the rim pointing the way. Calibration now sets the zoom too: drag it over the minimap and scroll to match. You will need to calibrate once more after this update.

**The watched objects check themselves**
- A hardcoded id the game quietly moves is how the vault stopped loading - nothing errored, it just never found anything again. The three Shatters objectives are now verified against the game's own data at startup and looked up by name if one has drifted, and the window says so if one cannot be found at all.

## 1.27.1

**Boss health is measured by what the fight actually cost, not by one reported number**
- A boss's "max health" is only true for the instant it is read. Big fights are built from many entities (the Tesseract Goddess is 24 of them in the game's own data, seven of which never report dying; Magi Conductor is 40; Crystal Entity is 21), several of them refill between phases, and the game rescales health as players come and go. Comparing a whole fight's damage against that single figure is what made damage look larger than the boss's health.
- "Of boss health" is now measured against the health each boss was actually seen to lose over the fight, falling back to the reported maximum when its health was never seen. A boss's health pool is also taken at its peak rather than whatever it had left at the end.
- The Current Fight window now shows "Health lost" when a boss loses more than its stated maximum, and "Healed in fight" when it regains any - so a phase refill is visible rather than showing up as damage that does not add up.

## 1.27.0

**Daily Quests knows what you already have**
- Every item a quest asks for is now marked with how many you hold, counted across both vaults, the gift chest, potion storage, and every character's inventory and worn gear. Requirements you have are outlined in green, and each quest says either how many of its items you have collected or that it is ready to hand in.

**Characters, led by the character**
- Each character is now shown as it looks in game, skin and dyes included, rather than as a block of text. Clicking one opens it: what it wears, what it carries, and all eight stats against the class maximum. The exaltation bonus sits to the right of each stat in blue, so it reads apart from the base number.
- Exalt bonuses are only shown for a character you have played since Peach started - the account-wide character list carries progress towards the next exalt, not the bonus itself, so showing it from there would have been a different number wearing the same label.

**A theme for the whole interface, not just the accent**
- Six base palettes beside the accent colours: Plum, Obsidian, Espresso, Midnight, Forest and Wine. Each brings its own text tones rather than reusing one grey on a different ground.

**A volume for every ping**
- Each loot and chat ping has its own volume beside its switch, so a white bag can be loud while the rest stay quiet. Letting go of a slider plays that sound at the level just set.

**A ping for "j"**
- "j" in party chat is how a popped dungeon gets called out, so it has its own ping, separate from ordinary party chat. It fires on a bare "j", "jj" or "j!" and stays quiet for anything else, so "just wait" will not set it off.

## 1.26.0

**Sound settings you can actually reach**
- Peach has had a full set of sound settings since it was forked - volume, a white bag ping, pings for every other bag colour, chat and trade pings - but they lived on a menu bar that was never attached to the window, so there was no way to open them. They now have their own Settings page, reachable from the button in the top bar or the rail. Switching a ping on plays it, so it is obvious which is which, and there is a Test button for the volume. Your existing settings are kept: these are the same saved values the old menu used.

**Accent colour**
- Six accents to choose from on the Settings page - Peach, Coral, Mint, Violet, Gold and Ice. The whole interface takes the new colour at once, and the choice is remembered.

**Daily Quests, rebuilt in Peach's own style**
- The quest list was still the plain grey panel carried over from Tomato. It is now a page of cards matching the rest of Peach: each quest's name, what it asks for and what it pays out shown as item pictures, with badges for repeatable and completed quests.

**The hover note over an item is themed, and shows the item**
- Resting on a gear icon used to open the system's grey tooltip. It is now painted in Peach's colours, leads with the item's own picture and name, and colours each enchant by its level.

**Fits a 1080p screen**
- The page could not shrink below a size that did not fit a 1080p screen with display scaling turned up, which forced scrollbars and squeezed the windows. The minimum is smaller now, the window can be sized down further, and the left rail narrows on its own when the window is narrow.

## 1.25.0

**Shiny items (and newly added items) now show their picture**
- Item pictures were looked up by name, and a Shiny carries the same display name as the plain version of the item - so only one of the two could own that name and the other came out blank. The same went for any item whose name Peach had not registered yet. Pictures are now looked up by the item's own id wherever it is known, in the vault, on the damage bars and on the Characters page. The sprites were never missing: Shinies live on the game's `lofiObjShiny` sheet, which was extracted correctly the whole time.

**The vault is grouped by kind**
- Vault contents now sit under headings - Weapons, Abilities, Armor, Rings, Consumables, Other - instead of one long alphabetical run, so a full vault can actually be read.

## 1.24.1

**Fixed the Vault page never loading - the real cause this time**
- The game sends a short after the potion limits, just before the enchant data, that Peach's packet reader did not know about. Two bytes behind from that point on, the three enchant strings at the end came out as nonsense and the reader ran off the end of the packet, so the whole vault update was thrown away every single time - the page could never have filled in, on any character. The reader now accounts for that field. Credit to RealmHound's packet implementation, which documents this field and is what the fix was checked against.
- This is the actual cause. The two earlier attempts (1.22.1 and 1.23.0) fixed real but secondary problems further down the line, which is why neither of them made any difference.

## 1.24.0

**Gear on the damage bars**
- Each player's weapon, ability, armor and ring now sit as icons to the left of their name, each behind a glow in the colour of the rarest enchant on it - so you can read a party's kit at a glance without opening anyone. Slots the game has no picture for are left out rather than shown as an empty box, and the icons step aside automatically when a bar is too narrow to fit them and the name.

**Hover an item to see its enchants**
- Resting the pointer on one of those icons opens a small note listing the item, every enchant on it, and what that enchant actually does in the game's own words. It opens almost immediately and stays up long enough to read.

**Daily Quests is its own tab, and More is gone**
- Daily Quests now has its own entry in the left rail instead of being buried inside More, and the More tab has been removed. The sections that lived in it - Chat, Key-pops, Security, the old Characters/Exalts panel, Statistics and My Info - are no longer shown. Their code is untouched and still running, so any of them can come back as its own tab on request.

## 1.23.0

**Characters now fill in as you play, instead of needing a special room**
- The game only answers a full character-list request in the Pet Yard and the Daily Quest Room, so the Characters page had no way to learn about your characters anywhere else - it just sat empty. Peach now records the character you're playing from the data already arriving each tick, and remembers it on disk, so characters build up as you play them and are still there after a restart. Visiting the Pet Yard or Daily Quest Room still fills in every character at once, including ones you haven't played recently.

**Fixed a seasonal character's vault never showing**
- Your vault's contents arrive as the map loads, often a moment before Peach has worked out whether you're on a seasonal character. The contents were filed under one of the two vaults the game keeps (regular and seasonal) based on what was known at that instant, but read back based on what was known later - and when those disagreed, which they reliably did on a seasonal character, the page showed "Vault not loaded" while the data sat in the other vault the whole time. Peach now reads from whichever vault actually received contents.

## 1.22.1

**Fixed Characters, Exaltation and Vault all sitting empty**
- These three pages show account-wide information, but they were only ever filled in while a tracked instance was loaded - so they stayed blank in the Nexus and in the vault itself, which is exactly where you'd open them. They now fill in regardless of whether you're in an instance.
- Second, separate cause for the Vault: your vault's contents arrive from the game right as the map loads, often a moment before Peach has registered your character. When that happened the entire vault update was thrown away and the page stayed on "Vault not loaded" for the rest of the session. Vault contents are no longer discarded over that timing.

## 1.22.0

**New Exaltation page**
- Account-wide exaltation progress, one card per class: all 8 stats' raw progress and exalt level, plus the four proficiency bonuses those levels grant - Fast Learner (bonus XP), Mastery (bonus damage), Armor Proficiency (shorter in-combat lockout) and Weapon Proficiency (bonus drop rate). A proficiency shared across classes (Armor and Weapon) stays at 0 until every class sharing it has progress, same as in-game.

## 1.21.0

**New Characters page**
- Every character on the account, in one place: class, level, fame, seasonal badge, equipped gear, and all 8 stats against that class's maximum (highlighted once maxed), plus your pet if you have one. This data was already being received from the game each time you pick a character - it just wasn't shown anywhere until now.

## 1.20.5

**Stopped guessing unidentified damage as "Pet"**
- Damage the server reported under your name with no identifiable shot behind it was being assumed to be your pet's - a guess that could misfire (most often on a bulletId lookup miss) and credit your pet with damage it never dealt. It's no longer a guess: that damage now shows as "Unknown" in your item breakdown instead of "Pet". Your pet's real, confirmed damage (tracked separately, from the actual pet entity) is unaffected.

## 1.20.4

**Fixed today's instances disappearing from the list after every restart**
- Reopening Peach (including right after an update installs) showed an empty Instances list even though nothing had actually been deleted - today's finished instances were safely saved to disk the whole time, but the list on screen was only ever built from what finished during the current run, never reloaded from the file. Today's saved instances are now loaded back in on startup, so they stay visible across restarts and updates the same way past days already did.

**Fixed the Vault page losing track of which vault it's showing**
- The write side (storing an incoming vault update) and the read side (deciding which vault to display) each independently guessed whether the character was seasonal, at different moments - if that guess changed between the two, vault data that had genuinely arrived could still show as not loaded. Both sides now use the same, more reliable source (the character list, sent once at character select) instead of each inferring it fresh from a stat that can lag.

**Widened the gap between Boss Segments and Leaderboard on the Analysis page**
- The two columns sat close enough that a dragged or saved window position could overlap them. Existing custom positions are reset to the (now wider) default on this update - if you'd rearranged this page, you can get back to your own layout with the "Reset layout" button, which only affects the page you're on.

**Fixed your own damage under-counting against a target that regens mid-fight**
- Fighting alone against something that heals partway through (a training dummy, say) could under-report your own confirmed damage - any health it regenerated was silently swallowed instead of credited back, even though the same fight-in-a-group formula already did this correctly. Solo damage checks now credit regen back the same way group ones do. A real, confirmed gap this closes - not a complete fix for every possible timing edge case (a hit whose own health-drop was never sampled before a session ends still can't be recovered after the fact), but a real, proven improvement either way.

## 1.20.2

**Fixed the Vault page getting stuck on "Vault not loaded"**
- Opening a vault chest in-game could silently fail to register with Peach at all - the Vault page stayed on "Vault not loaded" even with the vault genuinely open and full, seen most reliably on seasonal characters. Cause: checking whether a character was seasonal crashed outright if that specific detail hadn't been tracked yet for them, which silently dropped the whole vault update rather than just defaulting sensibly. Fixed to default to the regular vault instead of crashing when that's unknown - open your vault and it loads now.

## 1.20.1

**No more surprise "Realm Shark Asset Extractor" popup**
- When the game updates, Peach used to hand off to a raw, unbranded "Realm Shark Asset Extractor" window asking whether to re-extract item/enemy data - it could appear over the game mid-session, looking like it belonged to a different, unrelated program. Extraction now just happens silently in the background instead (it's fast and purely local - there was never really a meaningful choice to ask about). No visible change when everything's already up to date; a brief one-time pause the first time after a game patch, no popup.

## 1.20.0

**A new Analysis page, so the Instance page stops overlapping itself**
- The Instance page had grown to seven floating windows sharing one grid over several rounds of features (Boss segments, Leaderboard, the Shatters radar beta, Share results/by boss, the new Instances export) - on some screens they visibly overlapped. Boss segments, Leaderboard and Shatters (beta) now live on their own new "Analysis" page, with its own rail icon; Instance keeps the at-a-glance basics (Instance, Your stats, Enemies, Instances) in a wider, cleaner three-column layout.
- This resets everyone's saved window positions once (the same built-in mechanism that has handled every past layout change) - nothing is lost, windows just reappear at the new default spots the first time you open this version. Verified this actually happens automatically, with no "Reset layout" click needed, by simulating an old saved layout against the new build before shipping.

## 1.19.0

**Export instance history**
- A new "Export" button on the Instances window zips every saved day of instance history into one file under `run/exports`, and opens it in Explorer. Exports exactly what's already saved locally for the Instances page itself to work - nothing new is collected, nothing is sent anywhere automatically. For testers handing data back for accuracy calibration work.

## 1.18.1

**Clearer error for the most common install mistake**
- Setup used to fail with a confusing raw git error ("fatal: not a git repository", "[FAIL] The library branch was not found") when run from GitHub's "Download ZIP" of the source code - that download has no git history to build from, which looks nothing like a real problem with your machine. Setup now recognizes this specific situation immediately and says so directly, with a link straight to the ready-to-run release zip instead - no need to wade through the prerequisite checks first just to hit a wall at the build step.
- The README's download link is now the first thing on the page, and says plainly not to use the "Code" button.

## 1.18.0

**Share results, broken out by boss**
- A new "Share by boss" button sits next to "Share results" on the Instance window - the same results, but as one section per boss reached instead of one flat roster. Each section shows that boss's own in-game sprite, its own "FINAL BOSS" tag where it applies, its own damage total, and its own ranked list with the same PUMPER/LEECHER tags as everywhere else. "Share results" is unchanged and still the quicker, single-list overall summary.

## 1.17.0

**Pumpers, average, leechers - and a clean leaderboard**
- The Leaderboard window now has two tabs. "Leaderboard" is just Top 5 Contributors, overall and bosses, raw damage - nothing else mixed in, no confusion with anything else. "Classification" is the deeper dive: everyone's sorted into Pumper (near or past their own estimated ceiling), Leecher (well-geared, well short of it) or Average (everyone else, including anyone too lightly geared to call a leecher off of) - one pair of Pumpers/Leechers sections per boss, plus a combined Overall Dungeon pair.
- Dropped the percentage display entirely - "could 100% · did 34%" reads as a share of the boss's own health, which isn't what was being measured. It's a flat number now: did 34,500 of an estimated ~182,000. The shared results image got the same treatment - PUMPER/LEECHER are plain tags now, not percentages, with the comparison explained once in the footer instead.
- Being a leecher now specifically requires having the gear to do better - someone in modest gear doing a modest share of their own (already modest) ceiling reads as Average or a newer player, not a Leecher. Pumper has no such gear requirement: pushing hard is pushing hard, in any set.

## 1.16.0

**Leeching, scoped per boss - and weighted by what actually happened in the fight**
- Top 5 Leechers is no longer one list for the whole instance - it's now one list per boss segment reached, plus a combined "Overall dungeon" list. Someone who coasts through an early fight but genuinely tries on the final boss shows up on that early boss's own list, but their real effort elsewhere keeps them off the overall one; someone who coasts through every fight lands on all of them. The Leaderboard window and the shared results image both show the per-boss breakdown now, not just one lumped total per player.
- The ceiling estimate behind all of this is sharper: a player's own weapon-swap history against a given boss is now tracked and weighted by how long each weapon was actually held, so swapping to a closer-range, higher-damage weapon for a chest phase raises their ceiling instead of being erased by whichever weapon happened to be equipped last. The time-weighted share of a fight spent under a damage-relevant buff or debuff - Weak, Damaging, Berserk, Dazed on a player; Curse, Exposed, Armor Broken, Armored on a boss - now nudges the estimate too, using the game's own condition flags rather than assuming a flat, idealized fight. Still an estimate, not exact - it can't know reaction time or how safe a moment was to push - but closer than before.
- The shared results image shows each player's damage broken down per boss on a second line, with whichever boss(es) they fell short on called out in coral, alongside the existing overall total and tag. Still anonymous by design - no row, including the sharer's own, is ever marked apart from the rest.

## 1.15.0

**Top 5 Leechers, redefined: potential vs. actual**
- Leeching isn't about raw damage being low - someone in weak, starting gear doing modest damage is probably already at their own ceiling, not leeching. Top 5 Leechers now estimates each completing player's realistic max DPS with the gear they're actually wearing, using the same formula as the Calculator page, and ranks by how far short of that they fell on the final boss. A player in a best-in-slot set who only tapped the boss for the soulbound minimum is flagged with a large gap; a player in a budget set who did close to everything they were capable of is not, even with lower raw damage.
- Any stretch the boss spent invulnerable (the game's own condition flags, not a guess from timing gaps) is subtracted from everyone's potential window alike, so a phase nobody could damage at all is never held against anyone.
- Falls back to the previous lowest-total-damage ranking whenever there isn't enough to estimate from - an unmapped dungeon, the game's item data still loading, or a player whose equipped gear or stats were never seen this run - and both the Leaderboard window and the shared results image label which method produced the list, so the reasoning is never hidden.

## 1.14.1

**Shared images are anonymous**
- The shared results image no longer marks which row belongs to the person who generated it - no "(you)" label, no highlight color, nothing that singles anyone out. Everyone in the list, including yourself, is shown exactly the same way. Posting a run's results no longer reveals who shared it; who you are is only ever shown to you, inside the app itself.

## 1.14.0

**Share results as an image**
- A new "Share results" button on the Instance window renders everyone's damage this instance - ranked, Top 5 Contributors highlighted in gold, Top 5 Leechers in coral (same nexus-aware rule as the Leaderboard window: someone who left before the final boss died is never shown as a leecher) - into a single shareable picture. One click copies it straight to the clipboard, ready to paste into Discord or wherever a run gets organized; a copy is also kept under the app's `exports` folder.
- Built for exactly how these runs tend to get organized - in a Discord, where people ask where they stood - without anyone needing Peach installed themselves to see it.

## 1.13.0

**Boss segments window, rebuilt as one panel**
- The Boss segments window now shows a timeline of every boss/miniboss fight (shape of the run, sized by time spent), a quick-jump row below it, the selected fight expanded into full detail (your dps, the party's dps, your share of the boss's health), and the rest kept compact underneath for comparison. Click a timeline block, a chip, or a compact row to switch which fight is expanded.

**Top 5 Contributors and Top 5 Leechers**
- A new Leaderboard window: Top 5 by total damage, Top 5 by damage dealt to bosses, and a Top 5 Leechers list - the 5 players who did the least damage.
- Someone who nexuses (or otherwise leaves) before the dungeon's final boss is defeated is never counted as a leecher for it, even if their damage is the lowest in the instance - they forfeited the rest of the fight, they didn't coast through it. They're left out of the list entirely rather than unfairly ranked against people who saw the fight through. This only applies to dungeons with a confirmed final boss (currently The Shatters); elsewhere the list is labeled "all players" rather than silently pretending an accuracy it doesn't have.
- Top Contributors is never filtered this way - leaving early doesn't erase the damage already dealt.

## 1.12.0

**Boss segments now detect themselves - no per-dungeon mapping needed**
- Which fights earn their own DPS segment is now worked out automatically for every dungeon, not just The Shatters: the game's own boss/miniboss/encounter label, combined with a check for any enemy whose health clearly stands out from the regular enemies around it in that same run (so a true miniboss the game doesn't officially label still gets its own segment). Segments are ordered by max health, highest first - the fight that mattered most leads the list.
- The dungeon's confirmed final boss (what ends the Overall DPS clock - still just The Shatters' The Forgotten King for now, cross-checked against the RotMG wiki, the RealmEye wiki and the game's own object data) is called out separately as "Final boss" instead of ranked by health, since reliably knowing which fight actually ends a dungeon takes real dungeon knowledge, not just a health number.
- Regular enemies are never miscategorized as their own segment just for being a touch tankier than average - the check needs a real outlier, and backs off entirely when too few enemies have been hit yet to tell "trash" from "notably tough."

**Enemies list: filter and see your own damage per enemy**
- A new All / Bosses / Trash filter on the Enemies window, so you can isolate the fights that matter or everything you've cleared along the way.
- Each enemy's line now also shows your own damage to it, next to the party's total, without needing to click in.

## 1.11.0

**Overall DPS stops at the final boss, not the exit portal**
- In dungeons with a mapped final boss (currently The Shatters), the Overall DPS view now stops counting the moment that boss is defeated, instead of continuing until you walk out through the exit portal. Previously, standing around or walking to the exit diluted your Overall dps with dead time where nothing was being fought. "Time inside" (shown separately) still reflects how long you were actually in the instance - only the DPS calculation's own clock changes.
- Dungeons without a mapped final boss are unaffected and behave exactly as before; more dungeons can be mapped the same way over time.

**Named checkpoint bosses (per-dungeon stages)**
- The Shatters' three major bosses - Bridge Sentinel, Twilight Archmage and The Forgotten King (the final boss) - are now recognized by name and order. Each already gets its own isolated fight window (time and dps from your first hit on it to your last), the same as every enemy always has; this makes that per-fight breakdown visible and labeled instead of being buried in the general Enemies list sorted by recency. The Enemies window now shows "Stage 1 of 3", "Stage 2 of 3" or "Final boss" next to each one as you reach it.
- Confirmed against the game's own object data and the dungeon's public wiki page rather than assumed, after the user asked specifically for the Shatters boss order to be checked rather than guessed.

## 1.10.2

**Vault/gear reliability fixes**
- Fixed a crash: a malformed enchant code (truncated, dropped, or otherwise not the length the game normally sends) could throw while grouping your Vault, Gifts or equipped gear, which aborted that entire refresh - not just the vault, but your DPS tracking, player list and everything else on that tick, since one shared update builds the whole page. Malformed codes are now treated as having no enchants instead of crashing.
- Fixed a related crash one level deeper in the same decoding: a code that was a valid length but still too short for the data it claimed to hold could throw past the end of its own bytes. Same fix - it is now read as empty instead.

**Instance history reliability fix**
- Fixed a crash: a saved day's history file with a corrupted or hand-edited entry in it (a stray null in the list) could throw while stepping the Instances page back to that day. Corrupted entries are now skipped instead of crashing that page.

**Verified clean, no fix needed**
- A broader adversarial pass this round also covered: every class's fit check against its own and every other class's starting weapon (all 19 classes, 342 cross-checks, all correct), the Shatters radar's map-name detection with null/missing map data, and item name lookup for unknown ids - no issues found in any of these.

## 1.10.1

**Shatters radar fixes**
- Fixed a leak: turning the overlay off only hid the window, but its repaint timer kept running in the background forever. It is now fully disposed when turned off, and built fresh the next time it's turned on.
- Fixed: if the overlay's calibrated position ever ended up fully off every connected screen (a monitor unplugged, or a drag gone wrong before the release), there was no way to get it back, since you cannot drag what you cannot see. It now falls back to a safe default position automatically if that happens, and its saved size is kept within a sane range.
- Verified with an adversarial test pass: zero/negative/huge map dimensions, out-of-bounds and garbage entity positions, hundreds of simultaneous marks, and unknown object types all paint safely with no crashes.

## 1.10.0

**30 days of instance history**
- The Instances window now has day arrows: step back up to 30 days to see instances from earlier sessions, even after restarting Peach. Each saved instance shows its dungeon's own portal picture.
- Click a saved instance the same way as a finished instance from today, to review its Live, Instance and Players data.
- Only Live, Instance and Players data is kept - never the vault or the panels carried over from Tomato. Saved as one file per day under the run folder; days older than 30 are deleted automatically on startup.

**Shiny items on the Gear window**
- Equipped Shiny items now get the same treatment as in the Vault: a gold glow, the SHINY tag, and a reflection sweep, on the Players page's Gear and stats window.
- The reflection sweep (Vault and Gear both) now only plays while your cursor is actually on that item's picture, instead of continuously - the glow and SHINY tag stay visible either way so a shiny is always identifiable at a glance.

**Class/gear verification**
- Internal check added that catches a class being shown with gear it could never actually equip, against the game's own class and item data, so a display bug like that can't ship unnoticed again.

**Shatters Hardmode radar (beta)**
- A new overlay, off by default, draws small marks over your game's own minimap for the Stone Idol, a Derelict Monument and the Source as Peach spots them among entities the game has already sent your client - the same passive, single-client reading everything else in Peach does, nothing from other players or any outside service.
- Turn it on from the new Shatters window on the Instance page; it starts watching immediately and stays watching across instance changes, so there is no race to lose by starting it before you load in.
- Calibrate once to line the overlay up with your own minimap (drag to move, drag the corner to resize); click-through the rest of the time so it never blocks play. Re-calibrate if you change your minimap's zoom.
- Marked beta: the detection, overlay rendering and click-through were all verified in isolation (synthetic data, screen capture), but this has not yet been run against a live Shatters Hardmode instance. Marked as such in the app itself (window title and empty-state text) until that happens.

**DPS Logger shelved**
- Superseded by the Instances history above. Its code stays intact, just not shown, the same way the old File/Edit/Info menu was kept.

## 1.9.0

**Exaltation progress on Your stats**
- Each stat tile on the Instance page's Your stats window now shows a progress bar and "x/75" towards your next exalt point on that stat, for the class you're playing. Comes straight from the game's own exaltation packets, not estimated.

**New Vault page**
- A new rail page shows what's sitting in your vault chests, gift chest and potion storage, as item pictures grouped into stacks with a count, the same style as the Gear window. Switch between the three with the buttons at the top of the window.
- Click an item to see its name, how many are stored, and its enchants; click an enchant to see what it does.
- Enchanted items carry a faint glow colored by their rarest enchant's tier (white, green, blue or violet for tiers I-IV).
- Shiny items (the ultra-rare variants with their own sprite) are detected from the game's own item data, labeled SHINY, and get a periodic white reflection sweep across their picture, the same as they shine in game.
- The "Vault loaded" indicator next to the sniffer status shows whether your vault, gift chest and potion storage have been seen this session; once loaded they stay shown until you next open your vault, even across instances. The subtitle reads "Nexus · Vault" rather than wherever you happen to be, since the vault isn't tied to your current instance. Not loaded yet, it tells you to load into your vault to populate the page.
- Reads only what the game already sends when you open each of those in game this session; nothing is fetched or guessed.

## 1.8.4

**Calculator: percentage bonuses**
- Items and enchants that raise a stat by a percentage, such as Conqueror's Crown with its 25% attack and dexterity, were listed as "no attack or dexterity" and did nothing in a build. They now show their percentages in the menu and are counted, as a percentage of your own unboosted stat.

## 1.8.3

**Dyes and cloths fix**
- Dyed players were drawn in one flat wrong colour. The game sends which dye or cloth item a player wears, not the colour itself; Peach now looks the item up in the game files to get its colour or pattern.

## 1.8.2

**Players as they look in game**
- The player picture in the Gear window now shows the class or skin walking to the right.
- Dyes and cloths are applied: the player's main and accessory colours or patterns are drawn on the parts of the skin the game marks for them, with the game's own shading.
- Everything comes from the game files on your computer and from what the game sends about each player.

## 1.8.1

**Share of boss health**
- New line "Of boss health" under your total damage in the You window, and in every player's details. It is the damage that player dealt to bosses as a percentage of the bosses' total health.
- The health used is what the game reports for each boss, which is already scaled for the number of players.
- With several bosses in an instance it counts them together.

## 1.8.0

**Stats and skin in the Gear window**
- The Gear window, now "Gear and stats", shows the chosen player's eight stats in the game's own arrangement.
- For you it reads like the game: the total, what your equipment adds in brackets, and what your exalts add in blue.
- For other players the game does not send their own stats, so it shows what their equipment adds: items, enchants and set bonuses together, worked out from what they wear.
- A line underneath splits the equipment part into items, enchants and set.
- The player's class or skin is pictured beside their items, as the game files have it. Dyes and cloths are not applied yet.
- Window positions go back to the default arrangement once, to give the window room.

**Players list**
- A player who changed character was listed twice, once per character. Only their current character is listed now.

## 1.7.4

**Calculator: set bonuses**
- Wearing two, three or all pieces of a set now adds that set's attack and dexterity to a build, and taking pieces off removes it again.
- A build's card names the set and how many of its pieces are worn, such as "3/4 Storm Caller Bard Set".
- All 130 sets in the game files are read.

## 1.7.3

**Calculator: the game's own enchant symbols**
- The four level buttons of an enchant now show the game's own symbol for each level, in the game's colours. As in the game, the colour tells the kind of enchant and the frame around the symbol grows with the level. The level in use is bright and outlined, the others are dimmed.
- The enchant menu and the small marks on each item use the same symbols.

**Calculator: missing items**
- About 670 items were missing from the calculator, set items such as Augur of the Tempest among them, because the game writes them slightly differently in its files. They are all read now, with their pictures.

## 1.7.2

**Calculator: enchants belong to an item, with four levels**
- Enchants are now kept per item. Click one of a build's four items to look at it: its name and its enchants show underneath. Small coloured marks on each item show how many enchants it carries.
- Swapping an item out removes its enchants with it.
- Every enchant has its levels I to IV as four small buttons, each level in its own colour: green, blue, violet and gold. The level in use is filled in; click another to switch.
- An item takes up to four enchants.
- To change an item, click it again once it is picked, or use "Change".

## 1.7.1

**Calculator: copies, equipment and enchants from menus**
- Your character is the "Current" build. "Copy" makes a copy of any build; up to four copies are drawn on the graph next to it, each in its own colour.
- Click any of a build's four item pictures to pick another item from a menu. The menu shows every item of that kind with its picture and what it does, strongest first, and narrows as you type.
- Changing armor, ring or ability moves the build's attack and dexterity by what those items give.
- Each build lists the enchants that change damage per second. "+ Add enchant" opens a menu of those enchants with the game's description; the cross next to one removes it.
- Changing anything on "Current" leaves it alone and makes a copy with the change.
- The typed "Compare with weapon" box is gone.
- Copies are kept until Peach closes.

## 1.7.0

**Calculator**
- New "Calculator" page in the side rail. It shows the damage per second your weapon is expected to deal against any enemy defence, as a graph.
- It fills itself in: your class, attack, dexterity, exalt damage bonus, the weapon you hold and its enchants are read from the game. Nothing has to be typed.
- Status effects are switches: Berserk, Damaging, Weak and Dazed on you; Curse, Exposed, Armor Broken and Armored on the enemy.
- "Compare with weapon": type part of any weapon's name and press Enter to draw it as a second line with your stats.
- Move the pointer over the graph to read the numbers at one defence.
- It is a direct formula, worked out only while the page is open, so it does not slow the tracker. Weapon data comes from the game files on your computer.
- Enchants are no longer listed by themselves. In a player's details an enchanted item shows a +; click the item to open its enchants and again to close them. In the Gear window nothing is opened until you click an item.
- Not included yet: ability damage, on-hit effects, burst weapons, summons, and weapons that fire several different kinds of shot (only their first kind is counted).

## 1.6.2

**What an enchant does**
- In the Gear window, click an enchant to open a short description of what it does. Click it again to close it.
- The descriptions are the game's own, read from the game files on your computer.

## 1.6.1

**Gear window**
- New "Gear" window on the Players page. Click any player in the Players list and it shows their weapon, ability, armor and ring as pictures.
- Click a picture to list that item's enchants underneath. A violet dot marks the items that have enchants.
- It works as soon as a player is seen, before they have dealt any damage.
- Window positions go back to the default arrangement once, to make room for it.

## 1.6.0

**Item pictures**
- Items now show the game's own picture: next to each line under Damage by item, and next to the weapon, ability, armor and ring in a player's details.
- The pictures come from the game files already on your computer, so nothing is downloaded.

## 1.5.9

**Enchants in player details**
- A player's details now list the enchants on their weapon, ability, armor and ring, each on its own line under the item. This works for you and for every other player who has dealt damage.
- Items without enchants show no extra lines.

## 1.5.8

**Pet damage on your bar**
- Your pet's damage is now part of your total, dps, rank and share. The end of your bar is gold, as wide as the pet's part of your damage.
- "Pet" has its own gold line under Damage by item.
- Other players' bars have no gold part: the game reports their pet's damage mixed in with their own, so it is already in their total but cannot be told apart.

**Starting the sniffer**
- New "Auto-start" switch above "Check for updates". Off by default. When on, the sniffer starts by itself each time Peach opens.
- When the sniffer is started in the middle of an instance, Peach now says it is waiting for your next instance and that tracking begins at the next loading screen. The game's data can only be read from the start of an instance.

## 1.5.7

**Pet damage**
- "Pet damage" now registers pets whose attacks the game reports under your own name, such as Electric. Peach counts as pet damage everything the server reports as yours that none of your own shots accounts for.
- That damage is no longer added to your own, so "Before server check" is no longer inflated by it.
- Limit: ability damage that works without a shot, such as poison or traps, is reported by the game the same way and lands in this line too.

## 1.5.6

**Damage by item: abilities you shoot yourself**
- Your own quiver shots, and other abilities your game fires as shots, are now listed under the ability. Before, they were added to the weapon you were holding.
- Other players' ability shots are matched to their item by shot number where the game announces them. Hits that cannot be matched are still credited to the weapon held.

## 1.5.5

**Easier party**
- Every player's damage bar now has a small person button next to their name. Click it to add them to your party; it turns solid gold. Click it again to take them out.
- The Players list has the same button at the end of each row, so you can add someone before they have dealt any damage.
- In the Party window each member has a cross at the end of their row to remove them. Clicking the row itself no longer removes them; it shows their details instead.
- A "Refresh" button on the Party window re-reads every member's class and presence from the instance you are in.
- Your party is saved by username and stays through every instance and after closing Peach, as before. Members who are present without having dealt damage now also count toward "instances together".

## 1.5.4

**Players are listed as soon as they are seen**
- The Players window now lists everyone Peach has seen in the instance, not only those who have dealt damage. Players who have not hit anything yet come after the damage dealers and read "no damage yet".
- Party members who are present but have not dealt damage show as "here", and count toward "N of M here".
- The damage windows are unchanged: they still list only players with recorded damage.
- The game only sends players near you, so the list is everyone seen so far, which in a large instance can be fewer than everyone in it.

## 1.5.3

**Cleaner title bar**
- The old File, Edit and Info menus are gone from the top of the window. The sniffer is started with its own button, and the settings those menus held keep their saved values.

## 1.5.2

**Accuracy label on your own damage**
- Your damage now carries an "Accuracy" line, in the "You" window and in your player details, saying how far the server's numbers back it:
  - **Exact** - nobody else hit the enemy, so your damage is matched to the health it lost.
  - **Server-checked** - others hit it too; your damage is held to the health they did not account for.
  - **Partial NN%** - only that share could be checked; the rest is the game client's own calculation.
  - **Estimated** - the enemy's health was never seen, so the figure is the client's calculation alone.

## 1.5.1

**Own damage check**
- Hits the server reports without an owner are no longer taken off your damage. They are usually pets or summons, but the server sometimes sends your own hits back that way, and subtracting those could cut your number far too low.

## 1.5.0

**Four tabs instead of thirteen**
- The left rail is down to four entries. Nothing was removed; everything has a place.
- **Live**: the damage list, your numbers and the current fight, as before.
- **Players**: everyone who dealt damage, the chosen player's damage by item and details, and now your party as well.
- **Instance**: instance details, enemies, finished instances, and now your character stats as well.
- **More**: the eight sections carried over from Tomato (Chat, Key-pops, Security, Characters, Statistics, Daily Quests, My Info, DPS Logger) as tabs across the top of one page.
- Window positions go back to the new default arrangement once.

**Updates finish the job**
- After an update, Peach now also rebuilds `Peach.exe`, refreshes its shortcuts and re-reads the game's data, the things `Setup.bat` does. No update needs Setup to be run by hand any more.
- A small "Updating Peach" window shows while this happens, then Peach reopens.
- Peach also notices at startup when its launcher and shortcuts were made for an older version, and quietly rebuilds them. That covers the update from 1.4.0, whose updater did not do this yet.

**Pin to taskbar**
- Peach can now be pinned to the taskbar properly. Right-click the Peach shortcut, or the Peach button on the taskbar while it is running, and choose "Pin to taskbar".
- Before, Windows filed the running window under Java: a pinned Peach showed a second button when opened, and pinning the running window pinned a Java that opened nothing. Peach now tells Windows who it is, and its shortcuts carry the same identity.
- Setup also adds Peach to the Start menu.

## 1.4.0

**Updates install themselves**
- When a newer version exists, Peach asks **Update and restart**. On yes it downloads the release, closes, puts the new files in place and starts again by itself. Layout, party and settings are kept.
- Peach also looks for a newer version once, a few seconds after it starts. It says nothing unless there is one.
- **Check for updates** can no longer stay on "Checking": every failure is caught, and the check gives up after 25 seconds with "Couldn't check". What happened is written to `run\update\update.log`.
- The check no longer uses GitHub's rate-limited API.
- A copy built from source is not overwritten; it is offered the download page instead.

**Setup**
- Setup no longer replaces the app while Peach is open; it asks you to close Peach first. Swapping the file under a running Peach is what made the update button hang.

## 1.3.0

**Your own damage is checked against the server**
- The game server reports the damage of everyone else's hits, but never your own; those your client works out itself, and the server does not always register every one of them. That could leave your number higher than what other players see.
- Peach now holds your damage on each enemy against the health the server says that enemy lost. Alone on an enemy, your damage is the health it lost. In a group, it is the health lost that the other players' reported hits do not explain, and never more than Peach calculated.
- Damage beyond an enemy's remaining health is no longer counted for you, and an enemy you only tagged and left is not treated as killed.
- When the checked figure differs from the calculated one, the You window and your Players page show the calculated one as "Before server check".
- Finished instances keep the checked figures they ended on.

**Publisher information**
- `Peach.exe` now carries **JustAutomate-AI** as its company, with the product name, description and version filled in. They show under Properties > Details and in Task Manager.
- The desktop shortcut's description, the About box and the app file's manifest name the publisher as well.
- Peach is not code-signed, so Windows security prompts still say "Unknown publisher". That needs a paid signing certificate.

**Diagnostics**
- With an `audit` folder next to the app, Peach writes the server check's numbers per enemy and every own hit to that folder.

## 1.2.0

**Reset the current instance**
- A small **Reset** control at the top right of the damage list sets the damage shown for the current instance to zero. Damage, hits, time, deaths and pet damage all count from that moment.
- It takes two deliberate clicks: the first arms it ("Click again to reset"), the second confirms. A fast double-click does not count as the second click, and the armed state lapses after four seconds.
- An **undo arrow** appears beside it after a reset and brings the earlier numbers back. Resetting more than once can be undone one step at a time.
- The reset only changes what is shown. Nothing recorded is deleted, and a new instance starts clean as before.

**Step through instances**
- Two arrows beside the instance name step back through finished instances and forward again to the live one.
- Finished instances are shown with the numbers they ended on. The subtitle says which one is on screen, for example "Finished instance 2 of 5".

**Under the hood**
- The source is compiled as UTF-8 on every machine.
- A damage audit for investigating own-damage accuracy is included. It stays off unless a folder named `audit` is created next to the app.

## 1.1.0

**Players page**
- Click a player's bar on the Live page to see their damage by item, hit count, biggest hit and equipment. A list on the page switches between players.
- Ability shots are credited to the item that fired them. Other hits are credited to the weapon held at the time, because the game names the firing item only for ability shots.
- Adding or removing a party member moved to the rank number at the left end of a bar.

**Pet damage**
- Pet hits used to be dropped. They are now counted apart from the player lists: all pets together on the Instance page, and your own pet in the You window once Peach has identified it (after a visit to the Pet Yard or the Daily Quest Room).

**Updates**
- **Check for updates** at the bottom of the left rail asks GitHub for the latest release and offers its download page when it is newer. Nothing is downloaded or installed automatically.
- The window title and rail show the Peach version.

**Layout**
- The Live page holds three windows: Damage (the largest), You and Current fight. Instance, Party and Your stats have pages of their own.

## 1.0.0

First public release.

- Damage for the instance you are in: overall over elapsed time, per enemy, or bosses only; as damage per second or total damage dealt; for everyone or for your party.
- A party remembered by username across instances and sessions.
- Instance details, every enemy hit, finished instances for review, and your character stats.
- Movable, resizable, closable windows with a remembered layout.
- One-step `Setup.bat` that checks for Java and Npcap, offers to install what is missing, and creates `Peach.exe`.
- The sniffer is started by hand each session and stops when the app closes.
