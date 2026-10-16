package sonar;

import sonar.ui.WindowsShell;
import sonar.util.PropertiesManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Keeps Sonar where Windows can find it.
 * <p>
 * A program unpacked from a zip is invisible to the machine it is on. It has no Start
 * menu entry, so typing its name finds nothing, and the drive people unpack it onto is
 * usually not in the search index either, so not even the file turns up. The only way
 * back to it is to remember which folder it went in.
 * <p>
 * Sonar used to ask about that once, on first run, and remember the answer forever. Two
 * ways that left an install nobody could find: the answer was recorded before the dialog
 * was shown, so a "no thanks" and a PowerShell call that quietly failed were indistinguishable,
 * and either way it never asked again.
 * <p>
 * So it is not a question any more. A Start menu entry is part of being installed, the way
 * it is for every other program on the machine, and it is checked at every start: made when
 * it is missing, repointed when the install has moved, and rewritten when it predates the
 * taskbar identity. The desktop is the user's own space, so that one is offered once and
 * never resurrected - delete it and it stays deleted, while the Start menu entry it was
 * made alongside keeps the program searchable.
 * <p>
 * Only for the packaged build. A copy run from source or from the jar has no single
 * executable worth pointing at, and {@code tools/setup.ps1} already does this for those.
 * An install put here by the Windows installer has a Start menu entry of its own, in its
 * own group, which the script finds and leaves alone rather than adding a second one.
 */
public final class Shortcuts {

    /**
     * Records that the desktop has been dealt with. The name is the old one, from when
     * this was a dialog asking whether to add a shortcut at all: an existing install
     * that answered that question - however it answered - keeps its desktop as it is,
     * and only gets the Start menu entry it should always have had.
     */
    private static final String DESKTOP_SETTLED = "sonar.asked.shortcut";

    private static final String DESCRIPTION = "Sonar by JustAutomate-AI - Realm of the Mad God damage tracker";

    private Shortcuts() {
    }

    /**
     * Makes sure the Start menu entry exists and points here, on a background thread.
     * <p>
     * Does nothing at all unless this is the packaged build on Windows. Never fails the
     * start: everything it does is reported to {@code update/update.log} and nowhere else,
     * because a shortcut is not worth a dialog in front of someone who wants to play.
     */
    public static void ensure() {
        if (!windows() || automated()) return;
        // the same escape hatch tools/setup.ps1 honours, for a copy that is only being tested
        if (System.getenv("SONAR_NO_SHORTCUTS") != null) return;
        final File exe = packagedExe();
        if (exe == null) return;
        boolean first = !"true".equals(PropertiesManager.getProperty(DESKTOP_SETTLED));
        if (first) PropertiesManager.setProperties(DESKTOP_SETTLED, "true");
        final boolean desktop = first;
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                write(exe, desktop);
            }
        }, "shortcuts");
        worker.setDaemon(true);
        worker.start();
    }

    private static void write(File exe, boolean desktop) {
        try {
            File work = DataDir.dir("update");
            File script = new File(work, "shortcuts.ps1");
            PrintWriter w = new PrintWriter(new FileWriter(script));
            try {
                w.print(SCRIPT);
            } finally {
                w.close();
            }
            File helper = helper(work);
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                    "-File", script.getPath(),
                    "-Exe", exe.getPath(),
                    "-Root", exe.getParent(),
                    "-AppId", WindowsShell.APP_ID,
                    "-Description", DESCRIPTION,
                    "-Helper", helper == null ? "" : helper.getPath(),
                    "-Desktop", desktop ? "1" : "0")
                    .redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.trim().isEmpty()) log(line.trim());
                }
            } finally {
                r.close();
            }
            p.waitFor();
        } catch (Throwable e) {
            // a missing shortcut is a nuisance; a start that fails over one is not acceptable
            log("not checked (" + e + ")");
        }
    }

    /**
     * Unpacks the shortcut helper beside the script, or null when it is not in this jar.
     * <p>
     * It is the same {@code tools/launcher/Shortcut.cs} that Setup uses, shipped inside the
     * jar by the build so there is one copy of it. Without it the script still writes a
     * working shortcut, just one Windows will not match up with the running window.
     */
    private static File helper(File work) {
        try {
            InputStream in = Shortcuts.class.getResourceAsStream("/Shortcut.cs");
            if (in == null) return null;
            try {
                File cs = new File(work, "Shortcut.cs");
                Files.copy(in, cs.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return cs;
            } finally {
                in.close();
            }
        } catch (Throwable e) {
            return null;
        }
    }

    /** The launcher this process was started from, when that is Sonar's own exe. */
    private static File packagedExe() {
        try {
            String command = ProcessHandle.current().info().command().orElse(null);
            if (command == null) return null;
            File f = new File(command);
            // the packaged launcher is Sonar.exe; a plain java.exe means this is not it
            return f.getName().equalsIgnoreCase("Sonar.exe") && f.isFile() ? f : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** True for the screenshot and self-test runs, which should touch nobody's Start menu. */
    private static boolean automated() {
        return System.getProperty("sonar.selftest") != null
                || System.getProperty("sonar.shot") != null;
    }

    /** The same log the updater writes to, since both are housekeeping nobody sees happen. */
    private static void log(String line) {
        try {
            File dir = DataDir.dir("update");
            PrintWriter w = new PrintWriter(new FileWriter(new File(dir, "update.log"), true));
            try {
                w.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "  shortcut: " + line);
            } finally {
                w.close();
            }
        } catch (Exception ignored) {
            // the log is a convenience
        }
    }

    /**
     * Checks the two places a shortcut can be and writes only what is actually wrong.
     * <p>
     * The Start menu is searched through, not just looked at: the Windows installer puts
     * its entry in a group folder of its own, and a second one loose at the top level would
     * show up as a duplicate every time somebody typed "sonar". An entry pointing at some
     * other copy of Sonar.exe is treated as this install having moved and is repointed,
     * which is the case that leaves a dead shortcut behind otherwise.
     */
    private static final String SCRIPT = String.join("\r\n",
            "param([string]$Exe, [string]$Root, [string]$AppId, [string]$Description,",
            "      [string]$Helper, [int]$Desktop)",
            "$ErrorActionPreference = 'Stop'",
            "$name = 'Sonar.lnk'",
            "$ws = New-Object -ComObject WScript.Shell",
            // the app id is what lets Windows treat a pinned shortcut and the open window as one
            // program; without the helper the shortcut still works, it just pins as a second button
            "$identity = $false",
            "if ($Helper -and (Test-Path $Helper)) { try { Add-Type -Path $Helper; $identity = $true } catch { } }",
            "",
            "function Get-Target($path) {",
            "    try { return $ws.CreateShortcut($path).TargetPath } catch { return '' }",
            "}",
            "function Test-Identity($path) {",
            "    if (-not $identity) { return $true }",
            "    try { return ([SonarShortcut]::ReadAppId($path) -eq $AppId) } catch { return $true }",
            "}",
            "function Save-Link($path) {",
            "    if ($identity) {",
            "        try { [SonarShortcut]::Create($path, $Exe, $Root, $Description, $AppId); return '' } catch { }",
            "    }",
            "    $lnk = $ws.CreateShortcut($path)",
            "    $lnk.TargetPath = $Exe",
            "    $lnk.WorkingDirectory = $Root",
            "    $lnk.Description = $Description",
            "    $lnk.Save()",
            "    return ' (without the taskbar identity, so pinning it makes a second button)'",
            "}",
            "",
            "$programs = [Environment]::GetFolderPath('Programs')",
            "if ($programs) {",
            "    $mine = Join-Path $programs $name",
            "    $found = $null",
            "    $moved = $null",
            // the installer's own entry lives in a group folder, so this looks everywhere under
            // the Start menu rather than only at the top level
            "    foreach ($link in @(Get-ChildItem -LiteralPath $programs -Recurse -Filter $name -File -ErrorAction SilentlyContinue)) {",
            "        $target = Get-Target $link.FullName",
            "        if ($target -eq $Exe) { $found = $link.FullName; break }",
            "        if ($target -and ((Split-Path $target -Leaf) -eq 'Sonar.exe')) { $moved = $link.FullName }",
            "    }",
            "    try {",
            "        if ($found) {",
            // an entry that is already right is left alone, unless it is ours and predates the app id
            "            if (($found -eq $mine) -and -not (Test-Identity $mine)) {",
            "                $how = Save-Link $mine",
            "                Write-Output \"rewrote the Start menu entry so it can be pinned$how\"",
            "            }",
            "        } elseif ($moved) {",
            "            $how = Save-Link $moved",
            "            Write-Output \"repointed the Start menu entry at this install$how\"",
            "        } else {",
            "            $how = Save-Link $mine",
            "            Write-Output \"created the Start menu entry$how\"",
            "        }",
            "    } catch {",
            "        Write-Output \"could not write the Start menu entry: $_\"",
            "    }",
            "}",
            "",
            "$desk = [Environment]::GetFolderPath('Desktop')",
            "if ($desk) {",
            "    $path = Join-Path $desk $name",
            "    try {",
            "        if (Test-Path $path) {",
            // an existing one is kept working, but a deleted one stays deleted: the desktop is theirs
            "            if ((Get-Target $path) -ne $Exe) {",
            "                $how = Save-Link $path",
            "                Write-Output \"repointed the desktop shortcut at this install$how\"",
            "            }",
            "        } elseif ($Desktop -eq 1) {",
            "            $how = Save-Link $path",
            "            Write-Output \"created the desktop shortcut$how\"",
            "        }",
            "    } catch {",
            "        Write-Output \"could not write the desktop shortcut: $_\"",
            "    }",
            "}",
            "");
}
