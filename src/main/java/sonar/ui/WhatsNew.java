package sonar.ui;

import sonar.util.PropertiesManager;

import java.awt.Window;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * What changed, shown once after an update has installed itself.
 * <p>
 * An update that closes the app, swaps its files and opens it again leaves no trace of
 * what it did. The version in the corner goes up and everything else looks the same, so
 * a feature that was added lands only if the user happens to go looking for it. This
 * puts the release's own notes in front of them once, the first time the new version
 * starts, and then never again.
 * <p>
 * It reads the changelog shipped inside the jar rather than the release page, so it
 * works with no network and always describes the build it is part of. Skipping versions
 * is normal - somebody who was three releases behind gets all three sections, newest
 * first, rather than only the one they happened to land on.
 */
final class WhatsNew {

    /** The version whose notes have already been seen. */
    private static final String KEY = "sonar.notesSeenFor";

    private WhatsNew() {
    }

    /**
     * Shows the notes if this is the first run of a version the user has not seen notes
     * for, and records that they have seen them.
     * <p>
     * A fresh install sees nothing: there is no previous version to have come from, and a
     * changelog is a poor first impression of an app somebody has just chosen to install.
     */
    static void showIfUpdated(Window parent) {
        String now = Updater.current();
        String seen = PropertiesManager.getProperty(KEY);
        PropertiesManager.setProperties(KEY, now);
        if (seen == null || seen.trim().isEmpty() || seen.equals(now)) return;
        if (compare(seen, now) >= 0) return; // went backwards, or sideways; say nothing

        List<String[]> sections = sectionsBetween(seen, now);
        if (sections.isEmpty()) return;
        SonarDialog.notes(parent, "Sonar " + now,
                sections.size() == 1 ? "Updated from " + seen
                        : "Updated from " + seen + ", across " + sections.size() + " releases",
                html(sections));
    }

    /** Every changelog section above {@code from} and up to {@code to}, newest first. */
    private static List<String[]> sectionsBetween(String from, String to) {
        List<String[]> out = new ArrayList<>();
        String version = null;
        StringBuilder body = new StringBuilder();
        try (InputStream in = WhatsNew.class.getResourceAsStream("/CHANGELOG.md")) {
            if (in == null) return out;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("## ")) {
                    keep(out, version, body, from, to);
                    version = line.substring(3).trim();
                    body.setLength(0);
                } else if (version != null) {
                    body.append(line).append('\n');
                }
            }
            keep(out, version, body, from, to);
        } catch (Exception ignored) {
            // without the file there are no notes to show, which is survivable
        }
        return out;
    }

    private static void keep(List<String[]> out, String version, StringBuilder body, String from, String to) {
        if (version == null) return;
        // a heading like "2.7.1 - something" still counts; only the number is compared
        String number = version.split("\\s")[0];
        if (!number.matches("\\d+(\\.\\d+)*")) return;
        if (compare(number, from) > 0 && compare(number, to) <= 0) {
            out.add(new String[]{number, body.toString()});
        }
    }

    /**
     * Turns the markdown of those sections into the small subset the dialog renders.
     * <p>
     * Deliberately flat: no nested elements, just text and line breaks. Swing will wrap a
     * run of text against the width set on the body, but it does not reliably carry that
     * width into a nested div - and the first version of this, which wrapped every line in
     * one, ran every sentence straight off the right edge of the dialog.
     */
    private static String html(List<String[]> sections) {
        StringBuilder b = new StringBuilder();
        for (int s = 0; s < sections.size(); s++) {
            String[] section = sections.get(s);
            if (s > 0) b.append("<br>");
            if (sections.size() > 1) {
                b.append("<font color='#7a6f87'><b>").append(esc(section[0])).append("</b></font><br>");
            }
            for (String line : section[1].split("\n")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith(">")) continue;
                if (t.startsWith("**") && t.endsWith("**")) {
                    b.append("<br><b>").append(esc(t.substring(2, t.length() - 2))).append("</b><br>");
                } else if (t.startsWith("- ")) {
                    b.append("&#8226; ").append(inline(t.substring(2))).append("<br>");
                } else {
                    b.append(inline(t)).append("<br>");
                }
            }
        }
        return b.toString();
    }

    /** Bold and code spans, which is as much markdown as these notes ever use mid-line. */
    private static String inline(String text) {
        StringBuilder b = new StringBuilder();
        boolean bold = false, code = false;
        for (int i = 0; i < text.length(); i++) {
            if (text.startsWith("**", i)) {
                b.append(bold ? "</b>" : "<b>");
                bold = !bold;
                i++;
            } else if (text.charAt(i) == '`') {
                b.append(code ? "</font>" : "<font color='#a98cf0'>");
                code = !code;
            } else {
                b.append(esc(String.valueOf(text.charAt(i))));
            }
        }
        if (bold) b.append("</b>");
        if (code) b.append("</font>");
        return b.toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Compares two dotted version numbers. Returns negative when {@code a} is older.
     * Missing parts count as zero, so 2.9 and 2.9.0 are the same version.
     */
    static int compare(String a, String b) {
        String[] x = a.trim().split("\\."), y = b.trim().split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = part(x, i), q = part(y, i);
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int part(String[] parts, int i) {
        if (i >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[i].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
