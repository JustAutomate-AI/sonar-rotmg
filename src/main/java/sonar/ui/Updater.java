package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Finds newer Sonar releases on GitHub and installs them.
 * <p>
 * A check asks GitHub where "latest release" points, which needs no account
 * and is not subject to the API's hourly limit. When that release is newer,
 * the user is asked; on yes the release zip is downloaded and unpacked next
 * to the app, a small script waits for Sonar to close, copies the new files
 * over the old ones and starts Sonar again.
 * <p>
 * A copy built from source is never overwritten; there the download page is
 * offered instead.
 */
final class Updater {

    /** Version of this build. Raise it for each release; tools/make-release.ps1 reads it for the zip name. */
    static final String CURRENT = "2.55.4";

    private static final String REPOSITORY = "https://github.com/JustAutomate-AI/sonar-rotmg";
    private static final String LATEST = REPOSITORY + "/releases/latest";
    private static final int TIMEOUT_MS = 8000;
    private static final int GIVE_UP_MS = 25000;

    private static boolean busy;

    private Updater() {
    }

    static String current() {
        return System.getProperty("sonar.version", CURRENT);
    }

    /**
     * Checks in the background and reports on the event thread.
     *
     * @param parent Window the dialogs belong to.
     * @param status Receives a short line of progress or outcome for the button that started the check.
     * @param quiet  True for the check made at startup: says nothing unless a newer version exists.
     */
    static void check(Component parent, Consumer<String> status, boolean quiet) {
        if (busy) return;
        busy = true;
        if (!quiet) status.accept("Checking\u2026");
        AtomicBoolean done = new AtomicBoolean();
        // whatever happens on the network, the button never stays on "Checking"
        Timer giveUp = new Timer(GIVE_UP_MS, e -> {
            if (done.compareAndSet(false, true)) {
                busy = false;
                log("check gave up after " + GIVE_UP_MS + " ms without an answer");
                if (!quiet) status.accept("Couldn't check");
            }
        });
        giveUp.setRepeats(false);
        giveUp.start();
        Thread t = new Thread(() -> {
            String tag = null, error = null;
            try {
                tag = latestTag();
            } catch (Throwable e) {
                error = e.toString();
                log("check failed: " + e);
            }
            String foundTag = tag, foundError = error;
            SwingUtilities.invokeLater(() -> {
                if (!done.compareAndSet(false, true)) return;
                giveUp.stop();
                busy = false;
                try {
                    report(parent, status, quiet, foundTag, foundError);
                } catch (Throwable e) {
                    log("reporting failed: " + e);
                    if (!quiet) status.accept("Couldn't check");
                }
            });
        }, "sonar-update-check");
        t.setDaemon(true);
        t.start();
    }

    /** The tag the "latest release" link redirects to, such as v1.4.0. */
    private static String latestTag() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(LATEST).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "Sonar/" + current());
        int code = c.getResponseCode();
        String location = c.getHeaderField("Location");
        c.disconnect();
        if (code < 300 || code >= 400 || location == null || !location.contains("/releases/tag/")) {
            throw new java.io.IOException("GitHub answered " + code + (location == null ? "" : " -> " + location));
        }
        return location.substring(location.lastIndexOf('/') + 1).trim();
    }

    /**
     * Whether a release's file is actually downloadable yet.
     * <p>
     * Asked with a HEAD, so nothing is fetched to find out. A failure to ask is treated as
     * "yes": a network that cannot answer this would not have got as far as reading the
     * release anyway, and refusing an update over it would be worse than trying and failing
     * with the ordinary message.
     */
    static boolean filePublished(String address) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
            c.setRequestMethod("HEAD");
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "Sonar/" + current());
            int code = c.getResponseCode();
            c.disconnect();
            return code != 404;
        } catch (Exception cannotTell) {
            return true;
        }
    }

    /** What a download's answer means, in words somebody can act on. */
    static String explain(int code) {
        if (code == 404) {
            return "the files for this version are not published yet - the build is still finishing. "
                    + "Try again in a few minutes.";
        }
        if (code == 403 || code == 429) return "GitHub is rate limiting this connection. Try again shortly.";
        return "download answered " + code;
    }

    private static void report(Component parent, Consumer<String> status, boolean quiet, String tag, String error) {
        if (error != null || tag == null) {
            if (quiet) return;
            status.accept("Couldn't check");
            JOptionPane.showMessageDialog(parent, "Couldn't reach GitHub to check for updates.\n" + error, "Sonar", JOptionPane.WARNING_MESSAGE);
            return;
        }
        log("latest release is " + tag + ", this is " + current());
        if (!isNewer(tag, current())) {
            if (!quiet) status.accept("Up to date");
            return;
        }
        status.accept("Update available");
        String version = clean(tag);
        File root = installRoot();

        // A release is announced before its files exist.
        //
        // The tag is pushed, GitHub publishes the release page immediately, and the build that
        // produces the installer takes a couple of minutes more. Anyone who checks for updates
        // inside that window is told a new version exists, downloads it, and is handed a 404
        // with a stack trace attached - which reads as a broken updater rather than as a
        // version that is thirty seconds early.
        String fileUrl = REPOSITORY + "/releases/download/" + tag + "/"
                + (installedByInstaller(root) ? "Sonar-" + version + "-setup.msi"
                                              : "Sonar-" + version + "-windows.zip");
        if (!filePublished(fileUrl)) {
            log(tag + " is announced but its files are not published yet");
            status.accept("Update building");
            if (!quiet) {
                SonarDialog.ask(parent, "Sonar " + version + " is on its way",
                        "The release has been announced but its files are still being built. "
                                + "This usually takes a few minutes. Check again shortly.",
                        "All right");
            }
            return;
        }
        boolean fromSource = new File(root, "build.gradle").exists() && System.getProperty("sonar.update.force") == null;
        if (fromSource) {
            Object[] options = {"Open download page", "Later"};
            int choice = JOptionPane.showOptionDialog(parent,
                    "Sonar " + version + " is available. You have " + current() + ".\n\n"
                            + "This copy was built from source, so it is not replaced automatically.\n"
                            + "Pull the new source and run Setup.bat, or download the release.",
                    "Sonar update", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
            if (choice == 0) browse(parent, REPOSITORY + "/releases/tag/" + tag);
            return;
        }
        int choice = 0;
        if (System.getProperty("sonar.update.auto") == null) {
            choice = SonarDialog.ask(parent, "Sonar " + version + " is available",
                    "You have " + current() + ". Sonar will download the new version, close, install it "
                            + "and start again. Your layout, party and settings are kept.",
                    "Update and restart", "Later");
        }
        if (choice == 0) install(parent, status, tag, root);
    }

    /** Downloads the release, stages it and hands over to the script that swaps the files and restarts. */
    private static void install(Component parent, Consumer<String> status, String tag, File root) {
        busy = true;
        status.accept("Downloading\u2026");
        Thread t = new Thread(() -> {
            String error = null;
            File work = sonar.DataDir.file("update").getAbsoluteFile();
            try {
                deleteAll(new File(work, "staged"));
                work.mkdirs();
                String version = clean(tag);

                // An install the Windows installer put here is upgraded by the installer, not by
                // unpacking a zip over it. Doing it the other way leaves Add or Remove Programs
                // describing a version that is no longer on disk, and a later repair or uninstall
                // then works from records that no longer match anything.
                File msi = null;
                File staged = new File(work, "staged");
                if (installedByInstaller(root)) {
                    msi = new File(work, "Sonar-" + version + "-setup.msi");
                    download(REPOSITORY + "/releases/download/" + tag + "/Sonar-" + version + "-setup.msi", msi, status);
                    SwingUtilities.invokeLater(() -> status.accept("Installing\u2026"));
                    log("handing " + tag + " to the Windows installer, quietly");
                } else {
                    File zip = new File(work, "Sonar-" + version + ".zip");
                    download(REPOSITORY + "/releases/download/" + tag + "/Sonar-" + version + "-windows.zip", zip, status);
                    SwingUtilities.invokeLater(() -> status.accept("Installing\u2026"));
                    boolean hasApp = unzip(zip, staged);
                    zip.delete();
                    if (!hasApp) throw new java.io.IOException("the download does not contain the app");
                }
                File script = new File(work, "apply-update.ps1");
                try (PrintWriter w = new PrintWriter(new FileWriter(script))) {
                    w.print(SCRIPT);
                }
                String javaw = new File(System.getProperty("java.home"), "bin\\javaw.exe").getPath();
                log("starting the installer for " + tag);
                new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                        "-File", script.getPath(),
                        "-ProcessId", String.valueOf(ProcessHandle.current().pid()),
                        "-Root", root.getPath(),
                        "-Staged", staged.getPath(),
                        "-Javaw", javaw,
                        // empty for a copy upgraded by unpacking; the installer's path otherwise
                        "-Msi", msi == null ? "" : msi.getPath(),
                        // whichever theme is chosen, so the handover looks like the app it came from
                        "-Bg", hex(SonarTheme.SURFACE),
                        "-Fg", hex(SonarTheme.TEXT),
                        "-Muted", hex(SonarTheme.MUTED),
                        "-Accent", hex(SonarTheme.accent())).start();
            } catch (Throwable e) {
                error = e.toString();
                log("update failed: " + e);
            }
            String failed = error;
            SwingUtilities.invokeLater(() -> {
                busy = false;
                if (failed != null) {
                    status.accept("Update failed");
                    Object[] options = {"Open download page", "Close"};
                    int choice = JOptionPane.showOptionDialog(parent, "The update could not be installed automatically.\n" + failed
                                    + "\n\nYou can download it yourself and run Setup.bat.",
                            "Sonar update", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                    if (choice == 0) browse(parent, REPOSITORY + "/releases/tag/" + tag);
                    return;
                }
                status.accept("Restarting\u2026");
                // closing the window the normal way saves the layout and stops the sniffer; the script takes over once Sonar is gone
                Window window = parent instanceof Window ? (Window) parent : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
                if (window != null) window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
                else System.exit(0);
            });
        }, "sonar-update-install");
        t.setDaemon(true);
        t.start();
    }

    /** A colour as PowerShell's ColorTranslator reads it. */
    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static void download(String address, File target, Consumer<String> status) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "Sonar/" + current());
        int code = c.getResponseCode();
        // GitHub hands release files to another host; Java does not follow a redirect that changes host on its own in every case
        for (int hop = 0; hop < 5 && code >= 300 && code < 400; hop++) {
            String next = c.getHeaderField("Location");
            c.disconnect();
            if (next == null || !next.startsWith("https://")) throw new java.io.IOException("unexpected redirect");
            c = (HttpURLConnection) new URL(next).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Sonar/" + current());
            code = c.getResponseCode();
        }
        if (code != 200) throw new java.io.IOException(explain(code));
        long total = c.getContentLengthLong(), got = 0;
        int shown = -1;
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                got += n;
                int percent = total > 0 ? (int) (got * 100 / total) : -1;
                if (percent != shown && percent >= 0) {
                    shown = percent;
                    SwingUtilities.invokeLater(() -> status.accept("Downloading " + percent + "%"));
                }
            }
        }
        if (got < 1_000_000) throw new java.io.IOException("the download is too small to be the app (" + got + " bytes)");
    }

    /** Unpacks the zip; entries that would land outside the target folder are refused. Returns whether the app itself was inside. */
    private static boolean unzip(File zip, File target) throws Exception {
        boolean hasApp = false;
        String base = target.getCanonicalPath() + File.separator;
        try (ZipInputStream in = new ZipInputStream(new java.io.FileInputStream(zip))) {
            ZipEntry e;
            byte[] buffer = new byte[65536];
            while ((e = in.getNextEntry()) != null) {
                File out = new File(target, e.getName());
                if (!out.getCanonicalPath().startsWith(base)) throw new java.io.IOException("unsafe path in the download: " + e.getName());
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                out.getParentFile().mkdirs();
                try (OutputStream o = new FileOutputStream(out)) {
                    int n;
                    while ((n = in.read(buffer)) > 0) o.write(buffer, 0, n);
                }
                // Either release layout counts. The older one shipped the jar under run\ and
                // relied on Java being installed; the packaged one ships an exe with its own
                // runtime beside it. An install can be handed either and has to cope.
                String entry = e.getName().replace('\\', '/');
                if (entry.equals("Sonar/run/Sonar.jar") || entry.equals("Sonar/app/Sonar.jar")
                        || entry.equals("Sonar/Sonar.exe")) hasApp = true;
            }
        }
        return hasApp;
    }

    private static void deleteAll(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteAll(c);
        f.delete();
    }

    /**
     * The folder an update should be unpacked over: the one holding Sonar.exe.
     * <p>
     * There are two layouts and they differ by a level. A portable copy runs from a "run"
     * folder with Sonar.exe one above it, so the parent is right. A packaged install runs
     * from the folder that holds Sonar.exe itself, so the parent is whatever that install
     * happens to sit inside - a Desktop, a Downloads folder, the root of a drive.
     * <p>
     * This used to take the parent either way. On a packaged install that meant an update
     * unpacked the whole application into the folder beside it rather than over itself:
     * the install was left untouched on the old version, and a copy of the app appeared
     * loose wherever it had been put. So the folder is now identified by what is in it
     * rather than by counting levels.
     */
    private static File installRoot() {
        // Where this code actually sits, asked first, because the working directory is not
        // Sonar's to rely on. Started from a shortcut with the wrong "Start in", or from a
        // script, that directory can be anywhere - and this used to resolve a root from it
        // and hand that to the installer, which would then unpack an update over a folder
        // with nothing to do with Sonar. Seen for real: launched with the working directory
        // left at the user profile, it resolved C:\Users and tried to copy there.
        File code = codeFolder();
        for (File f : new File[]{code, code == null ? null : code.getParentFile()}) {
            if (f != null && looksLikeInstall(f)) return f;
        }
        File here = new File("").getAbsoluteFile();
        if (looksLikeInstall(here)) return here;
        File up = here.getParentFile();
        if (up != null && looksLikeInstall(up)) return up;
        return up == null ? here : up;
    }

    /** The folder holding the jar this code was loaded from, or null if that cannot be read. */
    private static File codeFolder() {
        try {
            java.security.CodeSource source = Updater.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) return null;
            File at = new File(source.getLocation().toURI());
            return at.isDirectory() ? at : at.getParentFile();
        } catch (Exception e) {
            return null;
        }
    }

    /** Whether a folder is the one the application was installed into. */
    /**
     * Whether this copy was put here by the Windows installer rather than unpacked from
     * the portable zip. The installer writes this marker beside the program; the zip has
     * no such file, so a portable copy keeps updating itself the way it always has.
     */
    /** Lets the self-test read the handover script without writing it anywhere. */
    static String scriptForTest() {
        return SCRIPT;
    }

    private static boolean installedByInstaller(File root) {
        return root != null && new File(root, "installed-by-msi").isFile();
    }

    private static boolean looksLikeInstall(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        return new File(dir, "Sonar.exe").isFile()
                || new File(dir, "app" + File.separator + "Sonar.jar").isFile()
                || new File(dir, "Setup.bat").isFile();
    }

    private static void browse(Component parent, String page) {
        try {
            Desktop.getDesktop().browse(new URI(page));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent, "Open this page in your browser:\n" + page, "Sonar", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void log(String line) {
        try {
            File dir = sonar.DataDir.file("update");
            dir.mkdirs();
            try (PrintWriter w = new PrintWriter(new FileWriter(new File(dir, "update.log"), true))) {
                w.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "  " + line);
            }
        } catch (Exception ignored) {
            // the log is a convenience
        }
    }

    private static String clean(String version) {
        String v = version.trim();
        return v.startsWith("v") || v.startsWith("V") ? v.substring(1) : v;
    }

    /** Compares dotted versions number by number, so 1.10.0 is newer than 1.9.0. */
    static boolean isNewer(String candidate, String current) {
        String[] a = clean(candidate).split("[.\\-]"), b = clean(current).split("[.\\-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = part(a, i), y = part(b, i);
            if (x != y) return x > y;
        }
        return false;
    }

    private static int part(String[] parts, int i) {
        if (i >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Waits for Sonar to close, copies the new files over the old ones, runs
     * Setup so the launcher, shortcuts and game data match the new version,
     * and starts Sonar again. A small window says so meanwhile.
     */
    private static final String SCRIPT = String.join("\r\n",
            // the colours come from whichever theme is chosen, so the handover matches the app it
            // is handing over from rather than a palette fixed at build time
            "param([int]$ProcessId, [string]$Root, [string]$Staged, [string]$Javaw, [string]$Msi = '',",
            "      [string]$Bg = '#17131D', [string]$Fg = '#F3EEF6', [string]$Muted = '#968EA0', [string]$Accent = '#FFA07A')",
            "$log = Join-Path $PSScriptRoot 'update.log'",
            "function Log($text) { Add-Content -Path $log -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + '  installer: ' + $text) }",
            "$form = $null",
            "try {",
            // Sonar's own colours, rounded, and no Windows chrome: the handover is part of the app,
            // and a grey system dialog in the middle of it looked like something had gone wrong
            "    Add-Type -AssemblyName System.Windows.Forms",
            "    Add-Type -AssemblyName System.Drawing",
            "    $w = 300; $h = 84; $radius = 16",
            "    $form = New-Object System.Windows.Forms.Form",
            "    $form.Width = $w; $form.Height = $h; $form.StartPosition = 'CenterScreen'",
            "    $form.FormBorderStyle = 'None'; $form.ControlBox = $false; $form.TopMost = $true",
            "    $form.BackColor = [System.Drawing.ColorTranslator]::FromHtml($Bg)",
            "    $form.ShowInTaskbar = $false",
            // a rounded region, so the corners are actually cut rather than merely painted dark
            "    $path = New-Object System.Drawing.Drawing2D.GraphicsPath",
            "    $path.AddArc(0, 0, $radius, $radius, 180, 90)",
            "    $path.AddArc(($w - $radius - 1), 0, $radius, $radius, 270, 90)",
            "    $path.AddArc(($w - $radius - 1), ($h - $radius - 1), $radius, $radius, 0, 90)",
            "    $path.AddArc(0, ($h - $radius - 1), $radius, $radius, 90, 90)",
            "    $path.CloseFigure()",
            "    $form.Region = New-Object System.Drawing.Region($path)",
            "    $title = New-Object System.Windows.Forms.Label",
            "    $title.Text = 'Updating Sonar'",
            "    $title.SetBounds(0, 20, $w, 22); $title.TextAlign = 'MiddleCenter'",
            "    $title.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($Fg)",
            "    $title.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 11.5)",
            "    $label = New-Object System.Windows.Forms.Label",
            "    $label.Text = 'Reopening in a moment'",
            "    $label.SetBounds(0, 42, $w, 16); $label.TextAlign = 'MiddleCenter'",
            "    $label.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($Muted)",
            "    $label.Font = New-Object System.Drawing.Font('Segoe UI', 8.5)",
            // one short accent rule, inset so it sits clear of the rounded corners
            "    $accent = New-Object System.Windows.Forms.Panel",
            "    $accent.SetBounds(([int](($w - 44) / 2)), 66, 44, 2)",
            "    $accent.BackColor = [System.Drawing.ColorTranslator]::FromHtml($Accent)",
            "    $form.Controls.Add($title); $form.Controls.Add($label); $form.Controls.Add($accent)",
            "    $form.Show(); $form.Refresh()",
            "} catch { }",
            "try {",
            "    Log \"waiting for Sonar (process $ProcessId) to close\"",
            "    try { Wait-Process -Id $ProcessId -Timeout 40 -ErrorAction Stop } catch { }",
            // An installed copy is upgraded by the Windows installer, and the installer used to be
            // shown. That meant clicking through a wizard on every single update, which is a worse
            // thing to ask than the update was worth - and the reason it was shown at all was only
            // that nothing was waiting around to run it quietly. Something is now.
            //
            // Sonar installs per user, so there is no elevation to prompt for and nothing a person
            // has to answer. Quiet it is. 3010 means it worked and wants a reboot, which it does not
            // really; anything else and the installer is shown after all, so a failure ends with a
            // wizard somebody can finish rather than an update that silently did not happen.
            "    if ($Msi) {",
            "        Log \"installing $Msi quietly\"",
            "        $msiArgs = @('/i', $Msi, '/qn', '/norestart')",
            "        $code = -1",
            "        try { $code = (Start-Process 'msiexec.exe' -ArgumentList $msiArgs -Wait -PassThru).ExitCode } catch { Log \"quiet install threw: $_\" }",
            "        Log \"installer finished with code $code\"",
            "        if ($code -ne 0 -and $code -ne 3010) {",
            "            Log 'quiet install did not work; showing the installer to be finished by hand'",
            "            if ($form) { $form.Close(); $form = $null }",
            "            try { Start-Process 'msiexec.exe' -ArgumentList @('/i', $Msi) -Wait } catch { Log \"shown install threw: $_\" }",
            "        }",
            "        Remove-Item $Msi -Force -ErrorAction SilentlyContinue",
            "    } else {",
            "    $source = Join-Path $Staged 'Sonar'",
            "    $copied = $false",
            "    for ($i = 0; $i -lt 30 -and -not $copied; $i++) {",
            "        try { Copy-Item (Join-Path $source '*') $Root -Recurse -Force -ErrorAction Stop; $copied = $true }",
            "        catch { Start-Sleep -Milliseconds 500 }",
            "    }",
            "    Log \"new files copied: $copied\"",
            "    Remove-Item $Staged -Recurse -Force -Confirm:$false -ErrorAction SilentlyContinue",
            "    $setup = Join-Path $Root 'tools\\setup.ps1'",
            "    if ($copied -and (Test-Path $setup)) {",
            "        if ($form) { $form.Refresh() }",
            "        $out = & powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $setup 2>&1",
            "        Log \"setup finished with code $LASTEXITCODE\"",
            "        $out | Where-Object { \"$_\" -match 'FAIL|WARN' } | Select-Object -First 8 | ForEach-Object { Log ('setup: ' + \"$_\".Trim()) }",
            "    }",
            "    }",
            "} catch { Log \"failed: $_\" }",
            "if ($form) { $form.Close() }",
            "$exe = Join-Path $Root 'Sonar.exe'",
            "if (Test-Path $exe) { Start-Process $exe -WorkingDirectory $Root; Log 'started Sonar.exe' }",
            "elseif (Test-Path $Javaw) { Start-Process $Javaw -ArgumentList '-jar', 'Sonar.jar' -WorkingDirectory (Join-Path $Root 'run'); Log 'started Sonar with java' }",
            "else { Log 'could not find a way to start Sonar again' }",
            "");
}
