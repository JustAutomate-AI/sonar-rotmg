package sonar.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Keeps a copy of everything printed, in a file beside the app.
 * <p>
 * Launched from a shortcut there is no console, so anything the app says about itself is said
 * to nobody - and the one time that matters is the time something went wrong, when the only
 * honest answer is "I cannot see what happened". A build meant for diagnosing has to leave a
 * trace whether or not anyone thought to start it from a terminal.
 * <p>
 * Off unless {@code -Dsonar.logfile=<path>} is given, and never fatal: a log that cannot be
 * opened is a shame, not a reason to refuse to start.
 */
public final class RunLog {

    private RunLog() {
    }

    public static void start() {
        String path = System.getProperty("sonar.logfile");
        if (path == null || path.isEmpty()) {
            // The installed build has no console, so without this it has no log at all, and
            // a fault in it can only be reported as a screenshot. It keeps one beside its
            // data; a copy run from source keeps its console and is left alone.
            String data = System.getProperty("sonar.data");
            if (data == null || data.isEmpty()) return;
            path = new File(sonar.DataDir.base(), "sonar.log").getPath();
        }
        try {
            File f = new File(path);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            // truncated per run: the interesting log is always the one from this launch, and an
            // ever-growing file is one nobody reads and everybody has to remember to delete
            OutputStream out = new FileOutputStream(f, false);
            System.setOut(tee(System.out, out));
            System.setErr(tee(System.err, out));
            System.out.println("[runlog] writing to " + f.getAbsolutePath());
        } catch (IOException | RuntimeException cannot) {
            // nothing to say about it, and nowhere to say it
        }
    }

    private static PrintStream tee(PrintStream console, OutputStream file)
            throws java.io.UnsupportedEncodingException {
        return new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                console.write(b);
                file.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                console.write(b, off, len);
                file.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                console.flush();
                file.flush();
            }
        }, true, StandardCharsets.UTF_8.name());
    }
}
