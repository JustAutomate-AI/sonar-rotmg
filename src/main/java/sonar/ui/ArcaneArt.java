package sonar.ui;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sonar.assets.resextractor.ArcaneExtractor;

/**
 * Draws an Arcane Style over a character.
 * <p>
 * The art comes from {@link ArcaneExtractor}, which takes it off the player's own game
 * install. Character art is eight pixels square and an effect frame is sixteen, so an
 * effect is twice the size of the person wearing it and spreads evenly around them. That
 * spread is the awkward part on a crowded row: the damage list draws a portrait on top of
 * the class-coloured bar, and an effect allowed to reach its full width would wash colour
 * across the bar underneath and make it read as a different class. So how far an effect
 * may spread past the portrait is the caller's choice, not this class's - see the
 * {@code bleed} argument.
 * <p>
 * What the game's own shader does, and what this follows:
 * <ul>
 *   <li>The frame is picked by elapsed time times the style's framerate, wrapping at the
 *       frame count the client declares - not at the strip's width, which is three cells
 *       too wide on one effect.</li>
 *   <li>A style carrying a colour treats its strip as a greyscale mask and tints it;
 *       a style without one uses the art as drawn.</li>
 *   <li>The result is laid over the character with ordinary source-over blending, which
 *       is what the shader's own one/one-minus-source-alpha setting amounts to.</li>
 * </ul>
 * The four families that carry no art - pulse, inner glow, hologram and the scrollers'
 * colour work - are a colour over the silhouette and need none of the above.
 */
public final class ArcaneArt {

    /** An effect frame is this many pixels; a character is half it. */
    private static final int FRAME = 16;

    private ArcaneArt() {
    }

    private static Map<String, Object> strips;
    private static Map<String, Integer> frameCounts;

    /**
     * Whether styles are drawn at all. Off unless {@code -Dsonar.arcane} is set, so a
     * release behaves exactly as it did before any of this existed.
     * <p>
     * The effect art is close but not yet exact: the game's shader lays these over a
     * character with blending that has not been matched, and until it has, a style drawn
     * here would be a near-miss of something the wearer sees correctly every day. Fewer
     * than two players in a hundred wear one, so there is nothing to gain by shipping it
     * early and a wrong-looking character to lose.
     */
    public static boolean enabled() {
        String set = System.getProperty("sonar.arcane");
        return set != null && !set.isEmpty() && !set.equalsIgnoreCase("false") && !set.equals("0");
    }

    /** Whether any effect art has been extracted yet. */
    public static synchronized boolean ready() {
        if (!enabled()) return false;
        index();
        return frameCounts != null && !frameCounts.isEmpty();
    }

    private static synchronized void index() {
        if (strips != null) return;
        strips = new HashMap<>();
        frameCounts = new HashMap<>();
        File dir = sonar.DataDir.file(ArcaneExtractor.DIR);
        List<ArcaneExtractor.Effect> effects = ArcaneExtractor.index(dir);
        for (ArcaneExtractor.Effect e : effects) {
            frameCounts.put(e.key, Math.max(1, e.frames));
        }
    }

    /** The strip for one art key, loaded once. */
    private static synchronized BufferedImage strip(String key, boolean scroller) {
        index();
        Object found = strips.get(key);
        if (found == null) {
            found = Boolean.FALSE;
            try {
                File f = new File(sonar.DataDir.file(ArcaneExtractor.DIR),
                        ArcaneExtractor.fileName(key, scroller));
                if (f.isFile()) {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) found = img;
                }
            } catch (Exception e) {
                // that effect stays undrawn; the character still appears
            }
            strips.put(key, found);
        }
        return found instanceof BufferedImage ? (BufferedImage) found : null;
    }

    /** How many frames of a strip the game actually plays. */
    private static int frames(String key, BufferedImage strip) {
        index();
        Integer n = frameCounts.get(key);
        if (n != null && n > 0) return n;
        return Math.max(1, strip.getWidth() / FRAME);
    }

    /**
     * Whether a style has art this can draw. The colour-only families return false:
     * they are real styles, they simply need no overlay from here.
     */
    public static boolean canDraw(ArcaneStyle style) {
        if (!enabled() || style == null) return false;
        ArcaneStyle.Effect e = style.drawable();
        return e != null && strip(e.art, e.family == ArcaneStyle.Family.SCROLLER) != null;
    }

    /**
     * A character with its style applied, as the game's own wardrobe shows it.
     * <p>
     * The effect is confined to the character's own silhouette. That was measured off
     * footage of the game's Customize Character screen rather than assumed: of every
     * pixel a style changed on the default model, 92 to 94 per cent fell inside the
     * silhouette, and every one of the remainder sat within three pixels of its edge,
     * which is video compression and not art. Nothing of a style is drawn beside a
     * character, only on it.
     * <p>
     * This matters more than it sounds. An effect frame is sixteen pixels and a character
     * is eight, so the obvious reading - and the one taken here at first - is that the
     * effect is twice the character's size and spreads evenly around them. It is not: the
     * frame is sampled across the character and clipped to its shape. Drawn the other way
     * a styled player bled colour over whatever was behind them.
     *
     * @param character the drawn character, which is not modified.
     * @param timeMs    how long the effect has been running, which picks the frame.
     * @return a new image the size of the character, or null if nothing could be applied.
     */
    public static java.awt.image.BufferedImage apply(BufferedImage character, ArcaneStyle style, long timeMs) {
        if (!enabled() || character == null || style == null) return null;
        BufferedImage out = new BufferedImage(character.getWidth(), character.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(character, 0, 0, null);
        boolean any = false;
        for (ArcaneStyle.Effect e : style.effects) {
            any |= paint(g, out, e, timeMs);
        }
        g.dispose();
        return any ? out : null;
    }

    /** One effect of a style, onto a character already drawn into {@code out}. */
    private static boolean paint(Graphics2D g, BufferedImage out, ArcaneStyle.Effect e, long timeMs) {
        int w = out.getWidth(), h = out.getHeight();
        if (e.hasArt()) {
            boolean scroller = e.family == ArcaneStyle.Family.SCROLLER;
            BufferedImage art = strip(e.art, scroller);
            if (art == null) return false;
            BufferedImage cell = scroller ? art : frame(art, e, timeMs);
            if (cell == null) return false;
            if (e.colour >= 0) cell = tint(cell, e.colour);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            // SrcAtop is the whole point: the effect lands only where the character
            // already has pixels, which is what the game does
            g2.setComposite(AlphaComposite.SrcAtop.derive(
                    Math.max(0f, Math.min(1f, e.intensity))));
            double scale = Math.max(0.1f, e.scale);
            int size = (int) Math.round(w * scale);
            int ox = (int) Math.round((w - size) / 2.0 + e.offsetX * w);
            int oy = (int) Math.round((h - size) / 2.0 - e.offsetY * h);
            g2.drawImage(cell, ox, oy, size, size, null);
            g2.dispose();
            return true;
        }
        // The colour families carry no art. On the game's own model they recolour the
        // whole silhouette, and the user reports the pulse ones changing only in
        // brightness while keeping their colour, which is what the shader's
        // _PulseSpeed does. Both are a wash over the character's own pixels.
        if (e.colour < 0) return false;
        float amount = Math.max(0f, Math.min(1f, e.intensity));
        if (e.family == ArcaneStyle.Family.PULSE) {
            // a full cycle of brightening and dimming, never inverting the colour
            double phase = Math.sin(timeMs / 1000.0 * Math.max(0.1f, e.speed) * Math.PI * 2);
            amount *= (float) (0.5 + 0.5 * phase);
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcAtop.derive(amount));
        g2.setColor(new Color(e.colour));
        g2.fillRect(0, 0, w, h);
        g2.dispose();
        return true;
    }

    /**
     * Lays a style's effect over a character already drawn in the given box.
     *
     * @param box    where the character was drawn, in device pixels.
     * @param style  the style to draw.
     * @param timeMs how long the effect has been running, which picks the frame.
     * @param bleed  how far the effect may spread past the box, as a fraction of its
     *               width: {@code 0} keeps it strictly inside, {@code 0.5} gives it the
     *               full double size the game uses. Anything drawn over a coloured bar
     *               wants {@code 0}.
     * @return true if something was drawn.
     */
    public static boolean overlay(Graphics2D g, ArcaneStyle style, long timeMs,
                                  Rectangle box, double bleed) {
        if (!enabled() || style == null || box == null || box.width <= 0) return false;
        ArcaneStyle.Effect effect = style.drawable();
        if (effect == null) return false;
        boolean scroller = effect.family == ArcaneStyle.Family.SCROLLER;
        BufferedImage art = strip(effect.art, scroller);
        if (art == null) return false;

        BufferedImage cell = scroller ? art : frame(art, effect, timeMs);
        if (cell == null) return false;
        if (effect.colour >= 0) cell = tint(cell, effect.colour);

        // the effect is twice the character's size; bleed says how much of that is allowed out
        double spread = Math.max(0, Math.min(0.5, bleed));
        double size = box.width * (1 + 2 * spread) * Math.max(0.1f, effect.scale);
        double cx = box.getCenterX() + effect.offsetX * box.width;
        // offsetY nudges the effect up the character, which is where the game puts it
        double cy = box.getCenterY() - effect.offsetY * box.width;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // never outside what the caller allowed, so a bar underneath keeps its own colour
        Shape allowed = new Rectangle(
                (int) Math.floor(box.x - box.width * spread),
                (int) Math.floor(box.y - box.height * spread),
                (int) Math.ceil(box.width * (1 + 2 * spread)),
                (int) Math.ceil(box.height * (1 + 2 * spread)));
        g2.clip(allowed);
        g2.setComposite(AlphaComposite.SrcOver.derive(
                Math.max(0f, Math.min(1f, effect.intensity))));
        g2.drawImage(cell, (int) Math.round(cx - size / 2), (int) Math.round(cy - size / 2),
                (int) Math.round(size), (int) Math.round(size), null);
        g2.dispose();
        return true;
    }

    /** The frame of a strip showing at a given moment. */
    private static BufferedImage frame(BufferedImage strip, ArcaneStyle.Effect effect, long timeMs) {
        int count = frames(effect.art, strip);
        float rate = effect.framerate > 0 ? effect.framerate : 30f;
        int i = (int) Math.floorMod((long) (timeMs / 1000.0 * rate), count);
        int x = i * FRAME;
        if (x + FRAME > strip.getWidth()) return null;
        return strip.getSubimage(x, 0, FRAME, Math.min(FRAME, strip.getHeight()));
    }

    private static final Map<String, BufferedImage> TINTED = new HashMap<>();

    /**
     * Recolours a greyscale mask. The mask's brightness becomes how much of the style's
     * colour shows, which is what makes one fire strip serve eighteen shades of fire.
     */
    private static synchronized BufferedImage tint(BufferedImage mask, int colour) {
        String key = System.identityHashCode(mask) + ":" + colour;
        BufferedImage done = TINTED.get(key);
        if (done != null) return done;
        int w = mask.getWidth(), h = mask.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int cr = (colour >> 16) & 255, cg = (colour >> 8) & 255, cb = colour & 255;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = mask.getRGB(x, y);
                int a = argb >>> 24;
                if (a == 0) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                int r = (argb >> 16) & 255, gg = (argb >> 8) & 255, b = argb & 255;
                // the masks are grey, so any channel is the brightness; the mean is safe
                // for the few that are not quite
                int level = (r + gg + b) / 3;
                out.setRGB(x, y, (a << 24) | ((cr * level / 255) << 16)
                        | ((cg * level / 255) << 8) | (cb * level / 255));
            }
        }
        if (TINTED.size() > 400) TINTED.clear();
        TINTED.put(key, out);
        return out;
    }

    /** Forgets the loaded art, so a fresh extraction is picked up without a restart. */
    public static synchronized void reload() {
        strips = null;
        frameCounts = null;
        TINTED.clear();
    }
}
