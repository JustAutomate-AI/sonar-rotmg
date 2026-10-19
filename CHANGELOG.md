# Changelog

Every release of Sonar, newest first. The version number is also shown at the bottom of the app's left rail.

## 2.40.0

**The Shatters overlay is a minimap now, not a layer over one**

The overlay drew marks on the game's minimap and had to be at the game's zoom for them to sit on the right ground. The game does not publish its zoom, in any packet, file or registry key — so four releases went into watching every way a person might change it, and a missed one is silent. RealmShark's Potato has never played that game: it draws its own map and zooms that, so there is nothing to keep in step. This is that, finished.

- **It draws the whole minimap, not half of one.** Ground from the tiles the game has already sent, and over it the three things the game's own minimap shows — **other people**, **anything that can be fought**, and **the ways out** — plus you. Nothing is lost by covering the game's minimap, which is what makes covering it honest.
- **So it covers it.** Solid, not half-transparent. Two copies of the same place at different scales showing through each other is harder to read than either alone; that was the state in 2.37.0 and it was rightly switched off. There is one picture now, at one zoom, and that zoom is whichever one you last asked for — wheel, keys, or the minimap's own buttons.
- **It covers nothing until it has something better.** Before the first tiles of an instance arrive, the overlay draws nothing at all and the game's own minimap shows through untouched. It takes over as it learns the ground.
- **On by default**, with `Map: on/off` beside the overlay switch for anyone who wants the old marks-only behaviour back.
- The ground is Sonar's own rather than Potato's. Potato ships **fourteen pre-drawn map images** and can only draw ground for a map it has a picture of; Sonar renders live tiles with the colour the game itself draws each one as, so it works on anything — including a realm, which no shipped image could ever cover.
- Seven assertions on what belongs on a minimap: another player is drawn as a player, a character who is not a player is drawn as something to fight, every kind of portal is a way out, a player standing in a portal is still a player, walls and loot and decoration are left off as the game leaves them off, the drawn kinds cannot be mistaken for one another or for a real object type, and nothing is gathered at all when the map is not being drawn.

## 2.39.1

**What's New opens on what is new**

- The What's New panel reads the changelog out of the jar and walks it top to bottom, because a changelog is written newest first and that is the only order it is ever in. It was not. An entry inserted above the wrong heading had been sitting at the top since **2.37.1**, so for four releases the panel led with a version people had already had for hours.
- The entry is back where it belongs. Trusting the file's order is fine; having nothing that notices when the file stops deserving it is what let this run for four releases without anybody spotting it.
- So the self-test reads the changelog out of the built jar now and checks it: every entry newer than the one below it, and **the newest entry is the version the build says it is**. That second one also catches the other half of the same mistake — a version raised without its notes written — which is the more likely way in.
- Version numbers are compared as numbers rather than as text, so 2.39.0 sorts above 2.4.0 and 2.38.10 above 2.38.2.

No behaviour changes.

## 2.39.0

**The settings page measures itself, and a typed word arrives**

Two faults with one shape: something was drawn that nothing had counted.

- **The zoom buttons disappeared when the page was zoomed in.** The **WORDS TO WATCH FOR** section was missing from the page's height calculation altogether — not shortened, absent. A pane scrolls to the height it says it is, so everything painted below the uncounted section hung off the bottom with nothing to scroll to. Zoom in far enough that the sections above fill the window and the zoom controls themselves are the first thing over the edge, which is a particularly unhelpful way to lose a control.
- The section's height is now written once and read by both the painting and the measuring, so the next edit to either cannot put them back out of step.
- **Add a word did not add words.** It had exactly one way to commit — the Enter key — into a text field that was asked for focus *before* the popup was on screen, which does nothing. So the field often was not what you were typing into, and the word simply never arrived. There is an **Add** button now, focus is requested once the popup actually exists, and what a typed word becomes is decided in one place rather than inline in the popup.
- Six assertions: the words section is given room when empty and grows by a row per word; a typed word is kept; asking for the whole word is spelled by quoting it; nothing typed adds nothing; and a word already watched for is not watched for twice.

**Still open: the realm biome fame bonuses.** The game's own panel pays these cumulatively — `Foe +20`, `Adversary +100`, **+120** — where Sonar pays 20. Sonar's thresholds match the published table exactly (200 / 1,000 / 5,000 / 10,000 / 50,000 at 20 / 100 / 400 / 1,000 / 2,500), its cumulative arithmetic is right, and each biome reads its own decoded field rather than an index into a shared list, so none of the usual suspects explains it. That leaves the decoded **count** landing below 1,000 when the game has it above. Settling that needs one character's copied working, which prints the decoded count beside the raw blob the count came out of.

## 2.38.2

**The installer stops asking questions, for people who have not updated yet too**

- 2.38.0 made Sonar run the installer quietly, and that works — but only from 2.38.0 onward, because **the copy doing the updating is the old one**. Anybody still on 2.37.1 or earlier gets their own updater, which shows the installer. Telling them the fix is in the version they cannot reach without the fix is not a fix.
- What an old copy *does* get is the **new release's installer**, because that is what it downloads and runs. So the pages come out of the package instead, and that reaches everybody on their very next update.
- Two jpackage flags are gone. `--win-dir-chooser` added the **Destination Folder** page with its Change button; `--license-file` added a licence page before it. Between them they were most of why updating felt like installing.
- Nothing is lost either way. The install is **per user** and goes where per-user installs go, so the folder was a question with one sensible answer — and `LICENSE.md` ships inside the app folder regardless, which is where anybody looking for it would look.
- So: on **2.38.0 or later**, an update is silent — a small window, and Sonar comes back. On anything **older**, the next update is a progress bar instead of a wizard, and the one after that is silent.

## 2.38.1

**The ground was being stretched to fill the frame**

- The overlay's own map did not line up, and the reason was not the zoom. It was one line of mine.
- `paintMap` built a source rectangle from the view and handed it straight to `drawImage`. **The view is very often wider than the map** — always in the Nexus, and in a realm the moment you zoom out — so that rectangle ran off the edge of the picture. `drawImage` clips a source rectangle like that but **keeps the destination it was given**, so whatever piece of ground survived the clipping was stretched across the whole frame.
- That is the worst shape a bug can have here, because the result still looks like a map. It is the right ground, drawn convincingly, at the wrong scale and in the wrong place — which reads as "the overlay does not line up" and sends the search off towards zoom levels and calibration, where it can be looked for indefinitely without being found. It was looked for there for several releases.
- The source is now clipped deliberately and the destination clipped with it by the same number of tiles. Ground that is not in the picture is not drawn, and every tile that is drawn lands where its own position says it should.
- Seven assertions, all on one number — the pixels a tile is given on screen must be the scale that was asked for, whatever part of the map is in view: a view inside the map, a view hanging off its edge, a view zoomed out past the whole map, and a view with no map in it at all.

## 2.38.0

**Updates install themselves, and the puzzle grid is ready every time**

- **No more clicking through the installer.** An installed copy of Sonar is upgraded by the Windows installer rather than by unpacking files over it, which is right — doing it the other way leaves Add or Remove Programs describing a version that is no longer on disk. But the installer was being *shown*, so every single update meant a wizard to click through. That is a worse thing to ask than most updates are worth.
- The only reason it was shown was that nothing was waiting around to run it quietly. Something already is: the handover script that waits for Sonar to close, puts the small **Updating Sonar** window on screen and starts it again afterwards. It now runs the installer too, with `/qn /norestart`. Sonar installs **per user**, so there is no elevation to prompt for and nothing anybody has to answer.
- If the quiet install fails for any reason, the installer is **shown** rather than given up on, so a failure ends with a wizard that can be finished by hand instead of an update that silently did not happen. Exit code 3010 is read as success, because it only means the installer would like a reboot.
- A copy upgraded by unpacking still is, and either way Sonar restarts itself when it is done.
- **The Observatory puzzle grid is prepared fresh for every instance.** Entering a board by hand takes the grid off the game until Live is pressed, which is right while you are standing in front of that board. It was wrong the moment you left: the pane stayed in hand-entered mode across the portal, so the next Shatters found the grid already taken and never filled it in — and the solver that had worked by itself a few times looked as though it had stopped for good.
- **The Shatters overlay's own map is off unless asked for**, and its `Map` switch is back. 2.37.0 drew Sonar's ground over the game's minimap everywhere by default and removed the switch in the same breath; a second copy of the same ground at a different scale makes the real minimap harder to read, which was not a trade to make on anybody's behalf.
- Thirteen assertions: the handover takes an installer and runs it with nothing to click, one that will not run quietly is shown rather than abandoned, 3010 counts as success, unpacking still works, Sonar restarts either way, the script's branches balance, and the puzzle grid hands itself back at every door while holding firm inside one.

## 2.37.1

**The puzzle grid is prepared fresh for every instance**

- The Observatory solver reads the nine towers off the wire and works the board out by itself. It did that a few times and then appeared to stop for good — and nothing said why.
- Entering a board **by hand** takes the grid off the game until **Live** is pressed. That is right while you are standing in front of that board: a hand-entered answer should not be overwritten halfway through using it. It was wrong the moment you left. The pane stayed in hand-entered mode across the portal, so the next Shatters found the grid already taken and never filled it in.
- **A new instance is a new puzzle.** The grid now goes back to the game at the door, every time, whatever was on it before — so the solver is always prepped to receive the board it is about to be shown. The buttons follow it back, rather than still offering Solve and Live over a grid that is live again.
- It still holds while you are *in* the instance: a board clicked in by hand stays exactly as entered until you press Live or walk out.
- Six assertions: a fresh grid is the game's to fill in, entering a board by hand takes it off the game, it stays taken while you are still in that instance, the next instance hands it back, the pane says so once so the buttons go back with it, and it does not say so twice.

## 2.37.0

**One switch, and it works outside the Shatters**

- **The overlay had grown five buttons** — calibrate, on, find minimap, map, portals — and four of them existed to work around it not being able to place itself or draw anything. It can do both now. Switching it on finds your minimap and starts drawing, so there is **one button**: on, or off.
- Two buttons survive and only appear when they are any use. **Line up by hand** and **Look again** show up if the search for the minimap came back empty, which is the one case where there is something for a person to do. **Portals** stays behind the same condition: it exists to prove the overlay is on the right spot, and when the overlay finds its own spot there is nothing to prove.
- **And the overlay now works anywhere, which is what makes it testable at all.** Where you are standing was read at the very bottom of the tick, after the work only a Shatters needs — so in the Nexus and the realm the tick returned early and your position was never touched. That was harmless while the overlay drew nothing outside the dungeon. It stopped being harmless the moment it drew its own map, because a map with no centre never appears: the one feature that made the overlay checkable anywhere could only be seen in the one dungeon that is hardest to get into.
- So switch it on in the Nexus. The ground draws itself as you walk, at whatever zoom you set with the wheel, and the Shatters objectives appear on top of it when you are in a Shatters.
- Four assertions: a position that was read is there to be used, the Nexus is read as a place the overlay works in rather than skipped over, the last dungeon's position does not survive into it, and walking into it starts a map of its own size.

## 2.36.1

**The portal the overlay kept missing**

- Lining the Shatters overlay up is checked against portals, because the Shatters is rare and a realm is full of them. One portal was never marked while every other one in the same realm was — which reads as the overlay missing something, and sent the search towards calibration.
- It was not calibration. The test matched the object class `Portal` exactly, and the game has **three**: `Portal`, `GuildHallPortal` and `ArenaPortal`. The two it did not know went unmarked. Matching on the ending finds all three, and finds a fourth if one is ever added without anybody noticing it had to be.
- Two assertions: every kind of portal the game has counts as one, and nothing else does — a `PortalChest` is not a portal.

Worth knowing about the other direction: the client is only told about entities near enough to see, so a portal on the far side of a realm is not in the data at all and no amount of matching will mark it. A portal that is missing while you are standing next to it is a bug; one missing from across the realm is the game.

## 2.36.0

**Instance history has never saved anything, and now does**

Sonar keeps thirty days of finished instances so they can still be reviewed after a restart. It has been writing every one of those days as an empty file.

- The evidence was on disk the whole time and only visible to somebody who thought to measure it: **every day file was exactly twenty bytes**, which is a gzip header and trailer with nothing in between.
- The cause is one field. `Row.tileX` and `Row.tileY` start at **`NaN`**, meaning nobody has yet seen where that player is standing — the honest value, and the ordinary one. **Gson refuses `NaN` outright**, because JSON has no such value, and throws rather than writing anything. Every single save died on it.
- It died invisibly because `record` swallows its exception on purpose, so that a history write can never interrupt the game tick that triggered it. That rule is right and stays; what was wrong was that it could fail forever and never say so. The reason for the last failure is now kept and the self-test reads it.
- An unknown number is now written as **`null`** and read back as `NaN`, which keeps the files ordinary JSON that anything can read — rather than the non-standard bare `NaN` that Gson's own override would emit. A position somebody *did* see still comes back exactly as it went in.
- **And the write no longer destroys the day it fails on.** Opening the file truncates it, so turning the day into JSON *inside* the try meant the throw wiped that day's history on the way past. The text is now built before the file is opened, so a failure leaves what was already saved exactly as it was.
- Five assertions cover it: an instance with a player nobody located saves, what it writes is ordinary JSON with no `NaN` in it, an unknown position comes back unknown, a known one comes back unchanged, and a save leaves nothing to complain about.

The empty files already on disk need no attention — they read as "no instances that day" and are replaced the next time that day is written.

## 2.35.0

**The overlay draws its own map, and finds your minimap by itself**

Sonar spent four releases trying to read a number the game does not publish. The minimap's zoom level is in no packet, no file and no registry key, so the overlay could only watch for the ways a person changes it — the bound keys, the buttons in the corner, the wheel over it — and hope it had caught every one. It had not, and there is no way to know when it has. A missed one is silent and stays wrong until the map changes.

RealmShark's **Potato** has never played that game, and its overlay has worked for years. It draws a map of its own and zooms that, so there is nothing to keep in step. Sonar now does the same, and does not need Potato's pre-drawn map images to do it.

- **Sonar draws the ground itself.** Every ground tile arrives in `UpdatePacket` as the map is explored, and the colour each tile is drawn as on a minimap is already in the extracted sprite sheets as that sprite's most common colour. So the overlay keeps one pixel per tile, painted as the tiles land, and scales it to whatever zoom it is on. Grass comes out green and Oryx's rug comes out red because those are the game's own colours, not a palette invented here.
- **That ends the zoom problem outright.** The ground and the marks are at the same zoom because they are the same picture, and that zoom is whichever one was last asked for here. The wheel, the keys and the minimap's own buttons all step it — but now they are setting Sonar's zoom rather than guessing at the game's, so a missed press is a zoom that did not change rather than an overlay that is quietly wrong.
- **Unseen ground stays transparent**, which is right twice over: it is what the game's minimap does with unexplored map, and it lets the game's own minimap read through wherever Sonar has nothing to say. How solid Sonar's ground is drawn is a setting, and **Map: off** puts the overlay back to marks alone.
- **The minimap is found for you.** Lining the overlay up by hand was the first thing anyone had to do and the thing most likely to be done slightly wrong — and a frame a few pixels out puts every mark a few pixels out at every zoom. **Find minimap** takes one picture of the game's own window and finds the minimap in it by colour: the frame is a flat grey nothing else on the HUD uses, with a darker border just inside. Measured against a real 2560x1440 game window it returns the map to the pixel, frame taken off. It fails rather than guesses — a wrong rectangle would look aligned and be wrong everywhere — and says why.
- **A new instance starts close in**, at the game's own most zoomed-in step of 48 tiles across, which is the one rung Sonar and the game are certain to agree on.
- Ten assertions cover it: the scan finds a minimap drawn to the real geometry and returns the map with the frame removed, a window with no minimap in it is not guessed at, tile colours are read correctly whether the sheet wrote them nought-to-one or nought-to-255, black ground is still drawn as ground rather than as unexplored, a new instance gets a map its own size, tiles off the edge are dropped rather than thrown, and walking into the realm starts the map over.

Only the game's own window is ever captured, never the screen, and nothing is sent anywhere.

## 2.34.2

**The realm is 2048 tiles wide, and the ladder has a rung too many for it**

- 2.34.1 reasoned backwards from a count and got it wrong. The realm's minimap has six zoom levels — counted in the game — and since the number of rungs depends on the map's size alone, that looked like it fixed the realm at 1024 tiles wide. It does not. **The realm reports 2048**, which RealmShark's own Potato confirms by branching on exactly that number, and this ladder gives a 2048-tile map **seven** rungs.
- So the ladder is one rung too long for the realm, and the self-test now says so instead of asserting the convenient reading. What is left standing is only what is true: the count does not depend on how big the minimap is drawn, and each doubling of the map's width adds exactly one rung.
- **The count alone cannot say which end is wrong.** Dropping the whole-map rung at the bottom and halving the top rung both give six, at every map size. Telling them apart needs a scale rather than a count — how many tiles the minimap spans when it is zoomed the whole way in, which is 48 under one reading and 96 under the other. Until that is measured, nothing is changed in the ladder itself; a guess here would move every mark on the overlay.
- No behaviour changes in this release. It corrects a claim that was wrong in 2.34.1's notes and replaces an assertion that asserted it.

## 2.34.1

**The overlay says what size the map is**

- Counted in the game: the realm's minimap has **six** zoom levels. Only one thing decides how many rungs the ladder has, and until now the overlay never said what that thing was. (**Corrected in 2.34.2:** this entry originally concluded from the count that the realm is 1024 tiles wide. It is 2048, and the ladder gives seven for it. The six and the seven do not agree.)
- How many rungs the ladder has depends on the **map's size alone**. The minimap's own size cancels out of it: drawing the minimap bigger scales the top rung and the whole-map rung by the same factor, so a 192-pixel minimap and a 950-pixel one over the same map have exactly as many steps as each other. That is worth knowing because it means a disagreement over the number of steps is never a calibration problem and never a screen-size problem — it is only ever the map's size.
- So the calibration readout now prints it: `map is 1024 x 1024 tiles`, beside the step it is on and out of how many. If the count ever disagrees with the game's again, the number that explains it is on screen.
- Three assertions cover it: the count does not move with the minimap's size, and each doubling of the map's width adds exactly one rung.

## 2.34.0

**The overlay follows the wheel, which is the way the minimap is actually zoomed**

- The minimap has three zooms, not two. 2.33.0 added the **buttons** to the **keys** Sonar already mirrored, and those two are confirmed working. The third is the **wheel over the minimap** — which is how it is done most of the time, and which leaves no trace either of the others watches for: no key goes down and no button is clicked.
- The wheel cannot be polled the way a key can. `GetAsyncKeyState` answers for things with an up and a down, and the wheel has neither — it only ever happens. The one way to see it from outside the window it was aimed at is a **low-level mouse hook**, which asks Windows to call Sonar as each mouse event goes past on its way to whoever it was meant for. Sonar now keeps one.
- **It only ever looks.** The hook reads the wheel's direction and where the pointer was, then hands the event on untouched, exactly as it arrived. Nothing is swallowed, redirected or sent — the game receives every event it would have received with Sonar closed. A hook that dawdles makes the whole machine's mouse feel sticky, so the callback does the least it can: two reads out of the event and a hand-off, on a thread of its own.
- A scroll only counts when the pointer is **inside the overlay's frame**, which is the minimap. Scrolling anywhere else on screen does nothing, and while calibration is on the wheel keeps its own meaning. Scrolling away from you zooms in, and a wheel that sends several notches at once steps several rungs.
- Four assertions cover the one thing here that is easy to get wrong: the wheel's travel is the **signed** high half of the event's `mouseData`, and read as unsigned every scroll towards you comes out as 65,416 and the overlay zooms the wrong way. A notch each way reads as one notch each way, several read as several, and the buttons held while scrolling do not reach the travel.
- The hook is verified to install and unhook cleanly. It could not be exercised end to end here without sending a synthetic wheel event, which is not something Sonar will do with the game running.

## 2.33.1

**A new Nexus is a new instance**

- 2.33.0 put the overlay back to the top rung whenever the map changed, because the game puts its minimap back to full zoom on loading into a new instance. That part is right — it is confirmed in play, not just read out of the client.
- What it got wrong was spotting the moment. It watched the map's **name and size**, which tells one dungeon from another but cannot tell two Nexuses apart, or two realms of the same name — and those are exactly the hops where a zoom gets reset most often. Nexus to realm to Nexus, and the overlay would hold the old rung through all of it.
- It now watches the **seed** instead: the number the game builds the instance from, different for every instance even when nothing else about it is, and already in the same packet as the name and the size.
- Four assertions cover it: a dungeon load counts, standing still in it does not count again, two Nexuses that differ only by seed are two instances, and each one steps the counter exactly once.

## 2.33.0

**The Shatters overlay follows the minimap's own zoom buttons**

- The overlay has mirrored the game's minimap zoom **keys** since it was built, whatever they are bound to. It turns out that covered half the ways to zoom. The game also draws a pair of grey plus and minus buttons in the minimap's corner, and clicking one presses no key — so the overlay never saw it, and sat on the old rung while the map under it changed scale. That is the whole of the marks not lining up any more after a zoom.
- It now watches those buttons too. The overlay is already lined up with the minimap, so where they are is known without being told: measured off the minimap, each is about a tenth of the frame wide, stacked against the top right corner. A click there steps the overlay exactly as the key would.
- Clicks are watched on their own timer at 40ms rather than on the 200ms repaint. A click on a button is over well inside a fifth of a second, so most of them were being missed even where they were being looked for — the keys were quietly dropping presses for the same reason, and now do not.
- **Walking into a new map no longer leaves the overlay on the last map's scale.** The zoom ladder is built from the map's size — a small map has fewer rungs, and the same rung is worth a different number of pixels per tile on each — and nothing rebuilt it when the map changed. The game rebuilds its own at that moment and starts it at the most zoomed-in rung, so the overlay now goes back to the top rung with it.
- Five assertions cover the button zones, measured off a real minimap: every corner of the plus button and of the minus button lands on its own button, a click on the map itself is not taken for a zoom, the zones scale with the minimap rather than being a fixed pixel count, and a frame with no size on it answers nothing.

## 2.32.0

**The blob now checks itself**

- The game sends a count of **dungeon types completed** alongside the per-dungeon numbers. Sonar has always decoded it and never used it, because it was not clear whether it meant distinct dungeons cleared at least once or total clears across all of them — and a guard built on the first reading would have refused a heavily played character under the second.
- A real character settled it. The game reported **21**, and **21** distinct dungeons came out of the same blob, against **101** clears between them. It is distinct types.
- That makes it the one number in the blob that can check the rest of the blob. Every other count is something only the server knows; this one the decode can be held against. A dungeon silently lost to a renumbering is the fault that shows up as fame quietly *short* — the kind nobody reports, because a plausible total looks like a right one. Sonar now notices it itself.
- The two directions are read differently. **More** dungeons decoded than the game counts is impossible, and is said outright. **Fewer** is only wrong when it cannot be accounted for: the blob carries dungeons Sonar deliberately reads and drops — the Neo wormholes and the legacy block, which have no statistic of their own to land in — so a shortfall is allowed up to that many and complained about beyond it.
- The copied working states it either way, so a report carries the check rather than needing it run again: `DUNGEONS AS DECODED   (21 distinct; the game counts 21)`.
- Four assertions cover it: agreement passes quietly, a lost dungeon is named, the deliberately dropped ones do not trip it, and an impossible excess is called impossible.

## 2.31.0

**The fame working can be handed over in one click**

- A fame total that disagrees with the game is the one fault Sonar cannot diagnose from the way it gets reported. "Off by a hundred" names the symptom and none of the hundred and thirty counts behind it — and the counts are not even the half that matters, because only the **statistics blob** they were decoded from can be replayed offline against another build.
- There has always been a full dump of exactly that, behind `-Dsonar.fame.dump=1`. Nobody running the installer is ever going to set a system property. So it is a button now: **copy**, on the fame card, which puts the whole working on the clipboard as plain text.
- What goes on the clipboard: the figure and its two halves, every line of the breakdown with the count and tier behind it, how far each collection has got, every statistic and every dungeon completion **as Sonar decoded them**, the eight stats against the ceiling the maxing test used, and the blob itself.
- Nothing in it identifies anybody. The game sends no name with these statistics and Sonar does not add one — a character is its class and level, which is what the page already calls it.
- Eight assertions cover it, including that a character whose statistics have not arrived says so rather than quoting a total it cannot support.

## 2.30.0

**The Shatters overlay uses the game's own minimap zoom ladder**

- The overlay's marks sat at roughly **two-fifths** of where they belonged. Measured against the game's own portal icons on a Nexus screenshot, the scale was out by a factor of **2.38** — and one portal of six was not being marked at all.
- The scale model was the wrong *shape*, not a mis-tuned constant. It assumed the minimap shows **480 tiles across** at the first step and halves from there. The client's own code says otherwise, and says it identically in the last Flash build and in two later ones:
  ```
  fit = max(width / mapWidth, height / mapHeight);
  s = 4;
  while (s > fit) { push(s); s /= 2; }
  push(fit);
  ```
- Three things follow that Sonar had wrong. The rungs are **pixels per tile**, not a count of tiles. There are **as many rungs as the map needs** — the Nexus gets three, a realm gets seven — rather than a fixed six. And **index zero is the most zoomed in**, which is the order the game's own zoom buttons step through.
- The base is **192**, not 480: the client builds its minimap as `MiniMapImp(192, 192)`, so the real ladder is **48, 96, 192, 384** tiles across. A minimap drawn larger scales the whole ladder with it, which is where the rest of the error on a 1440p screen came from — the minimap measures about 448px there, and 448/192 is 2.33 against the 2.38 measured off the screenshot.
- The ladder is checked against a transcription of the client's algorithm at **sixteen** frame and map sizes: rung count and every value.

**The view clamp matches the game too**

- The client shifts the map back until its near edge is flush with the frame, so a map smaller than the frame is **pinned to the corner**. Sonar centred it instead. In the game that case cannot arise — the last rung is chosen so the map always covers the frame — so it only showed when the scale was already wrong, which is exactly when it was least wanted.

**Calibration is now a correction, not a requirement**

- Stored per-step scales move to a new name. They were dialled in against a ladder of the wrong shape, and letting them win would have hidden the fix behind the workaround. The starting figure should now be close enough to read; right-clicking a portal's own icon still sets it exactly.
- The minimap's zoom level remains unreadable — of the 315 settings the client stores, there is a rotation mode and two zoom *keybinds*, but no zoom level. The difference is that the rungs are now a known, finite set, so stepping lands on real values rather than invented ones.

**The Fame page is measured rather than guessed at, and quieter**

- The collections rows were drawn **6px taller than the card** that held them, so the "needs ..." line sat on its own bottom border. The row metrics are now stated once, beside the card height, instead of as a dozen unrelated magic numbers.
- The right-hand column was a flat 150px. The price outgrew it on the wider sets while the completion ratio grew towards it from the other side, and on *Hero of the Nexus* `10/49` and `12.5% of base + 5,000` met in the middle. Both are measured now and the bar takes what is left.
- Colour carries less of the work. The "needs ..." line was **alarm red** for the one piece of news on the page that is not a problem — a set with three left is a set nearly finished — so the hierarchy is brightness instead, leaving gold for worth and mint for done. A ledger line paying nothing was drawn in the same bold gold as one paying thousands; it steps down a level.
- On the gold "finishes a collection" rows the sub-line kept the ink chosen for the dark surface and lost nearly all its contrast against the lighter one, which read as the line having been cut off halfway. It steps up with the surface.
- The three panes shared no metrics — gutters of 11, 13 and 14, and two corner radii between them. One of each now, which is most of what makes them read as one page rather than three.

## 2.29.0

**The statistics are read in the order the server writes them**

- The fame total in 2.28.0 was wrong, and this is the fix. On a live character the game paid **10,181** where 2.28.0 worked out **11,951** — over by 1,770, with the flat bonuses carrying all of the error.
- The cause was not the fame arithmetic, the ids, or the bit array. It was the **order**. The server writes the values in a fixed sequence of its own — the general statistics first, then dungeons in four batches with a few counters tucked between them, then the biome kills, then the seasonal dungeons, with two late additions appended out of position at the very end. Sonar walked the bits in **ascending id** instead, which is simply not how the string is laid out.
- Reading in the wrong order does not fail. It silently hands every value to the wrong statistic. That is why it presented as a tie-breaking problem, then as a framing problem, and why it once produced the impossible result of a completion count going **down** between two readings of the same character.
- The sequence comes from [RealmHound](https://github.com/pillarzz/RealmHound), whose `pcstats.rs` states it outright: *"Values are stored in a hardcoded read order, NOT in bit order."*

**The ids were right all along**

- 2.28.0 withdrew most of the statistic ids as unproved and shipped a page that deliberately read almost nothing, on the belief that the numbering was stale. It was not. Sonar's **original id table was correct from the first commit**, and every id derived to replace it was derived under a broken model. The original table is restored.
- With the order and the ids both right, the decode is exact rather than approximately right: **49 of 49 statistics match the game's own Attributes panel**, every biome kill and every dungeon completion included.
- Fame on death on the character it was checked against: Sonar **5,675**, the game **5,675**. 2.28.0 managed 5,640 on the same character and called the missing 35 an unknown dungeon. It was not an unknown dungeon.

**Blobs from before the 2026 patch still read**

- The bit array is **sixteen bytes or thirty-two**. A 2026 update grew it, and a blob frozen on a character that died before that patch still carries the old width. Sonar now works out which a given blob uses by trying both and keeping the one that consumes the string exactly.

**The damage board opens on the last boss**

- A finished run opened on **Whole instance**, which averages the trash across the fight that actually mattered. It now opens on the last boss the run reached, falling back to the last miniboss and then to the whole run. Picking a fight yourself still sticks until the instance changes.

**Under the hood**

- The self-test's blob builder was **sorting its values by id** — the exact fault under test — so it could never have caught this no matter how many assertions were added. It writes in the server's order now, and the suite checks a real captured blob field by field. 323 assertions pass.

## 2.28.0

**The Fame tab, and arithmetic that can be checked**

- Sonar has a **Fame** page again. It was taken out in 2.7.2 because the figure on it could not be trusted: a character the game valued in the thousands was shown a total in the hundreds of thousands. The cause was never the fame arithmetic. The statistics underneath it were being decoded from a bit array read **sixteen bytes short**, so the counts feeding every calculation were nonsense — see below.
- Everything here was checked against a live character with the game's own Attributes panel open beside it. That is the only reason the rest of this entry can be stated as fact rather than as a reading of the published tables, and it corrected three things the tables had me believing.
- The tables behind it are the published ones, checked against each other rather than copied once: thirteen dungeon collections, seventy-six per-dungeon completion ladders, and every counted category from Cartography to Runic Tundra kills. The one place the published table contradicts its own shape — the Mad Lab's third tier, printed as `+20` where every other dungeon follows `1 / 2 / 4 / 8 / 20` — is read as the typo it is, and said so in the source.
- Percentages are added once to base fame and never compounded, which has been the game's behaviour since Exalt 1.2.0.0 (Oct 2020). Only two families pay them at all: maxing a stat, and finishing a collection. Together they come to **145% of base fame** at the absolute most, and that ceiling is now a **guard**: a working claiming more than the game could possibly pay is refused rather than displayed. The old page had nothing of the kind.

**Three rules corrected by the game itself**

- **Every tier passed pays, added together.** A dungeon run a hundred times pays Scout *and* Explorer *and* Adventurer *and* Conqueror *and* Master. I had read the published tables as paying only the tier reached, on an argument from how much the game's most fame-trained dead characters are worth. The game settled it in one line: its own panel paid **Potions +70** for 113 potions drunk and **Ability +70** for 2,005 ability uses, and both counts sit in a second tier worth 50. Seventy is twenty plus fifty.
- **Percentages round up, and are rounded per line.** The panel paid **Maxing +1,099** on a base fame of 2,271 for one vital stat and five others. Rounded down that is 1,094; rounded up, line by line, it is exactly 1,099. Rounding once at the end instead of per line also loses a point.
- **A maxed stat is measured with its exaltation kept on.** The boost the game sends already contains the exaltation — an attack of 76 with `(+11)` from gear and `(+5)` from exaltation reports a boost of **16**. Sonar subtracted all 16 to find the base *and* added the 5 to the ceiling, so every exalted stat was pushed under its own maximum from both ends. Only a stat with no exaltation could still read as maxed, which is why a character with six maxed stats was paid for one. The class ceilings were wrong too, read from memory rather than from the game's data: a Wizard's life caps at **700**, not 770.

**The statistic ids were renumbered and nobody noticed**

- The ids Sonar read the statistics blob by were stale, so figures landed on the wrong names: shots fired was being counted as Sprite World completions, and tiles discovered as assists against gods. **50 ids are now proved**, each matched against the game's own panel on a live character, nine of them dungeons.
- The ordering came from [ExaltAccountManager](https://github.com/MaikEight/ExaltAccountManager), whose `pcStatsParser.js` keeps a current list of the statistics in the order the server writes them. Its index numbers are not the ids — its "Quests = 12" is really id 28 — but its **order** is the server's order, and walking that order against a live blob explained **36 of 36** set bits with every value matching in sequence.
- Every dungeon whose id has not been proved is marked unknown and is not read at all. A guessed id would quietly pay some other dungeon's reward, which is the class of fault that took this page out in the first place.
- Where it lands: on the character all of this was checked against, Sonar works out **5,640** where the game pays **5,675** — the remaining 35 is one dungeon whose id is still unknown, because that character has never run it.

**The statistics were being read wrong**

- Every fame bonus is counted from a packed statistics blob the game sends at character select. `RealmCharacterStats.parseBits` read a **16-byte** bit array saying which statistics the blob carries. The server sends **32**. Sonar was therefore consuming the second half of that bit array as though it were data — sixteen phantom statistics, every one of them reading zero — and every real figure after them landed in the wrong slot.
- This had been wrong since the fork began and could not have been noticed: `GameData` decoded the blob into `currentCharacterStats` and then read it from nowhere. Nothing in the app ever looked at the result until this page did.
- A live character settles it by arithmetic rather than opinion. At sixteen bytes the blob leaves **21 bytes unread** and yields **16 statistics set to zero**; at thirty-two it is consumed **exactly, to the byte**, with **no statistic zero**. A correct layout consumes its input.
- `parseStats` had a second fault of the same family: it walked *Sonar's own list of known statistics* rather than the bits. Any counter DECA adds that Sonar has not heard of would have gone unread, putting every figure after it out of step. It now reads one value per bit set, so an unknown statistic costs its own value and nothing else.
- The self-test's blob encoder wrote sixteen bytes too, which is why 297 passing assertions had nothing to say about it. It writes thirty-two now; a test that reproduces the bug proves nothing.

**A receipt, not just a claim**

- The game works fame on death out in its own client and never sends the answer while a character is alive — but it sends it in full the moment one dies: the total, and a line for every bonus with the tier it reached and the fame it added. **That packet was arriving and being thrown away.**
- `DeathPacket` is now read. Every death is kept alongside what Sonar had worked out for that character, so the page can state plainly how its arithmetic has done: *"Estimate · matched the game on all 3 deaths seen"*. Until a death has been watched it says *estimate* and nothing stronger.

**Collections, which is the part worth planning around**

- Thirteen named sets, **closest to finished first**, each showing what it is still missing by name and what finishing it would pay **this character, in fame** — not as a percentage of a number you then have to go and look up. *Tunnel Rat · 11/12 · needs Wine Cellar · +6,450.*
- Tunnel Rat is **twelve dungeons**, not the ten still named by the Flash-era pages that lead most searches, and it takes the whole castle with it: Oryx's Castle, Oryx's Chamber and the Wine Cellar.
- **Worth running next** ranks every dungeon by what one run is actually worth — the prize divided by the runs still needed for it. A run that is the last one missing from a set carries that whole set's reward, which is why a single Wine Cellar can be worth more than seventeen Shatters.
- Where the game keeps no completion count Sonar can read — the Neo wormholes and Stromwell's Rifts have no statistic in the client's own list — the page says so rather than showing a set as permanently unfinished.
- `Realm of the Mad God` is marked as approximate on purpose. Its description says every dungeon in the game while the list published beside it is a stale copy of another set, still naming Ice Cave two renames after it became the Ice Citadel.

**Where the counts come from**

- Every fame bonus is counted from the character's own packed statistics, which the game hands over at character select in `CreateSuccessPacket` — a field Sonar's own packet class described as `Unknown`. Those statistics are now **kept against the character**, so the page works for every character on the account rather than only the one being played, and survives a restart.

**The demo account stopped contradicting itself**

- The made-up account carried the fame of a fortnight's play and the kill counts of a thousand hours, because the two were typed in beside each other and had drifted. Base fame is now **derived from experience** by the game's own conversion — one per 900 before level 20, one per 2,000 after — so they cannot drift again, and the activity counts scale with experience rather than with how maxed the character is. Those are two different axes.

## 2.27.4

**The shared cards count per minute too**

- The exported results card and the MVP card still reported **damage per second** while the board they are a picture of had moved to per minute. Those cards are what actually gets posted when one run is held up against another, so they were the last place still speaking the old measure - and the one where the comparison is made.
- Both now read in **damage per minute**, in the same one-decimal notation the bars use: `242.4k dpm overall`.
- The per-enemy breakdown keeps its DPS column, and so does the player page. Those are the close-reading views, where a per-second figure is the finer instrument.

**Contrast colours named by the hues they actually are**

- The note explaining `classContrast` said "cyan behind a Samurai's red" from memory rather than from the wheel. A Samurai's bar is orange at hue 32 and its opposite is blue at 212; an Archer's green opposes rose, a Knight's blue opposes amber. Comment and changelog only - the colours themselves were always computed.

## 2.27.3

**The damage figure comes forward off the bar**

- A number drawn on a coloured bar needs an **edge of its own**, or the colour behind it eats the thin strokes of the glyphs. A drop shadow could not do that where a figure crossed from the dark track onto a bright bar - which is exactly where `450,412` sits on a Necromancer's magenta.
- Damage dealt is now drawn with a **hard black outline**, over a **bloom in the colour opposite that player's class**: blue behind a Samurai's orange, rose behind an Archer's green, amber behind a Knight's blue. Each row is separated from precisely the colour it is lying on, and at 5-9% opacity the bloom is felt rather than seen.
- The figure also moves up into the **same type as the rate beside it**, so the two are a pair and what distinguishes them is treatment rather than size: damage stands off the bar, the rate sits back against it.
- `SonarTheme.classContrast` is one rotation of the hue wheel from a class's own colour, at 42% saturation and full brightness. Every class gets its own opposite rather than one colour chosen to suit most of them.
- Both numbers being in the larger type, the second gives way a little sooner as the window narrows rather than reaching back over the name beside it.

## 2.27.2

**The rate stops shouting over the damage**

- Writing a rate per minute out in full made it **six digits and a unit** - the longest string on the bar, set in the heaviest type, beside the damage figure it is meant to accompany rather than drown. `242,402 dpm` read as the row; `1,054,448` read as a footnote to it.
- Rates on the bars are now **abbreviated to one decimal, the same way on every row**: `242.4k dpm`, `96.8k dpm`, `64.1k dpm`. One notation down the whole column, which is what 2.27.1 was for, at about half the width.
- The rate is also **softened to the theme's own text colour** rather than full white, so it sits back against the bar instead of standing off it, and the damage figure beside it is lifted to carry the row. Damage dealt keeps full white whenever it is the chosen measure.
- `SonarTheme.even` is the notation behind it: always one decimal, always abbreviated from a thousand. `compact` and `brief` each switch notation at a threshold, which is right for a number read on its own and wrong for a column read downward.

## 2.27.1

**One notation down the whole column**

- The bars abbreviated above a hundred thousand and not below it, which put **`242k dpm` three rows above `96,818 dpm`** - the same column in two notations, read down, with the eye asked to convert between them. Both numbers on a bar are now **written out in full**, so a column of rates compares at a glance and so does the column of totals beside it.
- Written-out figures take more room, so the second number gives way a little sooner as the window narrows, and the space it leaves is now measured rather than assumed from a constant that was written for abbreviated text.

## 2.27.0

**The live page counts per minute**

- **DPM and DPS have changed places.** The live board - the bars, the DPM/Total switch, Your stats and the current fight - now reports **damage per minute**, and **damage per second has moved to the player page**, where you land when you click somebody's bar.
- Per minute is the figure people actually quote at each other, and it is the steadier one: a per-second number moves under the eye with every shot that lands, while a minute is long enough for a reading to mean something. The live page is read at a glance mid-fight, so it gets the steady figure.
- Per second is the closer look, so it sits where one player is being read closely - `Overall dps` on their page, alongside their hits, their biggest hit and what each item of theirs did. The per-enemy breakdown keeps its DPS column for the same reason.
- They are the same number sixty apart, so nothing was gained or lost in the move - only put where it is used.

**Joining a dungeon partway opens on bosses too**

- 2.26.0 left a late join alone, on the grounds that its area is a guess. That was the wrong way round: the places that are **not** dungeons are a short, known list - the realm, the Nexus, the vault, the guild hall, the pet yard, the bazaars - so anything else is a dungeon, including an area whose name never arrived.
- This is also exactly the case the boss default exists for. Start the sniffer partway through a run and the boss is very often the only thing left to catch, so **Overall was the one tab guaranteed to be thin**.
- A run picked up partway never looks like a new instance to the board, because the game sends no map packet for it and the key it is tracked under stays null. The moment tracking starts now counts as that run's beginning, which is when the tab is chosen.

## 2.26.0

**A dungeon opens on boss damage**

- The damage board always opened on **Overall** - every hit of the instance, trash included. In a dungeon that is the wrong number to lead with: you entered it to fight its boss, and the boss total is what the run is actually about. The board had the right figure all along, one tab across, and nobody switched to it mid-fight.
- So the view a place opens on is now decided by the place: a **dungeon opens on Bosses**, and the **Realm, the Nexus, the vault and the guild hall open on Overall**. Setting a realm to boss damage would do the exact reverse of the point - there is plenty to fight out there and almost none of it is a boss.
- The test is the map's own name, using the same rule the dungeon-call pings already use, so the two can never disagree about what counts as a dungeon. The names it was checked against are the ones the game has actually been seen to send - *Realm of the Mad God*, *Packed Nexus*, *Guild Hall 4*, *Haunted Cemetery* - rather than names assumed.
- It applies when you enter a place, so **picking a tab by hand sticks** for the rest of that instance. A run picked up partway, where the game never sent the map, is left alone: its area is a guess, and guessing "dungeon" there would open an empty board.
- `sonar.damage.autotab=false` turns the whole thing off and restores the remembered tab.

**Damage per minute, beside damage per second**

- **DPM is the figure people actually trade** when they compare one O3 or Shatters pump against another, and it is what the older sniffers showed. Sonar knew it and never wrote it down.
- Your stats now reads **`18,204  ·  1.1M dpm`** on each of its rate rows - Overall, Enemy and Boss - and the same on another player's breakdown. It is the per-second figure times sixty, so it shares the line rather than taking one of its own, and nothing moved to make room.

## 2.25.0

**An enemy that was already on screen gives itself away when it shoots**

- The packet that says what an entity *is* arrives once, when the server introduces it. Start the sniffer after that and the entity stays type 0 forever - nameless, drawn as **"Enemy 0"** - because the tick stream that follows carries status by id and nothing else. In a run joined partway, that unidentifiable thing is very often the boss, because the boss is what was already on screen when you started.
- One real Nest run came out with fifteen enemies named correctly and a single *Enemy 0* holding **688,525 damage**: the Killer Bee Queen, missing from her own board.
- Every object in the game data carries its projectile table - each bullet's minimum and maximum damage - and `EnemyShootPacket` already carried the bullet's index and the damage it rolled. **Sonar had both halves and was throwing one away.** Running the table backwards turns a shot into the set of things that could have fired it; intersecting successive shots collapses that to one.
- It collapses fast. Measured against the real game data: **Oryx 3 and the Forgotten King are pinned by a single shot**, Treasurer Gemsbok and the Killer Bee Queen by two. Of the 122 enemies the game labels BOSS, the median is two shots and 96% of those that resolve do so within five.
- It never contradicts the server. An entity the game properly introduced is left exactly as it is; this is consulted only for a type that never arrived, and if the real packet turns up later it wins.
- **It never guesses.** A candidate set still holding two possibilities stays unresolved rather than picking the likelier one, because a board naming the wrong boss is worse than one admitting it does not know. Oryx 2 shares its projectile table with another object and so never resolves this way - the dungeon name is what tells those two apart.

**A run says when it is not the whole story**

- What survives identification - something that never shot, or whose projectiles are shared - is still kept out of every figure, so an unanswerable number is never presented as a fight.
- But the run now **counts what it dropped and says so**: the Instances list reads *"1 enemy not identified"* beside the run, and it is kept with the run, so a board reviewed days later still admits what is missing rather than looking complete because the thing it left out has no name to show.
- A run that recovers everything says nothing, because there is nothing to say. Most late-joined runs are now in that group.

## 2.24.1

**The demo stops putting a katana on a Wizard**

- The demo built the other end of each swap by borrowing **another player's kit**, so a Wizard was shown swapping into *Kusanagi* - a katana, which is a Samurai weapon and which no Wizard can equip. One wrong item tells a reader the whole screen is invented.
- Sonar already had the right rule and the swap code was not using it. `kitFor` picks the highest-tier item a class is *allowed*, by the game's own `SlotType` match - its comment even records that a hand-written kit once gave a Samurai a dagger. The other end of a swap now comes from `altFor`, the next-best item by that same rule, so **both ends are always legal for whoever made the swap**.
- Two self-test assertions hold it across all twenty classes in the demo roster: both ends of every demo swap are items that class can actually hold, and the two ends differ, or the swap would show nothing.

**Enchants stop running under the arrow**

- Four enchants are wider than the item name above them, so that line had been given the item tile's width as well. On the outgoing side that ran it under the arrow, and a row reading `Defense Bonus III->` looked like part of the swap.
- Only the incoming side has empty space to its right, so only it keeps the extra.

## 2.24.0

**Swap outs: what a player changed, and when**

- A swap is usually the most deliberate thing somebody does in a fight - a melee weapon for a chest phase, an armour change before a hit everyone knows is coming, a ring swapped for the one that keeps them alive through it. Sonar knew every one of them already: **every hit carries all four slots and their enchants at the moment it landed**. It threw them all away and kept a last-seen snapshot, which for anyone who swapped is the least interesting kit they wore.
- The **Players** page now lists them for whoever is picked in the roster: the slot, how far into the run it happened, what went out, what came in, and **the enchants on both sides**.
- **A change of enchants alone counts.** Two of the same item with different enchants is a real swap and reads as nothing at all if only names are compared, so those are listed and tagged `ENCHANTS` so the row is not mistaken for one that changed nothing.
- Over the whole run rather than one fight: a chest-phase weapon goes on before a boss and comes off after it, and scoping to one fight would cut the swap off at one end or the other.
- A slot the game had simply not sent yet is not a swap when it arrives, and the kit somebody started in is not a swap either.

**The Damage window leaves the Players page**

- It was a third copy of the same board. The Live page carries it, the Damage breakdown carries it with more in it, and this one carried it again with three switches of its own to keep in step. Swap outs sits there now.

**"Enemy 0" stops taking a share of the run**

- An enemy whose type never arrived shows up as *Enemy 0*, and it is not an enemy - it is damage Sonar could not place, which happens when the sniffer is started partway through a run and the packet introducing that entity is long gone. In one real run it was the **largest total on the board**, all of it unattributable.
- It is left out of the figures now. Mid-run starts still work and everything Sonar *can* identify in one is still counted; this is only about not presenting an unanswerable number as a fight.

## 2.23.0

**Sonar stops disagreeing with itself about one boss**

- The same player, the same fight, two figures: the shared board said **1,236,955** and the MVP card said **1,205,437**. The server said 1,202,859.
- The board was reporting the boss **plus every add folded into it**. Sonar groups an enemy whose fight overlaps a boss's into that boss, which is the right answer for a list of what happened in a run - *boss, wave, boss, wave* - and the wrong one for a board with the boss's name at the top of it. The MVP card reads the boss's own fight, which is why it was already within a fifth of a percent of the server.
- Anything that reports a fight **as that boss** now reads the boss's own fight: the shared picture, the single-fight picture and the Damage breakdown alike. The grouping still decides which fights get a section, so nothing scatters.

**No clock on a posted picture**

- The footer carried the date and the **local** time. The person posting a board is not always the person in it, and a wall-clock time pinned to a known server is enough to put somebody in a time zone.
- It is gone. Sonar is read-only about the game and ought to be read-only about the people playing it. The run's own length is still in the header, and that says nothing about where anybody lives.

**The accuracy check finally hears Oryx**

- Sonar compares its own figure against the server's own announcement at the end of a run, and keeps the result. It had two records in it. Neither was a Sanctuary run, which is the entire reason the check exists.
- The game has more than one wording. An ordinary boss gives *"X dealt the crucial blows with N (P%) damage!"*; Oryx gives *"X has vanquished the Exalted Mad God with N (P%) damage!"*. Only the first was ever matched, so every O3 kill went unmeasured while the file filled with easier fights.
- Both are read now, and the boss's own name is left open rather than listed, so a wording Sonar has not seen still parses as long as the shape holds.

**The share beside the breakdown says what it is**

- It shipped as a bare share icon, which put **two identical glyphs on one page doing different jobs**: this one, and the Run summary's, which posts every fight in the run. The first thing anyone did was press the one they already knew and get the whole dungeon back.
- It now reads **This fight**. An icon is not a name.

## 2.22.0

**Guarded hits are back on the board, as a column**

- Each player's guarded figures now sit in a **GUARDED HITS** column on the right of the exported board, past the damage, lined up down the picture. The run total that used to ride on the boss heading is gone: the column says the same thing and says who.
- The share of a bar that went into the guard is marked again, in the same deep oxblood, running from the **right end inwards**.
- The column's width is measured per fight rather than fixed. A fixed one cost every row ninety pixels of name on every fight in the game, guard or no guard, which left *Usern...* down a picture whose whole point is who did what.

**The class bar stops before the numbers**

- A bar used to run the full width of its row, so the top player's passed under their share, their damage and the guarded column - and every one of those figures had to be drawn in ink chosen for whatever class colour happened to be behind it.
- The bar now ends before the first digit. It may still run under the gear, whose tiles carry their own plates. Nothing else does, so the figures are drawn once, in one ink.

**One fight is one list**

- A single fight is listed straight down rather than split into columns. Splitting twenty players across two columns breaks the ranking in half for no reason.
- Anyone is moved to the side only once the picture has run away vertically - past **three and a half times** its own width. A column is 404 wide and a row costs 29, with 241 of header and footer around them, so a fight of N players runs `(29N + 241) / 404`: the old 2.6 split at 28 players, which is most of a Sanctuary run and exactly where a ranking is most worth reading whole. At 3.5 it holds to 40. The whole-run picture is unchanged.

**Guarded hits work again on Oryx 3**

- 2.21.0 removed the catch-all that counted any enemy flagged invulnerable, because it fired on trash all run long. That left O3's guard resting entirely on two animation hashes, and when those stopped matching, the figure read **zero on the one boss the feature exists for**. A number that fires everywhere says nothing; a number that fires nowhere says nothing and looks fine while doing it.
- The invulnerable bits are read again, but only on the bosses that actually have a phase like this - Oryx 3, the Forgotten King and Treasurer Gemsbok - and nowhere else. A Soldier Bee flickering invulnerable as it spawns is not on that list and never will be.
- `-Dsonar.guarddebug` prints every distinct animation value those bosses play and whether they were untouchable at the time, so the hashes can be repaired from a real run rather than from guesswork.

**One boss at a time, instead of the whole dungeon**

- The run's own share posts every fight in it, which is right when the run is the thing and wrong when the question is one boss. A **share button now sits beside Expand** on the Damage breakdown, and it posts whichever enemy is picked in the row above it.
- The picture is headed with that fight's own name and its own total - *ORYX THE MAD GOD 3 / 4,956,683* - and carries the full roster for that fight, nobody capped off the bottom, flowing into as many columns as it needs.
- With the picker on **Whole instance** there is no single fight to post, so the button says *Pick an enemy first* rather than quietly posting the run again under a different name.
- It shares the renderer with the whole-run version rather than being written twice: one fight is a list of fights with one in it.

## 2.21.1

**The update button stops showing mojibake**

- *Checking*, *Downloading*, *Installing*, *Restarting* and *Opening the installer* each ended in a stray `a-euro-broken-bar` rather than an ellipsis. On the one button whose whole job is to tell you it is safe to let a program replace itself, that looked exactly as dodgy as it sounds.
- The source had been saved once through the wrong code page, turning the single ellipsis character into the three characters that represent its UTF-8 bytes read as Windows-1252. The build was compiling that faithfully.
- All five are written as `…` now - an escape that is plain ASCII in the file and so cannot be mangled by any editor or code page again. **The whole Java source tree is now pure ASCII**, so there is nothing left of this kind to silently corrupt.

## 2.21.0

**The window stays where you put it**

- Sonar remembered how big its window was and never where it was, so every launch it went back to the middle of the primary display. On one monitor that is invisible. On two it means the window cannot be kept anywhere: move it to the second screen, close it, and it is back on the first - which reads as being stuck to that monitor, because nothing you do to it survives.
- It now remembers the position too, and opens where you left it.
- A saved position is only used while it still lands on a monitor that is **connected**. Unplug a screen, or change a resolution, and it goes back to centred rather than opening off in space with nothing to click. Same guard the Shatters overlay has always had.

**A Nest run stops being fourteen boss fights**

- A public Nest came out of the shared picture with fourteen sections: three Soldier Bees, three Maggot Eggs, four Beehemoth adds, the hives, the Enraged bees and the Loot Goblin, each given its own board as though it were a boss.
- The cause was the health heuristic. Sonar promotes an unlabelled enemy to a fight of its own when its health is five times the median of the trash around it - which exists for minibosses the game never labelled, and is right most of the time. In the Nest the trash baseline is set by **swarms of eight-pixel critter bees**, so anything with a real health bar cleared five times the median without being anything of the sort.
- **The game's own data was right and Sonar was not reading it.** Every one of those fourteen is labelled `MINION`, `CRITTER` or `BOSS_SPAWNED`; the Killer Bee Queen is labelled `BOSS` and the Beehemoths proper are `MINIBOSS`. An enemy the game calls somebody else's add is no longer promoted on health alone, however much of it there is.
- Nothing here can demote a real one: an enemy the game labels a boss still is one.

**Runs joined partway stop reporting fifty-six years inside**

- A shared picture from a run Sonar joined late read **`497813:22:23 inside`**.
- The instance's start time is claimed by the first tick, but only when it reads -1, and only `clear()` - which runs on a map load - set it to -1. A session that never saw a map load, because the sniffer was started partway through a run, never ran `clear()`. The field kept its default of **0**, so the first tick never claimed it and the elapsed time came out as the whole wall clock since 1970.
- It now starts at -1, so the first tick claims it whether a map load was seen or not.

## 2.20.0

**Guarded hits get a window of their own**

- The damage board was saying one fact three times: a deep red wash over the right end of every bar, the same figures again in red brackets after the damage, and two more columns besides. On a row already carrying nine numbers, over whatever class colour happened to be behind it.
- **The board is a damage board again.** No wash, no brackets, no guard columns - on the Live bars, in the Damage breakdown and in the shared picture alike.
- In their place, **Guarded hits**: a list of its own on the Instance page, ranked by damage into the guard, each row carrying the hits and what share of that player's damage it was. It hides itself on a fight with no guard to shoot, which is most of them.
- A **Copy** button puts the list on the clipboard as text. Text rather than a picture on purpose: this is the thing that gets quoted at somebody afterwards, and a short list pastes into a chat and stays readable.
- The shared picture keeps **one** line about it, on the boss heading - *302k INTO GUARD · 863 HITS* - so a posted board still says a guard was shot. Who did it is a question for the list.
- Clicking a name there goes to that player, the way the damage bars do.

**Guarded hits stop appearing in dungeons that have no guard**

- They were turning up everywhere. A plain thirty-five-enemy dungeon handed every player in it dozens of guarded hits, on a fight with no guard phase anywhere in it.
- The cause was a catch-all added alongside the real detection: any enemy flagged **invulnerable** or **invincible** counted, on the reasoning that untouchable is the same ask - stop shooting - without needing a list of every boss that does it. Ordinary enemies raise those bits constantly, for spawning in and for scripted beats of no consequence, so the figure fired on trash mobs all run long.
- It is gone. Sonar now counts only the two guards it can actually name: **Oryx's**, by the animation he plays, and the **Forgotten King's reflectors**. A figure that fires everywhere says nothing anywhere, and this is one that gets quoted at people afterwards.
- The other Sanctuary bosses with guard phases are not covered. Each needs its own animation hash read off a real fight, the way those two were.

**What the guard figures actually mean**

- Worth being plain about, because the community uses *guard* and *counter* for different things and Sonar can only see one of them. It detects three signals: Oryx playing his **guard animation**, the Forgotten King with **reflectors** up, and any boss flagged **invulnerable**. All three mean the same thing - the boss's defences were up and your shots were doing nothing.
- It cannot see the other mechanic, the one where too much damage inside an ordinary phase shifts the rotation. That is a threshold, and nothing in the packets names it.
- Of the Sanctuary's five bosses with these phases, Sonar knows **Oryx** specifically. The rest are caught only if they raise the invulnerable flag. **Chancellor Dammah is deliberately excluded** - his flag reads true for every hit until he counters once, so it says nothing about a phase.

**Sonar stops asking the game's servers for your character list**

- Sonar reads traffic that was going past anyway, which no server can see. One part of it did something different in kind: an **authenticated POST to the account website**, carrying your access token, made by a program that is not the game client, from the address your session is live on. That was the only thing about Sonar their servers could observe at all.
- There were three of those calls. The worst was ours: a **speculative request on every single launch**, which the comment beside it recorded as expected to be refused outside the Pet Yard and the Daily Quest Room, and treated as costing nothing. A refused authenticated request is not nothing - it is exactly what abuse detection counts - and on a day of updates it fired a dozen times.
- **The whole character-list request is gone**, all three call sites. The Characters page is built from the characters it watches you play, which it already recorded and already kept across restarts.

**Marking a character lost, by hand**

- Losing the list means losing the only hint the game ever gave that a character had died: it never says so against a character id, it just stops listing one. So open a character on the Characters page and there is a **Mark as lost** button at the foot of it.
- Nothing is deleted. The character keeps everything remembered about it, stays on the page with its `LOST` tag the way a character lost the old way always did, and the same button puts it back.
- This is also the more honest version. Sonar never knew whether a character had died or been deleted - only that it had gone.

**Exalt progress: once, then once a day**

- The second request, for your exaltation, used to fire **once per launch**. The thing it fetches changes about as often as you exalt a class, so it was asking a hundred times for an answer that had moved once.
- Now it is asked for **at most once every 24 hours**, remembered across restarts, so eight launches in an evening make one request rather than eight. Between readings it stays current from `ExaltationUpdatePacket`, which carries a class's whole progress as you earn it.
- It also moved **off the packet thread** and gained timeouts. An HTTPS request that hung used to hang packet handling with it.

**Your pet is recognised without asking anyone**

- Telling your pet's damage from everyone else's used to need the character list, because that is where the pet's instance id lived. It only worked after a Pet Yard or Daily Quest Room visit, so for most of most sessions no pet could be told from any other.
- A pet entity carries **its owner's account id**, and your own entity carries that same id. Comparing the two works everywhere, from the first hit, and needs no request at all.

**Sonar is findable from the Start menu**

- A program unpacked from a zip has no Start menu entry, so typing its name finds nothing - and the drive it was unpacked onto usually is not in the search index either. Sonar used to ask about that once, on first run, and record the answer **before** showing the dialog, so a refusal and a call that quietly failed were indistinguishable, and it never asked again.
- It is not a question any more. The Start menu entry is checked at **every start**: made when missing, repointed when the install has moved, rewritten when it predates the taskbar identity that lets a pinned Sonar and the running window be one button.
- The desktop is your own space: that one is made once and never resurrected. Delete it and it stays deleted. An install put here by the Windows installer has its own entry in its own group, which this finds and leaves alone rather than adding a duplicate.

**The roster keeps up with the room**

- Players arrive on the update packet and only the tick packet refreshed anything, so somebody walking into view waited for the next tick to be listed. In a crowd, where the server spreads arrivals over a run of update packets, the roster filled in visible steps behind the game. Both packets refresh it now.
- Snapshots used to be posted to the display one at a time. Drawing one costs more than building one, so in a crowd they arrived faster than they could be drawn and the queue grew - what you saw was however far behind the backlog had got. Only the newest snapshot waits now; the stale ones are dropped, which nobody wanted drawn anyway.
- `-Dsonar.timing` prints how long each snapshot took to build and to draw.
- Remembering how players look writes `players.json` from the packet thread. The write moved to a thread of its own.

**Clicking a player goes somewhere**

- The **Players here** list sits on the Live page, and nothing on that page shows a chosen player - their gear, their items and their profile are all on the Players page. So clicking somebody selected them correctly and changed nothing on screen, which is indistinguishable from a dead row. The damage bars have always gone to the Players page when clicked; the lists do the same now.

## 2.19.0

**Two columns gone, and the ones left have room**
- **ABSORBED** and **BLOCKED** are removed from the damage breakdown, along with the *armour guarded* figure in the summary strip above it.
- Absorbed was what a player's own armour took off a hit: the shot's raw damage minus what landed. Working that out needs Sonar to have seen the shot, and where it did not the two figures are equal, so the hit counted as nothing absorbed. It under-reported by however much happened off screen, and it answered a question nobody was asking.
- Blocked was only ever known for the user - the server does not announce a hit on somebody else that dealt nothing - so nineteen rows in twenty read as a dash.
- **TAKEN stays.** For everybody except you it is the server's own figure, announced outright with the hit; your own row is the exception, worked out from the shot that hit you and this client's idea of its defence.
- The 148 pixels those two columns held go back to the table, so **INTO GUARD** and **GUARD HITS** now appear at a window a third narrower than before, and TRACKING returns sooner too.

## 2.18.0

**Guard damage is always red**
- 2.17.0 drew a stray hit or two in grey, on the reasoning that it was not worth accusing anybody over. That was the wrong call: the figure exists to say damage went into the guard, and a stray hit is still a hit on the guard. Every guard figure is red now, in the breakdown, in the columns and on the player's own panel.
- What separates one hit from three hundred and fifty is the size of the number and the length of the red run on the bar. That is plenty - it does not need the colour as well.

**Outlined, so the red survives whatever is behind it**
- The guard figures are drawn with a black outline. On the top of the board the bar runs full width, which puts that line on the class colour and on the deep red within the same breath, and a drop shadow carried it on most surfaces and lost it on the bright end of a gradient.
- An outline holds on all of them. *(69k · 239 hits)* reads cleanly over tan, over oxblood and over the panel.

**Small things**
- Widen the breakdown and the guard figures stop being printed twice. The brackets after the damage are what the **INTO GUARD** and **GUARD HITS** columns become when there is no room for them, so the two no longer appear together.

## 2.17.0

**Words stop opening gaps inside themselves**
- *Usernam e2*. *total dam age*. *20 dealt dam age*. Text all over Sonar had been splitting words at arbitrary points, and it has been doing it for a long time.
- The cause, measured rather than guessed: Swing draws a label's text through its own helper, which takes the antialiasing and fractional-metrics hints from the look and feel rather than from the Graphics it is handed - so every custom pane setting them had no effect on a label. Without fractional metrics the engine rounds each glyph's advance to a whole pixel, and under the zoom transform that error piles up along a word until it lands as a space. Drawing the same string with the hint off and on, at zoom 1.0 and 1.5, reproduces and removes it exactly.
- Both hints are now set in the look and feel's defaults, so every piece of text in the app is measured the way the panes always intended.

**A stray guarded hit is not an accusation**
- Almost everybody finishes an Oryx run with a hit or two on the guard, however carefully they played: a shot already in flight when the guard goes up lands on the guard. A tracker that drew those in red beside a name would be accusing the whole party of blasting.
- So the figures go quiet below one percent of that player's damage. They are still reported exactly - *(626 · 2 hits)* - just in grey rather than red, and the shared pictures leave them off the rows entirely. Above that threshold nothing changes.
- *1 hits* now reads *1 hit*.

## 2.16.0

**The guard's red, settled**
- The red over a bar is now a **deep oxblood** rather than the bright pink 2.15.0 shipped. The red is laid *over* the class colour, so what you see is the two mixed - and Sonar's existing danger colour is already a pink-red, which over a Knight's blue came out flat pink. It reads as a stain now rather than a highlight.
- The figures moved. Instead of a badge beside the name, the damage into the guard and the **guarded hit count** sit in brackets after the damage, in red: *492,834  (384k · 977 hits)*. They stay in one column that way, and the name strip is left to the nexus tag.
- Widen the window and they become columns of their own, **INTO GUARD** and **GUARD HITS**.
- **The top of the board is the case this had to survive.** Their bar is full width, so the red run lands under the figures that sit on the bar's right end. Those figures now take the ink that suits the red where they cross it, and go to full strength on a row that has a red run at all - the quiet greys that read fine on a class colour washed out on a deep red.
- On a shared picture the name comes first: the badge drops the words, then the hit count, then itself, rather than squeezing a name down to *Us...*.

## 2.15.0

**The guard, on the bar**
- A player's damage bar now ends in **red for the share of it that went into the guard**, growing from the right end inwards - the same way the Live page already marks a pet's share in gold. Somebody who did nothing but shoot the guard has a bar that is red nearly all the way along, and you know it at a glance without reading a figure.
- On the Live page, on the damage breakdown, and on the pictures you share, so the three agree.
- Growing from the right was the right call: that is the end of the bar with the least on it, so most rows are untouched by it.
- **When it does reach under the text**, which is exactly what happens to a pure blaster, the name stays readable. The line is drawn in three runs rather than two, each clipped to one stretch of what is actually behind it - the class colour, then the red, then the panel - each in the ink that suits it. The red keeps its true proportion instead of being stopped short of the text, because the proportion is the thing being reported.

## 2.14.0

**Who shot the guard**
- Oryx's Sanctuary is built around a guard. Oryx raises it, and damage put into it does not hurt him - it fills the guard, and filling the guard is what pushes him into the next phase early, before the party is ready for it. The run wants that guard to come down on its own. Shooting it is the one thing everybody is asked not to do, and **blasting** is what it gets called when somebody does it anyway.
- Sonar now counts it. Every hit that lands on a boss holding a guard up is recorded as it happens, from the boss's own state, and shown against the player who dealt it.
- **On the damage breakdown**, a coral *69k GUARD* beside their name. Widen the window and it becomes a column of its own, **INTO GUARD**, sitting with the rest of the figures.
- **On the pictures you share**, the fight's heading carries *109k INTO GUARD* and each line says what that player put in - so the picture posted after a run answers the question without anybody having to ask it.
- **On a player's own panel**, the figure and the hit count: *Into the guard  ·  412,908 (1,204 hits)*.
- None of it appears on a fight that has no guard, which is nearly all of them. A column of zeroes down every Shatters run teaches nobody anything.
- Known by the boss's own animation for **Oryx 3** and the **Forgotten King's** reflector phase, and by the game's untouchable flag for anything else that raises one. It is read at the moment of the hit rather than worked out afterwards from gaps in a damage graph.
- The old **GUARDED** column, which is what a player's own armour absorbed, is now called **ABSORBED**. One word cannot mean both.

## 2.13.0

**The runs you have both been in**
- Click a player and their window now has a second tab: **Shared**, every run you and they were both in, newest first.
- It is the Instances list, filtered to one person. The same portal pictures, the same wording, the same time and party total and your own damage - because it answers the same question about the same runs, and learning to read the same list twice would be a waste of your attention. The Instances page itself is unchanged.
- Each row also says how it ended **for each of you**: *they nexus* / *you died*. That is the part a general list cannot tell you, and it is usually the reason you went looking.
- Clicking a run opens it with that player already picked out, so you land on them rather than hunting down the roster.
- Runs are kept for thirty days now rather than fourteen, matching the instance history, so the two lists cannot disagree about what happened.

**A posted picture worth zooming into**
- The pictures you copy to share a run - the roster, the per-boss page and the MVP card - are now drawn at three times the resolution.
- The reason is how they get read. A picture posted in a Discord is shown shrunk and looked at by zooming in, and that is exactly when somebody wants to see what they were wearing. At one to one, an item is an eight-pixel sprite stretched into a twenty-pixel box: two and a half source pixels to the box pixel, so some rows of the sprite come out two pixels tall and some three. Zoom in and it is mush.
- So the layout is unchanged and the drawing is not: text and panels are shapes and simply come out sharper, and the game's own art is now drawn at a **whole multiple of its own pixels**, centred in the space it was given. Every row of a sprite is the same height, and the enchant gem in the corner stays a gem.
- A very large run steps down from three times rather than producing a file too big to post.

**Small things**
- A window's heading no longer opens a gap inside a word - *20 dealt dam age*, *Usernam e1*. Swing draws a plain label by rounding each letter's width to a whole pixel, and at the sizes used here that rounding piles up until it lands as a space. Headings are now drawn the way every other piece of text in Sonar already was.

## 2.12.0

**What was true when somebody left**
- The NEXUS tag now carries their health as they went, and the boss's health at that moment: *NEXUS 02:30 · 86% hp · boss 71%*.
- Saying somebody nexused says nothing about whether that was reasonable. Leaving on four percent with the boss nearly dead is a different story from leaving at full health with most of the fight left, and only one of those is worth anybody's annoyance. Sonar cannot know **why** somebody left - that would mean predicting shots about to land - but it can say what was true when they did.
- The boss's figure is whatever largest thing the party had been hitting was still standing, which in a dungeon is the fight they were in the middle of. It says how close the run was to being over.
- A narrow window drops the boss first and the time last, keeping the health, which is the part worth having when only one fits.
- The tag reads on any class bar. It sits where the bar often already is, and a bar is a different colour for every class - so it is drawn the way names are, twice, clipped either side of where the bar ends, each half in the ink that suits what is behind it. On the bar that is a dark badge with the mark colour over it; off it, the quieter wash on the panel.

## 2.11.0

**Who nexused is written beside their name**
- A player who went before the final boss was finished now carries **NEXUS** next to their name, on the damage breakdown and in the image you copy to share a run. The breakdown also says how far in they went.
- This matters for reading a board fairly. Somebody who nexused at two minutes did not under-perform for the rest of the run - they were not there for it - and a list that does not say so reads as an accusation.
- A death is the one cause the game announces, so it is the only one named: that row says **DIED** instead. Everything else - nexused, disconnected, closed the game - arrives as the same packet and is called what a player would call it rather than guessed at.
- Only before the final boss is done. Someone who left during the victory lap did the run.

**Walking out of view no longer counts as leaving**
- A player leaving your view is recorded as a drop, and the record was never taken back when they returned - so anyone who rounded a corner was treated as having nexused. Found while building the label above, and it would have made the label wrong often.

**Nobody real is left in the test data**
- The sample rosters used to build and check these pages carried names that looked very much like they were taken from a real run. Five more files of them are now Username1 onwards. No real player should appear in a screenshot, a mock-up or a repository without having been asked.

## 2.10.0

**Sonar now checks itself against the game**
- At the end of Oryx's Sanctuary the server announces the top damage dealer with their damage and their share: *"Username1 dealt the crucial blows with 1,149,325 (18.6%) damage!"*. That is the only figure the game ever states outright, and it is the one thing that can tell Sonar whether it gave the credit to the right person.
- Every O3 run is now a free test. The comparison is made when the run finishes and kept, so accuracy is something you can look at rather than assume.
- **Copy accuracy** on the Run summary puts the record on the clipboard. It carries **no names at all** - whether the top dealer matched is a yes or no, and the rest is numbers. A record taken from a packet sniffer has no business carrying the username of somebody who never agreed to it.
- Nothing is uploaded. Sonar still sends nothing anywhere; sharing the record is a copy and a paste, by hand, if you choose to.

**When the top dealer has nexused, the game stops naming them**
- It says "a hero of the nexus" instead. Read as a name, that would have recorded a miss on exactly the runs where Sonar may well have been right.
- It is read as what it is: the server saying its top dealer left. The check then becomes whether the player Sonar put top had left too.

**Someone who walked out of view and back counted as having left**
- A player leaving your view is recorded as a drop, and the record was never taken back when they returned - so anyone who rounded a corner was treated as having nexused. A player who comes back plainly did not, and the record now goes when they do.

## 2.9.3

**The game can update itself again while Sonar is open**
- Sonar held the game's `resources.assets` open for as long as it was running. Windows will not let one program replace a file another has open, so on patch day the RotMG launcher failed with a sharing violation and told you to "remove the build manually" - an error pointing nowhere near the cause. Closing Sonar and clicking the same button worked, which is not something anyone would guess.
- Two handles were being left open: one taken to read the file's header and never closed at all, and one held for the whole extraction and never released afterwards. Both are closed now, the second as soon as the last pass over the file is done.
- Checked rather than assumed: with the fix, a forced re-extraction runs to completion and the assets file can be opened exclusively by another program while Sonar is still running.

## 2.9.2

**The version number in the rail opens the release notes**
- The notes an update shows went by once and were then unreachable. Clicking the version at the bottom of the rail brings them back: the last five releases, newest first, read from the changelog inside Sonar with no connection needed.
- It hangs off the version rather than taking a row of its own. The number is already what you look at when you wonder what you are running, and the rail is read during a run, where a changelog has no business taking up space beside the pages.

## 2.9.1

**An update now tells you what it changed**
- An update closes Sonar, swaps its files and opens it again, which left no sign of what it did. The version in the corner went up and everything else looked the same, so anything new landed only if you went looking for it.
- The first time a new version starts, its release notes appear once. Skipping versions is handled: somebody three releases behind gets all three, newest first, rather than only the one they happened to land on.
- The notes are read from the changelog inside Sonar itself, so they need no connection and always describe the build they are part of. A fresh install sees nothing - there is no previous version to have come from.

**The updater no longer trusts the working directory**
- It worked out where Sonar was installed from whatever directory the process happened to start in. Launched from a shortcut with the wrong "Start in", or from a script, that can be anywhere - and the updater would then hand the installer a folder with nothing to do with Sonar and unpack an update over it.
- Seen for real while testing this release: started with the working directory left at the user profile, it resolved `C:\Users` and tried to copy there. Windows refused on permissions, which is the only reason it did no damage.
- It now asks where its own code is first and falls back to the old guess only if that cannot be read.

## 2.9.0

**Pick a fight by its picture**
- The Damage breakdown now has a row of the run's enemies above it, each drawn as its own sprite, biggest health first. Click one and the whole roster is shown against that fight: who dealt what, how much of its health the party accounted for, and everything the breakdown already knew about each player.
- This is for the fights whose names are no use. The Shatters calls its fourth largest fight `Shatters A22` - a hundred thousand health, bigger than anything but the three named bosses - and every player who has run it knows her on sight as the Queen and by no other name. Among a hundred and sixty enemies that name finds nothing; the picture finds her at once.
- A gold dot marks a fight the game itself stages, so a boss is picked out from an add. A wave of ordinary enemies has no single face and gets a mark of its own. The caption underneath names whatever the pointer is over.
- Sharing a run as an image gives the boss numbers and only the boss numbers. This gives the same question a full answer, for any enemy, without leaving the page.
- The breakdown used to follow whatever the Live page was showing. It answers for itself now.

**Four more self-test assertions** cover the new row: what it leads with, that the biggest fight sits beside it, that picking one shows the roster against it, and that picking back returns the whole instance. 221 in total.

## 2.8.3

**The dungeon-call ping now hears guild chat**
- "j" is called out in guild chat at least as often as in party chat - a guild running keys calls them to the guild - and the ping only ever listened to party chat. That is why it could be switched on and still never fire. It now rings from either, and is still held back while you are already in a dungeon, where the call is for somebody else.

**The Players page keeps its own switches**
- Its damage window took DPS/Total and Everyone/Party from the Live page, so flipping a switch on one page quietly changed another. It owns all three of its switches now - what is counted, how it is measured and whose - and remembers them separately.

**The Analysis page stops leaving half of itself blank**
- Boss segments is three rows in a dungeon with three bosses and the Shatters panel is empty everywhere except the Shatters, so between them they left most of the page empty. Those two share the left column now and the leaderboard, which always has something in it, takes the full height of the right.

**The Wardrobe stops offering things that cannot be worn**
- Seventeen skins claim any class may wear them. Thirteen are not skins: they are the temporary shapes the game turns you into - Anubis, Bat, Ghost, Zombie, the boss transformations - plus one entry openly named a placeholder. They are no longer listed. The four real ones stay: Beefcake Santa, Baby Djinja, Calcium Skeleton and The Mistake.

**Window layouts reset once**
- The Analysis change moves windows that were already placed, so saved layouts are rebuilt from the new defaults. Anything you had arranged on other pages is set out afresh too.

## 2.8.2

**Updating an installed copy would have deleted your settings**
- The 2.8.1 installer put Sonar's data - settings, roster, characters, exaltation and every saved run - inside the folder it installs to. Windows removes that whole folder when a version is replaced, because a major upgrade is an uninstall followed by an install. Updating through the installer would therefore have taken all of it with it.
- Measured rather than assumed: a 2.8.1 install with those files written into it lost every one of them on uninstall.
- An installed copy now keeps its data in `%APPDATA%\Sonar`, where Windows has always meant settings to live, while the program stays in `%LOCALAPPDATA%\Sonar`. The installer owns one folder and never touches the other. Verified by installing, writing data, uninstalling, and finding the data still there.
- **The portable zip is unchanged** and still keeps everything in one folder beside `Sonar.exe`, which is the whole point of a portable copy.
- If you installed 2.8.1 today, nothing is lost yet - its data is in the install folder. Copy anything you want to keep out of `%LOCALAPPDATA%\Sonar` before installing this, because installing over it removes that folder.

**Check for updates now hands an installed copy to the installer**
- It downloaded the portable zip and unpacked it over whatever it found, including over an install Windows is keeping records of. Add or Remove Programs would then describe a version that was no longer on disk, and a later repair or uninstall would work from records matching nothing.
- An installed copy now downloads the installer and lets Windows do the upgrade, which is what the fixed upgrade code in 2.8.0 was for. A portable copy updates exactly as it always has.

## 2.8.1

**The 2.8.0 installer could hang forever, and does not any more**
- It created a shortcut on the Desktop. When Windows cannot write that shortcut it raises error 1909, and the installer then waits for an answer to an error dialog that never becomes answerable. The progress bar sits on "Creating shortcuts" and the only way out is Task Manager. Nothing is installed and nothing is cleaned up.
- A name already taken on the Desktop is enough to trigger it, and so is a Desktop redirected somewhere else - which OneDrive does for a great many people by default. This was not a rare corner.
- The installer no longer creates a desktop shortcut. The Start menu entry is written to a folder of its own and has never failed. Sonar still offers a desktop shortcut itself on first run, where refusing it is a message rather than a wedged installer.
- The wizard is one page shorter as a result, and a clean install now takes about five seconds.
- If 2.8.0 left you with a half-finished install: it registered nothing, so there is nothing in Add or Remove Programs to uninstall. Delete the folder it made, which is `%LOCALAPPDATA%\Sonar`, and run this installer.

**How this got out:** the installer was tested with `/qn`, which installs without showing any of the wizard. That exercised the file copying and missed the dialog sequence entirely. It is tested through the actual wizard now.

## 2.8.0

**There is a proper installer now**
- `Sonar-<version>-setup.msi` is the download for most people. It installs for your user only, which means Windows does not ask for administrator rights at any point, and the "unknown publisher" prompt that comes with that question never appears.
- Sonar shows up in Add or Remove Programs with its publisher, version and a link back to the project, gets a Start menu entry, and offers a desktop shortcut. Uninstalling removes it completely.
- Installing a newer version replaces the old one instead of leaving two side by side.
- The portable zip has not gone anywhere. Unpack it and run `Sonar.exe` if you would rather not install anything.

**Releases are built in public, and you can prove it**
- Every release is now built by a GitHub Actions workflow you can read, rather than on a laptop. Each file carries a signed provenance attestation tying it to the exact commit it came from, so anyone can check the download really is built from this source before running it: `gh attestation verify Sonar-<version>-setup.msi --repo JustAutomate-AI/sonar-rotmg`.
- `SHA256SUMS-<version>.txt` ships with every release as well.
- This matters more than it sounds. Sonar reads network traffic, and until now nothing connected the source anyone could read to the binary they downloaded. Now something does.

**The README answers the two questions people actually have**
- Whether it can get you banned, and what leaves your machine. The short answers: Sonar never sends anything to the game, and nothing leaves your computer except one check against GitHub for a newer version.
- It also explains the Windows warning in advance, why an unsigned program gets one, and how to verify the file rather than take anyone's word for it.

**The old name is gone**
- The project was called Peach before it was called Sonar. The leftover `Peach-<version>-windows.zip` download has been removed from all ten releases that carried it, along with the notes pointing at it, and the repository description still said Peach as well.
- The accent colour named Peach is now called Salmon. If you had it chosen, you keep it.
- A few internal names went with it. The one deliberate exception is the line that renames `peach.*` settings keys to `sonar.*`, which has to keep the old spelling to recognise them.

## 2.7.4

**Every ping sound was playing the same file**
- The enchant, dungeon, item, entity and realm-closing pings are meant to be told apart by ear. All five were falling back to the same default, so there was no hearing the difference between a white bag dropping and the realm closing.
- The sounds they are set to are full paths - the Windows ones live in C:\Windows\Media - and those were being tacked onto Sonar's own folder, which produced a path to nowhere. A second fault on the same line opened the name it was given rather than the file it had just found, which broke custom sounds dropped into the data folder as well.
- Both are fixed, so each ping plays what it is set to.

**The Players page can show boss damage on its own**
- The damage window there now has an Overall and Bosses switch of its own, starting on Overall. It no longer follows whatever the Live page happens to be looking at, because that page is read while picking through the roster.
- Bosses means the dungeon's own boss. Minibosses are left out, so in Oryx's Sanctuary it is Oryx rather than Oryx plus the Archbishop plus the Treasurer.
- It is worth switching to: someone who padded on trash drops down the list, and someone who turned up for the boss climbs it.

**Names and ranks no longer vanish where the bar ends on the Instance page**
- The same fault fixed on the shared image in 2.7.3 was still on screen. A class name that crossed the end of its bar was drawn all the way across in ink meant for the bar, so the part past it disappeared against the panel. Rank numbers were nearly invisible for the same reason.
- Both are now drawn in the ink that suits what is actually behind them, and the method is shared with the image rather than written out a second time.

**The leaderboard bar ran through the class name**
- On the Analysis page the share indicator sat on the class label's baseline and cut through the tails of g, y and j. It now sits clear below them.

**Characters wearing patterned cloth came out striped**
- The pattern was being repeated once per screen pixel rather than once per pixel of the character, so it tiled five times too often and shredded the figure with thin vertical bars. Only the large portrait beside your gear showed it; in the small ones the striping was finer than a pixel.

**A failed exalt lookup could stop the sniffer reading packets**
- Exalt progress is fetched from the website rather than the game, so it can fail when nothing is wrong with the run - no connection, or their server down. That failure was being thrown onto the packet thread with nothing to catch it. It is now noted and stepped over, and the next time a token arrives it tries again.

**The Shatters panel finishes its sentence**
- Its "nothing found yet" line was longer than the panel and broke off mid-sentence.

## 2.7.3

**Names on the shared image vanished where the bar ran out**
- A name was drawn in one ink, chosen from whichever surface its first letter sat on. A name that started on the class-coloured bar and ran off the end was therefore drawn all the way across in ink meant for the bar - dark ink over a bright colour - and the part hanging past it, now over the dark panel, disappeared.
- It only showed on the players with the least damage, because theirs are the shortest bars, and only on names long enough to reach past them.
- Each line is now drawn twice, clipped either side of where the bar ends, each half in ink chosen for what is actually behind it. The damage figures beside them had the same fault and the same fix.

## 2.7.2

**The Fame tab is gone**
- It showed a sum Sonar worked out itself beside a warning that the sum was being read in the wrong order, which is not something worth keeping on screen. The page and the code behind it have been removed. Fame still appears where it is actually read from the game: on the Characters page, against each character.

**The glow behind the big figures is much quieter**
- It was still drawing a visible halo. It now reaches three pixels instead of four and at roughly a third of the strength, so the figure is lifted off the panel rather than wrapped in light.

**The roster says how many it has yet to name**
- Players arrive as entities before their names do, and one without a name cannot be listed. Where that happens the Live page now reads "30 of 107 here" rather than just "30 players", so a roster that is still filling looks like one that is still filling.

## 2.7.1

**An update installed itself next to the application instead of over it**
- There are two layouts and they sit a level apart. A portable copy runs from a `run` folder with Sonar.exe one above it; a packaged install runs from the folder that holds Sonar.exe itself. The updater took the parent either way, which is right for the first and wrong for the second.
- On a packaged install that meant an update unpacked the whole application into whatever folder the install happened to sit inside - a Desktop, a Downloads folder - and left the install itself untouched on the old version. The copy that started afterwards was the loose one, with none of your settings, characters or extracted assets beside it, so it opened with nothing in it.
- The folder is now found by looking for Sonar.exe rather than by counting levels, and the self-test checks it.
- Only 2.7.0 could reach this, because every build before it could not download an update at all.

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
- The old look is kept exactly as it was: choose Plum for the ground and Salmon for the accent. Nothing was removed.
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

> Versions below 1.64.0 shipped under the name **Peach**, and 1.64.0 to 1.71.0 during the
> changeover. The entries have been written in the current name; file names from that time
> are left as they were, because that is what those releases actually contained.

## 1.71.0

**The rename is finished everywhere, not just on screen**
- 1.64.0 changed the name and the icon. Everything underneath still said Sonar: the program file, the folder it installs into, the zip you download, the settings it saves. That is all Sonar now — `Sonar.exe`, `Sonar.jar`, `Sonar-<version>-windows.zip`, and the install folder.
- Your saved settings move with it. The window layout, the theme, the accent, the starred party, the wardrobe and the Shatters overlay's position are all stored under a name that has changed, so the app renames them for you the first time it opens. Nothing resets and nothing is left behind, and it only does this once.
- A few internal names stayed behind at the time, which 2.8.0 finished off. All that remains is the one line that renames old settings keys, which has to keep the old spelling to recognise them.

**Midnight is what a fresh install starts on**
- The deep navy is the ground the Sonar mark was drawn against, so it is what the app opens with now instead of the old plum. A theme already picked in Settings is kept exactly as it is — this only changes what a new install looks like on its first run.

**This one update has to be done by hand**
- Older versions look for a file called `Peach-1.71.0-windows.zip`, and this release is called `Sonar-1.71.0-windows.zip`, so **Check for updates** cannot fetch it. Download the zip below, unpack it to its own folder and run `Setup.bat`. Updating from inside the app works normally again after this.

---

209 self-test assertions pass.

## 1.70.0

**The Graveyard tab is gone; the Characters page says it instead**
- A whole page for something that happens a few times a month was the wrong shape. A character the account has stopped listing now stays on the Characters page, sorted to the bottom, faded, tagged **LOST**, with how long ago it went: *"Rogue - Level 20, 3,570 fame, lost 2 hours ago"*. It keeps its gear, stats and pet, so the card still opens.
- It says "lost" rather than "died" on purpose. The account list looks identical whether a character died or was deleted, and Sonar cannot tell those apart.

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
- Scrolling until it looks right was guesswork. Sonar knows exactly how many tiles away each mark is; what it cannot know is how many pixels the minimap gives a tile. **Right-click the game's own icon** for something and it has both halves - pixels clicked divided by tiles away - and sets the scale exactly, in one go. The mark used is whichever lies nearest the direction clicked, and a click nowhere near one is ignored rather than taken as a measurement.

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
- It was calibrated at one zoom and stayed there, so zooming the minimap left every mark at the wrong distance. The minimap's zoom level is stored nowhere Sonar can read - it is in no packet and no game file, and the registry keeps only the camera's - so it cannot be followed directly. The key bindings can be read, though, and that is enough.
- Sonar now watches **the same two keys the game does**, read from Exalt's own settings. Press minimap zoom and both step together. There is no second shortcut to learn, and rebinding the keys in game moves Sonar with them.
- Each zoom step keeps **its own calibration**. Zooming does not move the minimap or resize it, only how many tiles fit, so the overlay never needs re-aiming - only re-scaling. Calibrate a step once and stepping back to it restores an exact figure instead of a guess. The calibration readout says which step is being set.
- Ctrl+shift+plus and ctrl+shift+minus step the overlay alone, for nudging the two back into line. One known cost: the game's binding is a bare key, so typing it into chat steps the overlay too - one press puts it back.

---

195 self-test assertions pass. Update from inside the app with **Check for updates**, or download the zip below.

## 1.66.0

**The Shatters overlay can be tested in a realm**
- The dungeon is rare, so whether the overlay was lined up could only be answered by getting into one. **Portals: on** switches it to marking portals and nearby enemies instead, anywhere at all - so it is answerable in a realm, or the Nexus, in ten seconds.
- Two references, because they answer different questions. **Portals** never move, and the game draws each on the minimap, so they settle position and scale: if Sonar's mint ring sits on the game's portal icon, the overlay is right. **Enemies** always move - a realm god wanders constantly - so the coral dots settle whether a mark actually follows something, rather than only being right while everything stands still.
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
- The name and the icon, and nothing else. The palette, the layout, the wording and every page are exactly as they were - "Sniffer live" still says sniffer, and the accent colour kept its old name at the time, though 2.8.0 renamed it to Salmon.
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

184 self-test assertions pass. Update from inside Sonar with **Check for updates**, or download the zip below.

## 1.63.0

**The Shatters overlay starts over the minimap**
- It opened at 100,100 - the top left, which is the one corner of the screen the game never puts the minimap in. So the first thing anyone saw was a circle at the opposite side of the screen from the thing it is meant to cover. It now opens in the top right at about a third of the screen's height, square, which is roughly where and what size the minimap is. The same position is used when a saved one turns out to be off-screen.
- **Recentre** sits beside Calibrate. A position saved before this is still saved, so a better default would never have reached anyone already using the overlay; this puts it back over the minimap, resets the scale and drops straight into calibration.

---

182 self-test assertions pass. Update from inside Sonar with **Check for updates**, or download the zip below.

## 1.62.0

**One header, not two**
- Sonar sat under a grey system title bar carrying the name and buttons a second time, in a palette that matched nothing below it. The window is borderless now and carries its own minimise, maximise and close, so there is one header and it is Sonar's. Dragging the header moves the window, double-clicking it maximises, and every edge and corner resizes. `-Dpeach.borderless=false` puts the system bar back.
- The buttons along the header lost their outlines. Nothing is drawn behind them until the pointer is on one, so a row of them reads as a row of words rather than a row of boxes.

**A "Near me" tab on the Players list**
- A max-pop realm lists well over a hundred names, which is no use when what you want is who is actually with you. The tab cuts the list to everyone within 22 tiles - about a screen - and the count reads "12 within 22 tiles" instead of the usual seen-and-dealt line.
- Someone whose position has not arrived is not known to be near, so they drop out rather than being assumed close.

**The shared image's headline counts what is on the page**
- A boss page reported the whole run - every scrap of trash between the fights - so a Plagued Nest showing 3.1M against the Killer Bee Queen was headed **9,041,448**. It now reads **BOSS DAMAGE** and sums only the fights on the page.

---

180 self-test assertions pass. Update from inside Sonar with **Check for updates**, or download the zip below.

## 1.61.0

**The marks are the game's own sprites**
- The Void Phantasm is drawn as the Void Phantasm, the Stone Idol as the Stone Idol, and so on, cut from the game's own sheets at the frame each one stands still on. The coloured ring stays around it, carrying how much the thing matters; the sprite inside says what it is, so there is no symbol legend to learn.
- The Derelict Monument needed a second route to its texture - the id table has no entry for it, so the Shatters data is read directly for anything the table cannot answer.

**Objects the game draws almost black fall back to a mark**
- The nine void cracks are cracks in a floor, drawn nearly black on purpose, and on a dark minimap they came out as nothing at all. Rather than keep a hand-written list of which sprites are too dark - which would go stale the first time one changed - the pixels are counted, and a sprite needs a twentieth of itself to be solid and brighter than the dark around it. The cracks measure **0-1%** by that rule and everything else **20-45%**, so it separates them without a judgement call. Anything that fails it keeps its drawn diamond.

---

175 self-test assertions pass. Update from inside Sonar with **Check for updates**, or download the zip below.

## 1.60.0

**The Void Phantasm is on the minimap**
- It sits in the first room, and the game flags it `NoMiniMap` - it is deliberately hidden, which is the whole reason finding it is the puzzle. Sonar already had it in the entity list; only the minimap had been told not to draw it. It is now the largest mark on the overlay, a coral ringed cross, and it leads the sidebar.
- **Eighteen Shatters objects are marked**, not three: the Phantasm, the Stone Idol, the Derelict Monument and the Source as full-strength objectives; the nine void cracks, the void crystal and the Void Tendril as faint violet diamonds; and the three invisible logic objects as bare ticks, in case they ever reach the client at all.
- The Source's four generators are gone. They were never what answers "where is it" - the Phantasm is.

**Every watched object is verified by the name the game actually uses**
- These are matched on the internal name, not the display name, and the difference is not cosmetic: most of them have no display name at all, and the two that do are not named what you would guess - the Derelict Monument is a "Village Switch" internally, and the Source is an "Experimental Generator". A check against display names would have found almost none of them.
- All eighteen ids are confirmed against the game's own data on startup and repaired if one has drifted, with anything that cannot be found named in the sidebar rather than silently never appearing.

---

173 self-test assertions pass. Update from inside Sonar with **Check for updates**, or download the zip below.

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

172 self-test assertions pass. Update from inside Sonar with **Check for updates**, or download the zip below.

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
- The footer keeps the date and time of the run and drops the "Generated by Sonar".

## 1.55.0

**The shared image stops leaving people out**
- It showed sixteen names a fight and counted the rest as "+ 7 more". Everyone who hit the boss is on the page now.
- Raising the limit alone would have made it worse: a run with one boss and two dozen players was a single block, and a block could not be split, so the page could only grow downwards into a strip. A fight too long for one column now carries on at the top of the next under its own name again, marked "continued".
- A twenty-three player Plagued Nest comes out 822 by 618 with every name on it, where before it was sixteen names and a footnote. Forty players is 822 by 850; eighty is 822 by 1140.

## 1.54.0

**Every stat was being shown as maxed**
- The Gear panel painted all eight totals gold whether they were at their ceiling or not, so a Rogue six short on speed and one short on vitality read as finished in all eight. Nothing was being compared to anything: the colour was simply always gold.
- A stat is gold now only when what it is worth on its own - the total with the equipment taken back off - has reached the class ceiling, raised by whatever that character has exalted. Everything else is plain, and carries the shortfall the way the game writes it: "72|6" is six points short.
- Checked against Geksa's own panel: speed 72 with 13 from gear is 59 against a ceiling of 65, so six short; vitality 56 with 17 from gear is 39 against 40, so one short; the other six really are at their ceilings.

## 1.53.0

**Spider Den and Spectral Penitentiary were paying each other's reward**
- The per-dungeon completion counts were gathered into a list in roughly alphabetical order, and the rewards are looked up by a list in the game's own order. The two were almost the same - which is why this hid - but not quite: those two sat the wrong way round, so an easy low-level dungeon was rewarded as a hard one and the hard one as the easy one.
- The counts are gathered by name now, so they cannot drift again whatever order the fields happen to be declared in. Checked in the self-test by putting completions into one dungeon and insisting they turn up under that dungeon and nowhere else.
- The character statistics are per character, not account-wide: each one's figures are read from inside its own entry in the character list. That was worth confirming while looking at this.

## 1.52.0

**The character statistics were being read in the wrong order**
- This is the cause of the Fame page's nonsense, and of everything built on it. The statistics arrive one after another in ascending stat id, each one present only if its bit is set. Sonar read them in a hand-written order that was not that order: from the ninth value on, **93 of the 123 reads took the wrong stat's number**, and every read after the first mismatch was shifted as well. That is where "677,545 dungeon completions" came from, and with it a fame on death of 333,714 for a character the game valued at 6,490.
- They are read in the order they were written now. That needs no table of offsets and cannot drift: a statistic Sonar has no field for is still read and only then discarded, so the ones behind it stay aligned.
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
- A shot that only fires while some condition holds - Lethal Strike's two extra projectiles in the moment after sneak - is a burst, not a rate. Counted at its own cooldown it came to a hundred damage a second for a whole run. Those are listed and left out of the total, marked "ability not counted", rather than given a number Sonar made up.
- The damage tracker has its own reader of the same scaling data, and the two are now held against each other in the self-test, because two readers of one file that disagree would mean one of them is wrong.

## 1.50.0

**Zoom**
- Everything can be drawn larger or smaller, from 80% to 200%, in steps of ten. Hold Ctrl and scroll over any page, or use the new control in Settings. It is remembered between sessions.
- It is a real zoom rather than a bigger font: every page paints at its natural size and is scaled on the way out, so bars, portraits, gear, sprites and text all grow together and nothing has to be re-laid out. The pointer is divided back into the same space, so what you click is what you are looking at.

**The windows stay put**
- No more moving, no more resizing. The arrangement a page gives its windows is the arrangement it keeps, which is one you can learn - and a layout nudged half an inch by a stray click was never worth putting back. What used to be done by dragging a window bigger is done by the zoom, which grows every page at once.

## 1.49.0

**The game's own enchant rarity marks**
- One green diamond for Uncommon, two blue for Rare, three violet for Legendary, four gold for Divine - the game's own 16x16 sprites, in the bottom-right corner of every gear icon Sonar draws, with the glow already in that rarity's colour.
- They were not in anything Sonar extracts. The game keeps them in a 4096x2048 GUI atlas whose pixels live in a streamed sidecar file, and the code that would have read it was commented out, unfinished, in the library Sonar inherited. The atlas also carries a table of 658 named sprites, which is where the four turned out to be: RarityIcon_1 through RarityIcon_4. They ship with Sonar now rather than being hunted for at startup.
- Hovering an item still lists each enchant with its own symbol, as it did; the corner now says the rarity rather than repeating the first enchant.

## 1.48.0

**The shared image is readable now**
- It was one long column: a Shatters run came out six times taller than it was wide, a strip nobody could get through. The fights are measured first, split across however many columns bring the picture closest to square, and read down one column then the next. The same run is now about as wide as it is tall.
- Each player is one line at half the height, so a whole fight fits without the page running away: rank, how they look in game, their name, their gear and their damage with its share of the enemy's health beside it.
- Sixteen players a fight rather than twenty-four, with the rest counted underneath.

## 1.47.0

**Bosses were never sorted from minibosses at all**
- The fights are merged by name before the list is built, and the merged copies were not carrying what the game calls each one. Every anchor came out `boss = false`, so every fight of every run - the Forgotten King included - was filed under MINIBOSSES and the BOSSES heading never appeared. The label comes across now.
- An enemy Sonar promoted only for having an outlying health pool is no longer called a miniboss. The game labels its staged fights BOSS and MINIBOSS; obelisks, generators, tablets and idols are labelled neither, and they now sit under their own TOUGH ENEMIES heading instead of among the real ones.

**The shared image is the dungeon's bosses, and nothing else**
- It had a section for every fight, including every generator and obelisk - a Shatters run ran to twelve sections and was unreadable. It is now only the fights the game itself stages, in the order they were fought: for the Shatters that is three, the Bridge Sentinel, the Twilight Archmage and the Forgotten King.

**The calculator reads the enchants you are wearing**
- It only ever recognised enchants that change damage per second, which is right when picking one and wrong when showing what is already on. Anything else - an armour's Defense Bonus, a ring's Life Bonus - was dropped, so a fully enchanted character came up bare. Everything worn is listed now; what it adds to the damage is still nothing unless it really does.

**Gear marks**
- The enchant mark sits in the corner of the item rather than the corner of the tile, where it was half again too big.
- The rarity glow is a halo around the item rather than a wash underneath it. Filled, its innermost ring covered the item's own plate, so a divine enchant turned the whole tile gold.

**Fame on death, honestly**
- 1.46.0 claimed to take this from the game. It does not: fame on death is not among the stats the game sends, and the stat tried read 52,909 on a character the game valued at 6,490. The claim is withdrawn. The client works the figure out itself from the same packed statistics Sonar still misreads - the page says so, and getting that decoding right is the fix.

## 1.46.0

**The Exaltation page kept almost nothing**
- The account's own exaltation only arrives with a character list, and the game answers one in the Pet Yard and the Daily Quest Room and nowhere else. Sonar held it in memory alone, so every session it started again from nothing and the page read "1 of 19 started" for an account with eighteen. It is written down now and put back at startup, and the page says which day the figures are from and that the Pet Yard brings them up to date.
- Progress earned in front of Sonar was also being thrown away: the update packet was ignored for any class not already known, which on a session without a Pet Yard visit was every class. It is taken as it comes now.
- A character carries what exaltation *adds* to a stat, not the level, and life and mana go up five at a time. Read straight across, a Rogue with the usual +10 life showed "L5 max" at level two and added up to 7/40 when the truth was 4.

**A page could be closed and left blank**
- Every single-window page - Exaltation, Wardrobe, Vault, Characters, Collection, Party finder, Fame, Puzzle - could be emptied by one click on the window's cross, leaving a page with nothing on it and no sign of how to get it back. The cross is no longer offered where there is only one window, and any page already left blank comes back the next time Sonar starts.

**Fame on death is the game's figure now**
- The game does this sum itself, bonuses and all, and sends the answer. Sonar was adding the bonuses up on its own and reaching the hundreds of thousands for a character the game valued at six thousand. Wherever a character has been played in front of Sonar, its own figure is what the page shows; Sonar's working stays underneath as the explanation, and the two are shown side by side when they disagree.

**Enchants, everywhere gear is drawn**
- The mark in the corner of an item is now the game's own symbol for the *best* enchant on it rather than whichever happened to be listed first, and that symbol carries its rarity the way the game draws it.
- An enchanted item glows in the colour of its rarity on every page that shows gear - the damage bars, the breakdown, the gear panel and the shared image - where before only the damage bars did.
- The full list of enchants, each with its own symbol and coloured by rarity, appears on hover and only on hover.
- Rarity is read from the game's own files rather than guessed from the numeral on the end of the name, so enchants that are not numbered are no longer treated as having no rarity at all.

**Demo data**
- The demo's enchants were invented names the game has never heard of, which meant nothing to look at and nothing to check. They are the game's own now.

## 1.45.0

**The Wardrobe**
- A page of its own for how Sonar pictures each class. Until now it drew whichever character of that class you last played, which is a sensible default and a poor preference - the one you happened to log in on is rarely the one you think of the class as.
- Every class down the left, as Sonar now draws it. Pick one and change any part of it: its skin, its clothing, its accessory. Everything the game has is offered - every skin that class can wear, all 143 dyes and all 256 cloths for each part, patterns drawn as the game itself lays them on. Nothing is a short list of favourites.
- A look takes effect the moment it is clicked; there is nothing to apply. Hovering anything says what it is.
- **Outfits.** Save what a class has on and it becomes a chip to switch back to. Outfits are named after the skin they lead with, and a second outfit on the same skin takes the name of its colour instead, so two Bandit Rogues read "Bandit Rogue" and "Lush Camo" rather than "Bandit Rogue" and "Bandit Rogue 2".
- **Putting it all back.** Undress one class, or every class at once. Either leaves the saved outfits where they are, so nothing is lost by changing your mind.
- **One colour, every class.** Whatever the class you are on is dyed can be put on all nineteen in a click.
- None of this reaches the game. It is how Sonar draws, kept in Sonar's own settings; no character is touched and nothing is sent.

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
- Sonar cannot join one for you. The stream from the client to the server is RC4 encrypted and stateful, the game client owns that state, and anything Sonar injected into it would desync the cipher and disconnect you mid-run. Listening only is staying true.
- What it can do is take the time out of the step that actually costs it. Clicking a listing copies its name, ready to paste into the game's own filter box - the real Join button is then one click away, and the row says so rather than leaving it to be discovered.
- Parties can be starred. A watched one is kept by the name it runs under rather than its id, since an id lasts one party while the people who run the same thing every night tend to call it the same thing every night. Watched parties sort to the top however the list is otherwise ordered, are marked in gold wherever they turn up, and have a filter of their own.

## 1.42.0

**The Party finder was never going to work**
- A packet only reaches Sonar once it has been asked for by name, and the two party packets never were - so the page could not have filled however long the finder was left open. Both are now asked for, and the listings land as they are browsed.

**Faces in the lists**
- The Players and Party lists draw each person as they look in game, skin and dyes, rather than leaving a name to do all the work. Recognising someone from the list is now the same as recognising them on screen.

**The enchant gems are the game's own**
- They were a shape drawn here, and they looked it - too large, and nothing like the real thing. The game keeps a picture for every one of its enchantments; Sonar now cuts the right one out of that sheet and draws it at the proportion the game itself uses. 925 of them are matched. An enchant Sonar does not recognise draws nothing rather than the wrong gem.

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
- Sonar only ever listens, so this fills as the finder is browsed in game rather than refreshing itself. The page says so plainly rather than letting an empty list look like a fault.

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

**Updating looks like part of Sonar**
- The grey Windows panel that appeared while the files were swapped is gone, replaced by one in Sonar's own colours with no system chrome. It said very little and looked like something had gone wrong.
- The update prompt itself is now a Sonar dialog rather than a system one, with the same buttons used everywhere else in the app.

**The shared image**
- Class names and percentages were too small and too dim to read against a bright bar; both are heavier now.
- How each player looks is filled in from what Sonar remembers of them before the image is drawn, so figures are no longer blank in a shared picture or a run being reviewed.

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
- Lesser bosses are kept apart from headline ones, using the game's own MINIBOSS label rather than a guess. Anything Sonar promoted only for having an outsized health pool is counted as a lesser boss too, since calling an unlabelled tanky enemy a boss outright would overstate it.

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
- Hovering a weapon, ability, armor or ring beside a player's name now shows a note, which it never did before. The look and feel Sonar is built on quietly replaces every tooltip with its own, and that one never came up over the custom painted pages. Sonar now owns the note outright: it decides when it opens, where it sits and what it looks like, so nothing outside Sonar can take it away. It also keeps to whichever screen the pointer is on rather than being pushed onto another.
- The note says what the item is and what it does, not just its name: its tier or whether it is untiered or set, the damage its shot deals and how many it fires, its rate of fire when that is not the usual one, what it costs in mana, and anything it adds to the wearer's stats. All of it read from the game's own data rather than written down by hand. Underneath, each enchant on the item with the game's wording for what that enchant changes.

**Stats no longer claim to be maxed when they are not**
- A character wearing anything at all read as maxed in every stat, because what the game sends already includes what equipment is lending. Sonar was comparing that against the class base maximum, so a Rogue at 959 life showed "959/750" and "8/8 maxed". Maxing is now judged on the stat itself, with what the gear adds shown separately in green beside it, so the two add up to the figure the game shows.
- Exaltation raises the ceiling as well as the stat, which Sonar was not allowing for either: an exalted character could pass the class base without having maxed anything. Each stat is now measured against the ceiling that character actually has.
- The fame page counted those same stats as maxed, and no longer does.

**Hover a stat to see what is making it up**
- On the Characters page, hovering a stat that something is adding to now opens a note listing each worn item that lends it, with its picture and how much it gives, then exaltation on its own line. What the items account for is read from the game's own data; whatever is left over is named as enchants and other sources rather than being quietly folded in or guessed at.

**The fame page says when not to trust it**
- The reward table behind it is verified, but the counts fed into it are read out of a packed run of bits whose layout Sonar has not got right yet - some come out far too large. Rather than let a confident looking total stand in for a wrong one, the page now says so plainly and prints the count behind every line, so each can be checked against the game's own Fame Overview. The totals will be presented as fact again once the counts are right.

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
- A new Puzzle page. The Observatory puzzle is Lights Out: nine towers, shooting one flips it and the towers above, below and either side of it, and the room opens once all nine are lit. Sonar already receives each tower's state and position, so unlike the solvers on the web there is nothing to type in - walk in and the answer is already there, drawn with the game's own tower sprites so the grid reads the same as the room. The ones to shoot are ringed and crossed.
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
- Exalt bonuses are only shown for a character you have played since Sonar started - the account-wide character list carries progress towards the next exalt, not the bonus itself, so showing it from there would have been a different number wearing the same label.

**A theme for the whole interface, not just the accent**
- Six base palettes beside the accent colours: Plum, Obsidian, Espresso, Midnight, Forest and Wine. Each brings its own text tones rather than reusing one grey on a different ground.

**A volume for every ping**
- Each loot and chat ping has its own volume beside its switch, so a white bag can be loud while the rest stay quiet. Letting go of a slider plays that sound at the level just set.

**A ping for "j"**
- "j" in party chat is how a popped dungeon gets called out, so it has its own ping, separate from ordinary party chat. It fires on a bare "j", "jj" or "j!" and stays quiet for anything else, so "just wait" will not set it off.

## 1.26.0

**Sound settings you can actually reach**
- Sonar has had a full set of sound settings since it was forked - volume, a white bag ping, pings for every other bag colour, chat and trade pings - but they lived on a menu bar that was never attached to the window, so there was no way to open them. They now have their own Settings page, reachable from the button in the top bar or the rail. Switching a ping on plays it, so it is obvious which is which, and there is a Test button for the volume. Your existing settings are kept: these are the same saved values the old menu used.

**Accent colour**
- Six accents to choose from on the Settings page - Sonar, Coral, Mint, Violet, Gold and Ice. The whole interface takes the new colour at once, and the choice is remembered.

**Daily Quests, rebuilt in Sonar's own style**
- The quest list was still the plain grey panel carried over from Tomato. It is now a page of cards matching the rest of Sonar: each quest's name, what it asks for and what it pays out shown as item pictures, with badges for repeatable and completed quests.

**The hover note over an item is themed, and shows the item**
- Resting on a gear icon used to open the system's grey tooltip. It is now painted in Sonar's colours, leads with the item's own picture and name, and colours each enchant by its level.

**Fits a 1080p screen**
- The page could not shrink below a size that did not fit a 1080p screen with display scaling turned up, which forced scrollbars and squeezed the windows. The minimum is smaller now, the window can be sized down further, and the left rail narrows on its own when the window is narrow.

## 1.25.0

**Shiny items (and newly added items) now show their picture**
- Item pictures were looked up by name, and a Shiny carries the same display name as the plain version of the item - so only one of the two could own that name and the other came out blank. The same went for any item whose name Sonar had not registered yet. Pictures are now looked up by the item's own id wherever it is known, in the vault, on the damage bars and on the Characters page. The sprites were never missing: Shinies live on the game's `lofiObjShiny` sheet, which was extracted correctly the whole time.

**The vault is grouped by kind**
- Vault contents now sit under headings - Weapons, Abilities, Armor, Rings, Consumables, Other - instead of one long alphabetical run, so a full vault can actually be read.

## 1.24.1

**Fixed the Vault page never loading - the real cause this time**
- The game sends a short after the potion limits, just before the enchant data, that Sonar's packet reader did not know about. Two bytes behind from that point on, the three enchant strings at the end came out as nonsense and the reader ran off the end of the packet, so the whole vault update was thrown away every single time - the page could never have filled in, on any character. The reader now accounts for that field. Credit to RealmHound's packet implementation, which documents this field and is what the fix was checked against.
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
- The game only answers a full character-list request in the Pet Yard and the Daily Quest Room, so the Characters page had no way to learn about your characters anywhere else - it just sat empty. Sonar now records the character you're playing from the data already arriving each tick, and remembers it on disk, so characters build up as you play them and are still there after a restart. Visiting the Pet Yard or Daily Quest Room still fills in every character at once, including ones you haven't played recently.

**Fixed a seasonal character's vault never showing**
- Your vault's contents arrive as the map loads, often a moment before Sonar has worked out whether you're on a seasonal character. The contents were filed under one of the two vaults the game keeps (regular and seasonal) based on what was known at that instant, but read back based on what was known later - and when those disagreed, which they reliably did on a seasonal character, the page showed "Vault not loaded" while the data sat in the other vault the whole time. Sonar now reads from whichever vault actually received contents.

## 1.22.1

**Fixed Characters, Exaltation and Vault all sitting empty**
- These three pages show account-wide information, but they were only ever filled in while a tracked instance was loaded - so they stayed blank in the Nexus and in the vault itself, which is exactly where you'd open them. They now fill in regardless of whether you're in an instance.
- Second, separate cause for the Vault: your vault's contents arrive from the game right as the map loads, often a moment before Sonar has registered your character. When that happened the entire vault update was thrown away and the page stayed on "Vault not loaded" for the rest of the session. Vault contents are no longer discarded over that timing.

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
- Reopening Sonar (including right after an update installs) showed an empty Instances list even though nothing had actually been deleted - today's finished instances were safely saved to disk the whole time, but the list on screen was only ever built from what finished during the current run, never reloaded from the file. Today's saved instances are now loaded back in on startup, so they stay visible across restarts and updates the same way past days already did.

**Fixed the Vault page losing track of which vault it's showing**
- The write side (storing an incoming vault update) and the read side (deciding which vault to display) each independently guessed whether the character was seasonal, at different moments - if that guess changed between the two, vault data that had genuinely arrived could still show as not loaded. Both sides now use the same, more reliable source (the character list, sent once at character select) instead of each inferring it fresh from a stat that can lag.

**Widened the gap between Boss Segments and Leaderboard on the Analysis page**
- The two columns sat close enough that a dragged or saved window position could overlap them. Existing custom positions are reset to the (now wider) default on this update - if you'd rearranged this page, you can get back to your own layout with the "Reset layout" button, which only affects the page you're on.

**Fixed your own damage under-counting against a target that regens mid-fight**
- Fighting alone against something that heals partway through (a training dummy, say) could under-report your own confirmed damage - any health it regenerated was silently swallowed instead of credited back, even though the same fight-in-a-group formula already did this correctly. Solo damage checks now credit regen back the same way group ones do. A real, confirmed gap this closes - not a complete fix for every possible timing edge case (a hit whose own health-drop was never sampled before a session ends still can't be recovered after the fact), but a real, proven improvement either way.

## 1.20.2

**Fixed the Vault page getting stuck on "Vault not loaded"**
- Opening a vault chest in-game could silently fail to register with Sonar at all - the Vault page stayed on "Vault not loaded" even with the vault genuinely open and full, seen most reliably on seasonal characters. Cause: checking whether a character was seasonal crashed outright if that specific detail hadn't been tracked yet for them, which silently dropped the whole vault update rather than just defaulting sensibly. Fixed to default to the regular vault instead of crashing when that's unknown - open your vault and it loads now.

## 1.20.1

**No more surprise "Realm Shark Asset Extractor" popup**
- When the game updates, Sonar used to hand off to a raw, unbranded "Realm Shark Asset Extractor" window asking whether to re-extract item/enemy data - it could appear over the game mid-session, looking like it belonged to a different, unrelated program. Extraction now just happens silently in the background instead (it's fast and purely local - there was never really a meaningful choice to ask about). No visible change when everything's already up to date; a brief one-time pause the first time after a game patch, no popup.

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
- Built for exactly how these runs tend to get organized - in a Discord, where people ask where they stood - without anyone needing Sonar installed themselves to see it.

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
- Confirmed against the game's own object data and the dungeon's public wiki page rather than assumed, after Jordan asked specifically for the Shatters boss order to be checked rather than guessed.

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
- The Instances window now has day arrows: step back up to 30 days to see instances from earlier sessions, even after restarting Sonar. Each saved instance shows its dungeon's own portal picture.
- Click a saved instance the same way as a finished instance from today, to review its Live, Instance and Players data.
- Only Live, Instance and Players data is kept - never the vault or the panels carried over from Tomato. Saved as one file per day under the run folder; days older than 30 are deleted automatically on startup.

**Shiny items on the Gear window**
- Equipped Shiny items now get the same treatment as in the Vault: a gold glow, the SHINY tag, and a reflection sweep, on the Players page's Gear and stats window.
- The reflection sweep (Vault and Gear both) now only plays while your cursor is actually on that item's picture, instead of continuously - the glow and SHINY tag stay visible either way so a shiny is always identifiable at a glance.

**Class/gear verification**
- Internal check added that catches a class being shown with gear it could never actually equip, against the game's own class and item data, so a display bug like that can't ship unnoticed again.

**Shatters Hardmode radar (beta)**
- A new overlay, off by default, draws small marks over your game's own minimap for the Stone Idol, a Derelict Monument and the Source as Sonar spots them among entities the game has already sent your client - the same passive, single-client reading everything else in Sonar does, nothing from other players or any outside service.
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
- Dyed players were drawn in one flat wrong colour. The game sends which dye or cloth item a player wears, not the colour itself; Sonar now looks the item up in the game files to get its colour or pattern.

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
- Copies are kept until Sonar closes.

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
- New "Auto-start" switch above "Check for updates". Off by default. When on, the sniffer starts by itself each time Sonar opens.
- When the sniffer is started in the middle of an instance, Sonar now says it is waiting for your next instance and that tracking begins at the next loading screen. The game's data can only be read from the start of an instance.

## 1.5.7

**Pet damage**
- "Pet damage" now registers pets whose attacks the game reports under your own name, such as Electric. Sonar counts as pet damage everything the server reports as yours that none of your own shots accounts for.
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
- Your party is saved by username and stays through every instance and after closing Sonar, as before. Members who are present without having dealt damage now also count toward "instances together".

## 1.5.4

**Players are listed as soon as they are seen**
- The Players window now lists everyone Sonar has seen in the instance, not only those who have dealt damage. Players who have not hit anything yet come after the damage dealers and read "no damage yet".
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
- After an update, Sonar now also rebuilds `Peach.exe`, refreshes its shortcuts and re-reads the game's data, the things `Setup.bat` does. No update needs Setup to be run by hand any more.
- A small "Updating Sonar" window shows while this happens, then Sonar reopens.
- Sonar also notices at startup when its launcher and shortcuts were made for an older version, and quietly rebuilds them. That covers the update from 1.4.0, whose updater did not do this yet.

**Pin to taskbar**
- Sonar can now be pinned to the taskbar properly. Right-click the Sonar shortcut, or the Sonar button on the taskbar while it is running, and choose "Pin to taskbar".
- Before, Windows filed the running window under Java: a pinned Sonar showed a second button when opened, and pinning the running window pinned a Java that opened nothing. Sonar now tells Windows who it is, and its shortcuts carry the same identity.
- Setup also adds Sonar to the Start menu.

## 1.4.0

**Updates install themselves**
- When a newer version exists, Sonar asks **Update and restart**. On yes it downloads the release, closes, puts the new files in place and starts again by itself. Layout, party and settings are kept.
- Sonar also looks for a newer version once, a few seconds after it starts. It says nothing unless there is one.
- **Check for updates** can no longer stay on "Checking": every failure is caught, and the check gives up after 25 seconds with "Couldn't check". What happened is written to `run\update\update.log`.
- The check no longer uses GitHub's rate-limited API.
- A copy built from source is not overwritten; it is offered the download page instead.

**Setup**
- Setup no longer replaces the app while Sonar is open; it asks you to close Sonar first. Swapping the file under a running Sonar is what made the update button hang.

## 1.3.0

**Your own damage is checked against the server**
- The game server reports the damage of everyone else's hits, but never your own; those your client works out itself, and the server does not always register every one of them. That could leave your number higher than what other players see.
- Sonar now holds your damage on each enemy against the health the server says that enemy lost. Alone on an enemy, your damage is the health it lost. In a group, it is the health lost that the other players' reported hits do not explain, and never more than Sonar calculated.
- Damage beyond an enemy's remaining health is no longer counted for you, and an enemy you only tagged and left is not treated as killed.
- When the checked figure differs from the calculated one, the You window and your Players page show the calculated one as "Before server check".
- Finished instances keep the checked figures they ended on.

**Publisher information**
- `Peach.exe` now carries **JustAutomate-AI** as its company, with the product name, description and version filled in. They show under Properties > Details and in Task Manager.
- The desktop shortcut's description, the About box and the app file's manifest name the publisher as well.
- Sonar is not code-signed, so Windows security prompts still say "Unknown publisher". That needs a paid signing certificate.

**Diagnostics**
- With an `audit` folder next to the app, Sonar writes the server check's numbers per enemy and every own hit to that folder.

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
- Pet hits used to be dropped. They are now counted apart from the player lists: all pets together on the Instance page, and your own pet in the You window once Sonar has identified it (after a visit to the Pet Yard or the Daily Quest Room).

**Updates**
- **Check for updates** at the bottom of the left rail asks GitHub for the latest release and offers its download page when it is newer. Nothing is downloaded or installed automatically.
- The window title and rail show the Sonar version.

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
