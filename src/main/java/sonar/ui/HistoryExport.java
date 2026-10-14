package sonar.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Bundles every saved day of instance history (run/history/*.json - the same
 * slimmed Live, Instance and Players data the Instances page replays, kept
 * for {@link InstanceHistory#KEEP_DAYS} days) into one zip, for a tester to
 * hand back for calibrating the potential-damage estimate (see
 * LeechAnalysis) against real play instead of assumptions.
 * <p>
 * Exports exactly what is already saved locally for the Instances page to
 * work - nothing new is collected, and nothing is sent anywhere by this; it
 * only writes a file, on request, for the person running Sonar to choose
 * whether and how to share.
 */
final class HistoryExport {

    private HistoryExport() {
    }

    /**
     * A day of history. These have been gzipped since the saved instances
     * were made to take less disk; the plain ones are older files kept from
     * before that. The export was still only looking for the plain ones, so
     * an account with months of runs in it reported nothing saved yet.
     */
    static boolean isHistoryFile(String name) {
        return name != null && (name.endsWith(".json") || name.endsWith(".json.gz"));
    }

    /** Zips every saved history day into run/exports. Null when there is nothing saved yet. */
    static File exportAll() throws IOException {
        File historyDir = sonar.DataDir.file("history");
        File[] files = historyDir.listFiles((d, n) -> isHistoryFile(n));
        if (files == null || files.length == 0) return null;

        File dir = sonar.DataDir.file("exports");
        dir.mkdirs();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File zip = new File(dir, "Sonar-history-" + stamp + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            for (File f : files) {
                out.putNextEntry(new ZipEntry(f.getName()));
                try (FileInputStream in = new FileInputStream(f)) {
                    in.transferTo(out);
                }
                out.closeEntry();
            }
        }
        return zip;
    }

    /** Best-effort only - a failure here should never be treated as the export itself failing. */
    static void reveal(File file) {
        try {
            java.awt.Desktop.getDesktop().browseFileDirectory(file);
        } catch (Throwable e) {
            try {
                java.awt.Desktop.getDesktop().open(file.getParentFile());
            } catch (Throwable ignored) {
                // the file is still right there in run/exports even if nothing could open it
            }
        }
    }
}
