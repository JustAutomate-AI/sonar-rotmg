# Installing Sonar (Windows)

## The short version

1. Double-click **`Setup.bat`**. It checks the prerequisites, offers to install anything missing (automatically or through a download link), builds Sonar, tests packet capture and creates the launcher.
2. Double-click **`Sonar.exe`** (Setup also puts a **Sonar** shortcut on the desktop).
3. In Sonar press **Start sniffer**, then enter a new area in game (e.g. the Nexus) so it can sync. The sniffer is started by hand each session and stops when Sonar closes.

The release zip from GitHub works the same way, without the build: unpack it and run `Setup.bat` (see `INSTRUCTIONS.txt` inside it).

If something stops working later, double-click **`Diagnose.bat`**. It changes nothing and reports what is wrong.
Run `Setup.bat` again after pulling new source code or after a game update.

## What Setup does

**Step 1 - prerequisite check.** Every item is listed as OK, MISSING (required) or OPTIONAL.

| Prerequisite | Needed for | Offered when missing |
|---|---|---|
| Windows 10/11, 64-bit | Everything | - |
| Java JDK, 64-bit, version 8 to 19 | Building and running | Automatic install of Eclipse Temurin 17, or the download page |
| Npcap | Reading the game's network packets | The download page with the options to tick (its license forbids automatic installs) |
| Git | Fetching the packet library source (first setup only) | Automatic install, or the download page |
| Internet access | Downloading the build tools (first build only) | Check again after fixing the connection |
| .NET Framework 4 compiler | Building `Sonar.exe` (optional) | The download page, or continue with the .bat launcher |
| Realm of the Mad God | Item and enemy names/icons (optional) | The game's website, or continue without |

**Step 2 - install what is missing.** For each missing item you pick a numbered option; Setup then checks again. Required items can be declined, which stops Setup with the links in the summary.

**Step 3 - build and verify.**

| Step | What happens | If it fails |
|---|---|---|
| Environment | Looks for VPN adapters, Wi-Fi only connections, more than one game client, Npcap admin-only mode | Warns |
| Build | Builds Sonar into `run\Sonar.jar` | Stops with the build error |
| Self test | Java loads Npcap and opens the network interfaces | Says which of the fixes below applies |
| Assets | Extracts item/enemy data and sprites into `runssets` | Warns; Sonar offers to extract on start |
| Launcher | Builds `Sonar.exe` (with icon) and a desktop shortcut; it starts Sonar with the full Java path | Falls back to the .bat launcher |

Npcap installer options: keep **WinPcap API-compatible Mode** ticked, leave **Restrict access to Administrators only** unticked.

## Known problems and their fixes

| Symptom | Cause | Fix |
|---|---|---|
| Double-clicking the `.jar` does nothing, or Windows asks what to open it with | Broken `.jar` file association | Use `Sonar.exe`, it does not rely on the association (no "jarfix" needed) |
| "You are missing a program called Npcap" although it is installed | Npcap without WinPcap-compatible mode, or a 32-bit / wrong Java opened the jar | Reinstall Npcap with the option ticked; start through `Sonar.exe` |
| "Out of memory crash", or asset extraction never finishes | 32-bit Java | Run `Setup.bat`, it selects or installs a 64-bit JDK |
| "Extraction access denied" / nothing gets extracted | Jar sits in a protected folder (Program Files, OneDrive, inside a zip) | Setup runs Sonar from its own `run` folder; keep the project in a normal folder |
| Sniffer started but chat and damage stay empty | VPN is on, sniffer was started mid-dungeon, or two game clients are open | Turn the VPN off, change area after starting the sniffer, run a single client |
| "Stream Constructor reached 50 packets. Shutting down." | Packet loss, almost always on Wi-Fi | Use a network cable, then restart the sniffer |
| "The pcap_t has not been activated", "Invalid memory access", "Time sync failed" | Npcap install is damaged | Uninstall Npcap, reboot, install the current version, run `Diagnose.bat` |
| Sonar does not open at all | Startup error | It now shows the error in a dialog; `Start Sonar (debug console).bat` shows the full output |
| Wrong or missing item names/icons after a game update | Extracted assets are older than the game | Run `Setup.bat` again |
| Blurry or broken window on a high-DPI screen | Java 8 does not scale | Setup uses Java 17 |

## Folder layout

- `src/` - all of Sonar, under the `sonar` package: the interface, the damage tracking and the packet library.
- `run/` - where Sonar runs: the jar, extracted assets, settings (`sonar.properties`) and logs.
- `tools/setup.ps1` - the script behind `Setup.bat` and `Diagnose.bat`.
- `tools/launcher/` - source and icon for `Sonar.exe` (`tools/make-icon.py` redraws the icon).
