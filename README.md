# Sonar

A damage tracker for Realm of the Mad God. Sonar reads the game's network packets (it never modifies, blocks or sends any) and shows who is dealing how much damage in the instance you are in.

**[⬇ Download the latest release](https://github.com/JustAutomate-AI/sonar-rotmg/releases/latest)** - get `Sonar-<version>-windows.zip` from there, not the "Code" button above. The "Code" button downloads the source, which needs to be built; the release is ready to run.

![The Live screen](docs/design/sonar-live.png)

## What it shows

- **Live**: the damage list, your own numbers and the current fight. Damage is counted three ways (overall for the instance over elapsed time, per enemy, bosses only), as damage per second or total damage dealt, for everyone or for your party.
- **Self-updating**: Sonar checks GitHub for a newer release at startup and installs it on request.
- **Server-checked own damage**: the game never reports your own hits, so Sonar holds its calculation against the health each enemy actually lost.
- **Reset and review**: a two-click reset zeroes the damage shown for the current instance (with undo), and two arrows step through finished instances.
- **Players**: click a player's bar to see their damage by item, hit count, biggest hit and equipment. Pet damage is counted separately.
- **Instance**: details of the area, every enemy hit, finished instances and your character's stats. Everything resets when a new area loads, and finished instances stay available for review.
- **Party**: click the rank number on a player's bar to add them. The party is remembered by username across instances and sessions.
- Windows can be moved, resized and closed; the layout is remembered.

## Install (Windows)

**To just use it:** download `Sonar-<version>-windows.zip` from the [latest release](https://github.com/JustAutomate-AI/sonar-rotmg/releases/latest), unpack it, and double-click `Setup.bat`. It checks for Java and Npcap, offers to install what is missing, and creates `Sonar.exe` and a desktop shortcut. `INSTRUCTIONS.txt` in the zip walks through it.

**To build it yourself:**

1. Clone this repository.
2. Double-click `Setup.bat`. It checks the prerequisites (64-bit Java JDK, Npcap, Git), offers to install what is missing, builds Sonar and creates `Sonar.exe`.
3. Start `Sonar.exe`, press **Start sniffer**, then enter a new area in game so it can sync.

`tools\make-release.ps1` packs the built app into the release zip.

`Diagnose.bat` checks an existing install without changing anything. [INSTALL.md](INSTALL.md) has the details and the fixes for known problems.

Only one game client at a time is supported, and the sniffer sees nothing while the game runs through a VPN.

## A note on use

Sonar only listens to traffic on your own machine. Third-party tools are still a grey area under the game's terms of service, so use it at your own risk.

## Licence

MIT - see [LICENSE.md](LICENSE.md), which ships with every copy.

Sonar includes third-party code under their own terms: the packet-capture layer
([ardikars pcap](https://github.com/ardikars/pcap) on [Npcap](https://npcap.com/))
and asset extraction derived from [UnityPy](https://github.com/K0lb3/UnityPy).
Their notices are kept in the source and in the built jar.

## Changes

See [CHANGELOG.md](CHANGELOG.md) for what each version added.

