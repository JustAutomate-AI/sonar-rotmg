package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * A question asked in Sonar's own colours rather than the system's.
 * <p>
 * The update flow used to put a grey Windows dialog in the middle of an app
 * that is otherwise painted end to end, which read as something having gone
 * wrong rather than as part of the app. This is the same question in the same
 * palette, with the same buttons used everywhere else.
 */
public final class SonarDialog {

    private SonarDialog() {
    }

    /**
     * Shows a question and waits for an answer.
     *
     * @return the index of the option chosen, or the last one if it was dismissed.
     */
    public static int ask(Component parent, String title, String message, String... options) {
        if (options.length == 0) options = new String[]{"OK"};
        Window owner = parent instanceof Window ? (Window) parent
                : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);

        JPanel body = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = SonarTheme.quality(g.create());
                g2.setColor(SonarTheme.SURFACE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // one warm rule along the top, the same mark the installer window carries
                g2.setPaint(new GradientPaint(0, 0, SonarTheme.accent(), getWidth(), 0, SonarTheme.CORAL));
                g2.fillRect(0, 0, getWidth(), 3);
                g2.setColor(SonarTheme.LINE_STRONG);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };
        body.setBorder(BorderFactory.createEmptyBorder(26, 28, 20, 28));

        JLabel heading = new JLabel(title);
        heading.setFont(SonarTheme.bold(17f));
        heading.setForeground(SonarTheme.TEXT);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JLabel text = new JLabel("<html><body style='width:330px'>" + escape(message) + "</body></html>");
        text.setFont(SonarTheme.font(12.5f));
        text.setForeground(SonarTheme.MUTED);

        Box head = Box.createVerticalBox();
        head.setOpaque(false);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(heading);
        head.add(text);

        int[] answer = {options.length - 1};
        Box buttons = Box.createHorizontalBox();
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(22, 0, 0, 0));
        buttons.add(Box.createHorizontalGlue());
        for (int i = 0; i < options.length; i++) {
            int index = i;
            // the first option is the one being offered, so it leads
            SonarButton button = new SonarButton(options[i], i == 0, () -> {
                answer[0] = index;
                dialog.dispose();
            });
            if (i > 0) buttons.add(Box.createHorizontalStrut(8));
            buttons.add(button);
        }

        body.setOpaque(false);
        body.add(head, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(body);
        dialog.getRootPane().setBackground(SonarTheme.SURFACE);

        // escape answers with the last option, the way dismissing a system dialog does
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return answer[0];
    }

    /**
     * A taller cousin of {@link #ask} for release notes: the body is already HTML rather
     * than escaped text, it scrolls when there is more than fits, and there is one button
     * because there is nothing to decide. Same frame, same warm rule along the top.
     */
    public static void notes(Component parent, String title, String subtitle, String bodyHtml) {
        Window owner = parent instanceof Window ? (Window) parent
                : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);

        JPanel body = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = SonarTheme.quality(g.create());
                g2.setColor(SonarTheme.SURFACE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setPaint(new GradientPaint(0, 0, SonarTheme.accent(), getWidth(), 0, SonarTheme.CORAL));
                g2.fillRect(0, 0, getWidth(), 3);
                g2.setColor(SonarTheme.LINE_STRONG);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(26, 28, 20, 28));

        JLabel heading = new JLabel(title);
        heading.setFont(SonarTheme.bold(17f));
        heading.setForeground(SonarTheme.TEXT);
        JLabel sub = new JLabel(subtitle);
        sub.setFont(SonarTheme.font(12f));
        sub.setForeground(SonarTheme.FAINT);
        sub.setBorder(BorderFactory.createEmptyBorder(2, 0, 12, 0));

        // An editor pane rather than a label: a label's HTML wraps against the width written
        // into the markup, which Swing does not honour once the label is inside a scroll pane -
        // every line then ran off the right edge. An editor pane wraps against the width it is
        // actually given, which is the one that matches the dialog.
        JEditorPane text = new JEditorPane("text/html", "<html><body>" + bodyHtml + "</body></html>");
        text.setEditable(false);
        text.setOpaque(false);
        text.setBorder(null);
        text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        text.setFont(SonarTheme.font(12.5f));
        text.setForeground(SonarTheme.MUTED);
        text.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(text,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        // the pane has to be told how wide it will be before it can say how tall that makes it
        text.setSize(new Dimension(424, Short.MAX_VALUE));
        // tall enough to be worth reading, short enough to stay a dialog on a laptop
        int wanted = Math.min(430, Math.max(120, text.getPreferredSize().height));
        scroll.setPreferredSize(new Dimension(440, wanted));

        Box head = Box.createVerticalBox();
        head.setOpaque(false);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(heading);
        head.add(sub);
        head.add(scroll);

        Box buttons = Box.createHorizontalBox();
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        buttons.add(Box.createHorizontalGlue());
        buttons.add(new SonarButton("Got it", true, dialog::dispose));

        body.add(head, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(body);
        dialog.getRootPane().setBackground(SonarTheme.SURFACE);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}
