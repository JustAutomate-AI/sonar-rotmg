# Sonar

A damage tracker for Realm of the Mad God. Sonar reads the game's network packets (it never modifies, blocks or sends any) and shows who is dealing how much damage in the instance you are in.

**[⬇ Download the latest release](https://github.com/JustAutomate-AI/sonar-rotmg/releases/latest)** - get `Sonar-<version>-setup.msi` from there, not the "Code" button above. The "Code" button downloads the source, which needs to be built.

## What it does and does not do

Sonar **reads**. It never writes, and that is a design limit rather than a promise:

- It **never sends anything to the game**. No packet injection, no automation, no input simulation, no clicking or moving for you. The game's upstream channel is RC4 encrypted and stateful, so anything injected would desync the cipher and disconnect you - listening only is the only thing that works, and it is what Sonar does.
- It **never reads your password**. Sonar watches game traffic on your own machine; it has no login, no account access and no interest in one.
- **Nothing leaves your computer** except one request to GitHub's release page, to see whether a newer version exists. No telemetry, no analytics, no uploads, no accounts. That check is in [`Updater.java`](src/main/java/sonar/ui/Updater.java) and it is the only outbound call in the program.

Third-party tools are a grey area under the game's terms of service, and a passive reader is at the mildest end of that. It is still your call and your risk.

## Install

Download **`Sonar-<version>-setup.msi`** and run it.

It installs for **your user only**, so Windows does not ask for administrator rights at any point. Sonar brings its own Java, so there is nothing else to install, and it appears in Add or Remove Programs like any other application.

The one thing it cannot bring with it is **Npcap**, the driver that lets any program read network traffic - its licence does not allow redistribution. Sonar tells you if it is missing and points you at [npcap.com](https://npcap.com/). Npcap's own installer *does* ask for administrator rights, because it installs a driver; it is signed by the Nmap project, who have published security tools since 1997. Everything except the sniffer works without it.

A portable `Sonar-<version>-windows.zip` is also on the release page if you would rather not install anything: unpack it anywhere and run `Sonar.exe`.

## About the Windows warning

**Windows will warn you about this download, and here is exactly why.**

Sonar is not code-signed. A signing certificate requires a registered company with three or more years of filings, or several hundred dollars a year; this is a one-person project a few weeks old, so it has neither. Windows cannot tell an unsigned program written carefully apart from an unsigned program written badly, so it warns about both.

What that looks like: SmartScreen may say *"Windows protected your PC"*. Click **More info**, then **Run anyway**. You should not take that advice from a README alone, so here is how to check the file instead of trusting it.

**Every release is built in public, and you can prove the download came from this source code.** Releases are built by [a GitHub Actions workflow](.github/workflows/release.yml) you can read, and each file carries a signed provenance attestation tying it to the exact commit it was built from:

```
gh attestation verify Sonar-<version>-setup.msi --repo JustAutomate-AI/sonar-rotmg
```

That passes only if the file you have is the file that workflow produced from this repository. Nothing I say can be substituted for it. Each release also carries `SHA256SUMS-<version>.txt`, and the build log is public under the Actions tab.


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

## Building it yourself

1. Clone this repository.
2. Double-click `Setup.bat` - it checks the prerequisites (a 64-bit JDK, Npcap),
   offers to install what is missing, and builds Sonar into `run\Sonar.jar`.
3. `tools\make-package.ps1` turns that into the release zip: a trimmed Java
   runtime and `Sonar.exe`, ready to unpack and run.

[INSTALL.md](INSTALL.md) has the details and the fixes for known problems.

## Licence

MIT - see [LICENSE.md](LICENSE.md), which ships with every copy.

Sonar includes third-party code under their own terms: the packet-capture layer
([ardikars pcap](https://github.com/ardikars/pcap) on [Npcap](https://npcap.com/))
and asset extraction derived from [UnityPy](https://github.com/K0lb3/UnityPy).
Their notices are kept in the source and in the built jar.

## Changes

See [CHANGELOG.md](CHANGELOG.md) for what each version added.

