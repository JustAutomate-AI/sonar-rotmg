package sonar.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Carries settings saved under the app's earlier names over to the current ones.
 * <p>
 * Two renames have to be survived. The file itself used to be called
 * realmShark.properties, and the keys inside it used to start with "peach.". An
 * install that has not opened since either change would otherwise come up looking
 * for a file and keys nobody had written yet, and fall back to defaults without
 * saying so - losing the window layout, theme, accent, starred party and wardrobe.
 */
public final class Settings {

    private static final String FILE = "sonar.properties";
    private static final String OLD_FILE = "realmShark.properties";
    private static final String OLD = "peach.", NEW = "sonar.";

    private Settings() {
    }

    /**
     * Must run before anything touches {@code sonar.util.PropertiesManager}, which reads
     * the file as its class loads and would then hold the pre-migration copy for
     * the rest of the session.
     */
    public static void migrate() {
        renameFile();
        renameKeys();
    }

    /**
     * Moves the old file to the new name, content untouched.
     * <p>
     * Written first and only deleted once that has worked, so a failure part way
     * through leaves the original where it was rather than losing it.
     */
    private static void renameFile() {
        File now = sonar.DataDir.file(FILE), before = sonar.DataDir.file(OLD_FILE);
        if (now.isFile() || !before.isFile()) return;
        try {
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(before)) {
                p.load(in);
            }
            try (FileOutputStream out = new FileOutputStream(now)) {
                p.store(out, "Sonar settings");
            }
            if (!before.delete()) before.deleteOnExit();
            System.out.println("Settings: moved " + OLD_FILE + " to " + FILE);
        } catch (Exception e) {
            System.out.println("Settings file was not moved (" + e + ")");
        }
    }

    /** Renames any key still carrying the old prefix. */
    private static void renameKeys() {
        File file = sonar.DataDir.file(FILE);
        if (!file.isFile()) return;
        try {
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                p.load(in);
            }
            List<String> stale = new ArrayList<>();
            for (String name : p.stringPropertyNames()) {
                if (name.startsWith(OLD)) stale.add(name);
            }
            if (stale.isEmpty()) return;
            for (String name : stale) {
                String renamed = NEW + name.substring(OLD.length());
                // a value already saved under the new name is the newer of the two, so it wins
                if (!p.containsKey(renamed)) p.setProperty(renamed, p.getProperty(name));
                p.remove(name);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                p.store(out, "Sonar settings");
            }
            System.out.println("Settings: carried " + stale.size() + " saved values over from the old name");
        } catch (Exception e) {
            // losing the migration costs the saved layout, not the session, so carry on either way
            System.out.println("Settings were not carried over (" + e + ")");
        }
    }
}
