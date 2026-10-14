package sonar.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything Sonar keeps on disk about instances already finished, and the
 * one way to be rid of it.
 * <p>
 * Nothing here is deleted outright: it goes to the Recycle Bin, so a wipe
 * made in haste can be undone from Windows. Settings, the party, saved
 * characters and remembered faces are deliberately not touched - those are
 * about the account, not about any instance.
 */
final class InstanceMemory {

    private InstanceMemory() {
    }

    /** The folders and files a wipe takes, each relative to the app's run folder. */
    private static final String[] PLACES = {"history", "audit", "exports", "FameSessions"};

    private static List<File> present() {
        List<File> found = new ArrayList<>();
        for (String place : PLACES) {
            File f = sonar.DataDir.file(place);
            if (f.exists()) found.add(f);
        }
        return found;
    }

    /** How much room all of it takes, in bytes. */
    static long size() {
        long total = 0;
        for (File f : present()) total += sizeOf(f);
        return total;
    }

    private static long sizeOf(File f) {
        if (f.isFile()) return f.length();
        File[] children = f.listFiles();
        if (children == null) return 0;
        long total = 0;
        for (File child : children) total += sizeOf(child);
        return total;
    }

    /** How many instances are currently remembered, for saying what is about to go. */
    static int days() {
        File history = sonar.DataDir.file("history");
        File[] files = history.listFiles((d, n) -> n.endsWith(".json") || n.endsWith(".json.gz"));
        return files == null ? 0 : files.length;
    }

    static String describe() {
        long bytes = size();
        if (bytes <= 0) return "Nothing saved";
        String room = bytes >= 1024 * 1024
                ? String.format("%.1f MB", bytes / 1024.0 / 1024.0)
                : Math.max(1, bytes / 1024) + " KB";
        int days = days();
        return room + (days > 0 ? "  ·  " + days + (days == 1 ? " day" : " days") + " of instances" : "");
    }

    /**
     * Sends every instance file to the Recycle Bin.
     * <p>
     * Windows has no Java call for this, so it goes through the shell's own
     * recycler rather than being unlinked - the whole point is that this is
     * recoverable. If that cannot be reached, nothing is removed at all
     * rather than quietly deleting for good.
     *
     * @return what happened, for showing on the button.
     */
    static String wipe() {
        List<File> targets = present();
        if (targets.isEmpty()) return "Nothing to clear";
        StringBuilder paths = new StringBuilder();
        for (File f : targets) {
            if (paths.length() > 0) paths.append(',');
            paths.append('\'').append(f.getAbsolutePath().replace("'", "''")).append('\'');
        }
        String script =
                "Add-Type -AssemblyName Microsoft.VisualBasic;"
                        + "$paths = @(" + paths + ");"
                        + "foreach ($p in $paths) {"
                        + "  if (Test-Path -LiteralPath $p -PathType Container) {"
                        + "    [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($p,"
                        + "      [Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,"
                        + "      [Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin) }"
                        + "  elseif (Test-Path -LiteralPath $p) {"
                        + "    [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($p,"
                        + "      [Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,"
                        + "      [Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin) } }";
        try {
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroy();
                return "Took too long";
            }
            if (p.exitValue() != 0) return "Couldn't clear";
        } catch (Exception e) {
            return "Couldn't clear";
        }
        return "Sent to Recycle Bin";
    }
}
