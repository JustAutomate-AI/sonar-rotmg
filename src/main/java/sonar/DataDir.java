package sonar;

import java.io.File;

/**
 * Where Sonar keeps its settings, saved characters, history and extracted assets.
 * <p>
 * Run from a folder you unzipped, everything sits in that folder, which is how it
 * has always worked and what a portable copy should do. Installed properly, the
 * program lives somewhere the user cannot write to, so the data goes to the place
 * Windows keeps per-user application data instead.
 * <p>
 * The installed build says so with {@code -Dsonar.data}, set by the packaged
 * launcher. Nothing guesses: without that property this behaves exactly as every
 * previous version did, so an existing install keeps its files where they are.
 */
public final class DataDir {

    private static final File BASE = resolve();

    private DataDir() {
    }

    private static File resolve() {
        String set = System.getProperty("sonar.data");
        if (set == null || set.trim().isEmpty()) {
            // portable: the working directory, as before
            return new File("").getAbsoluteFile();
        }
        String path = set.trim();
        // the packaged launcher passes the token rather than a path it cannot expand itself
        // The installed build asks for one of these rather than a path it cannot expand
        // itself. %APPDATA% is what the Windows installer passes: the program it installs
        // is removed wholesale when a new version replaces it, so nothing that belongs to
        // the user can be kept beside it.
        if (path.equalsIgnoreCase("%LOCALAPPDATA%") || path.equalsIgnoreCase("%APPDATA%")) {
            String folder = System.getenv(path.substring(1, path.length() - 1).toUpperCase());
            if (folder == null || folder.isEmpty()) return new File("").getAbsoluteFile();
            path = new File(folder, "Sonar").getPath();
        }
        File dir = new File(path);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            System.out.println("Could not use " + dir + " for data; falling back to the current folder");
            return new File("").getAbsoluteFile();
        }
        return dir.getAbsoluteFile();
    }

    /** The folder everything below lives in. */
    public static File base() {
        return BASE;
    }

    /** A file in the data folder, by name. */
    public static File file(String name) {
        return new File(BASE, name);
    }

    /** A folder in the data folder, created if it is not there yet. */
    public static File dir(String name) {
        File d = new File(BASE, name);
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** A path inside the data folder, for the places that want a string. */
    public static String path(String name) {
        return file(name).getPath();
    }
}
