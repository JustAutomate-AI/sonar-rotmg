package sonar.ui;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * Line icons for the navigation rail, drawn as vectors so they stay sharp at any display scale.
 */
final class Glyph {

    private Glyph() {
    }

    /** Draws the icon for a section inside a box of the given size. */
    static void paint(Graphics2D g, String section, double x, double y, double size, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y);
        g2.scale(size / 20, size / 20);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double p = new Path2D.Double();
        switch (section) {
            case "Live":
                line(p, 1, 10, 5, 10, 8, 3, 12, 17, 15, 10, 19, 10);
                break;
            case "Players":
                g2.draw(new Ellipse2D.Double(2.5, 3.5, 5.5, 5.5));
                p.moveTo(1, 16.5);
                p.curveTo(1, 11.5, 9.5, 11.5, 9.5, 16.5);
                line(p, 12, 6, 19, 6);
                line(p, 12, 10.5, 19, 10.5);
                line(p, 12, 15, 17, 15);
                break;
            case "Instance":
                line(p, 3, 17, 3, 6, 10, 2, 17, 6, 17, 17, 3, 17);
                line(p, 8, 17, 8, 11, 12, 11, 12, 17);
                break;
            case "Analysis":
                line(p, 2, 17, 18, 17);
                line(p, 5, 17, 5, 12);
                line(p, 10.5, 17, 10.5, 7);
                line(p, 16, 17, 16, 3);
                break;
            case "Vault":
                g2.draw(new java.awt.geom.RoundRectangle2D.Double(2.5, 2.5, 15, 15, 3, 3));
                g2.draw(new Ellipse2D.Double(6.5, 6.5, 7, 7));
                line(p, 10, 6.5, 10, 4.5);
                break;
            case "Exaltation":
                line(p, 10, 1, 12.1, 7.1, 18.6, 7.2, 13.4, 11.1, 15.3, 17.3, 10, 13.6, 4.7, 17.3, 6.6, 11.1, 1.4, 7.2, 7.9, 7.1, 10, 1);
                break;
            case "Collection":
                // a chest with its lid banded
                g2.draw(new java.awt.geom.RoundRectangle2D.Double(2.5, 6.5, 15, 11, 2, 2));
                line(p, 2.5, 10.5, 17.5, 10.5);
                line(p, 10, 10.5, 10, 14);
                p.moveTo(5, 6.5);
                p.curveTo(5, 2.5, 15, 2.5, 15, 6.5);
                break;
            case "Party finder":
                // three figures side by side, the party the finder is for
                g2.draw(new Ellipse2D.Double(2.2, 4.4, 5, 5));
                p.moveTo(1, 17.5);
                p.curveTo(1, 12, 8.8, 12, 8.8, 17.5);
                g2.draw(new Ellipse2D.Double(11.8, 4.4, 5, 5));
                p.moveTo(10.6, 17.5);
                p.curveTo(10.6, 12, 18.4, 12, 18.4, 17.5);
                break;
            case "Wardrobe":
                // a coat hanger: the hook, the shoulders and the bar between them
                g2.draw(new java.awt.geom.Arc2D.Double(8, 2, 4, 4, 200, 280, java.awt.geom.Arc2D.OPEN));
                line(p, 10, 6, 10, 8);
                line(p, 10, 8, 2.5, 14.5, 17.5, 14.5, 10, 8);
                break;
            case "Puzzle":
                // a three by three grid, the shape of the puzzle itself
                for (int gx = 0; gx < 3; gx++) {
                    for (int gy = 0; gy < 3; gy++) {
                        double cxx = 3 + gx * 5.5, cyy = 3 + gy * 5.5;
                        if (gx == 1 || gy == 1) g2.fill(new java.awt.geom.RoundRectangle2D.Double(cxx, cyy, 4, 4, 1, 1));
                        else g2.draw(new java.awt.geom.RoundRectangle2D.Double(cxx + 0.5, cyy + 0.5, 3, 3, 1, 1));
                    }
                }
                break;
            case "Settings":
                g2.draw(new Ellipse2D.Double(7, 7, 6, 6));
                // eight teeth around the hub
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    line(p, 10 + Math.cos(a) * 6.2, 10 + Math.sin(a) * 6.2, 10 + Math.cos(a) * 8.6, 10 + Math.sin(a) * 8.6);
                }
                break;
            case "Calculator":
                g2.draw(new java.awt.geom.RoundRectangle2D.Double(4, 2.5, 12, 15, 3, 3));
                g2.draw(new java.awt.geom.Line2D.Double(7, 6.5, 13, 6.5));
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 3; col++) g2.fill(new java.awt.geom.Ellipse2D.Double(6.4 + col * 3, 10 + row * 3.2, 1.3, 1.3));
                }
                break;
            case "More":
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 2; col++) {
                        g2.draw(new java.awt.geom.RoundRectangle2D.Double(3 + col * 8, 3 + row * 8, 6, 6, 2, 2));
                    }
                }
                break;
            case "Chat":
                line(p, 3, 4, 17, 4, 17, 13, 9, 13, 5, 17, 5, 13, 3, 13, 3, 4);
                break;
            case "Key-pops":
                g2.draw(new Ellipse2D.Double(2, 7, 7, 7));
                line(p, 9, 10.5, 18, 10.5);
                line(p, 15, 10.5, 15, 14);
                line(p, 18, 10.5, 18, 13);
                break;
            case "Security":
                line(p, 10, 2, 17, 5, 17, 10, 10, 18, 3, 10, 3, 5, 10, 2);
                break;
            case "Characters":
                g2.draw(new Ellipse2D.Double(6.5, 2.5, 7, 7));
                p.moveTo(3, 18);
                p.curveTo(3, 11.5, 17, 11.5, 17, 18);
                break;
            case "Statistics":
                line(p, 4, 17, 4, 9);
                line(p, 10, 17, 10, 3);
                line(p, 16, 17, 16, 12);
                line(p, 2, 17, 18, 17);
                break;
            case "Daily Quests":
                line(p, 5, 2, 15, 2, 15, 18, 5, 18, 5, 2);
                line(p, 8, 7, 12, 7);
                line(p, 8, 11, 12, 11);
                break;
            case "My Info":
                g2.draw(new Ellipse2D.Double(2, 2, 16, 16));
                line(p, 10, 9, 10, 14);
                g2.fill(new Ellipse2D.Double(9.1, 5.4, 1.8, 1.8));
                break;
            case "DPS Logger":
                line(p, 3, 5, 17, 5);
                line(p, 3, 10, 13, 10);
                line(p, 3, 15, 9, 15);
                break;
            default:
                g2.draw(new Ellipse2D.Double(6, 6, 8, 8));
        }
        g2.draw(p);
        g2.dispose();
    }

    private static final java.util.HashMap<String, Object> ITEM_ICONS = new java.util.HashMap<>();
    private static final java.util.HashMap<Integer, Object> ITEM_ICONS_BY_ID = new java.util.HashMap<>();

    /**
     * The game's picture for an item id, or null when there is none.
     * <p>
     * Preferred over the by-name lookup wherever the id is known. Names are
     * not unique: a Shiny carries the same display name as the plain item, so
     * only one of the two can own that name - which is why Shinies, and any
     * item whose name has not been registered yet, came out blank.
     */
    static Image itemIcon(int id) {
        if (id <= 0) return null;
        Object icon = ITEM_ICONS_BY_ID.get(id);
        if (icon == null) {
            icon = Boolean.FALSE;
            try {
                Image found = sonar.assets.ImageBuffer.getOutlinedIcon(id, 24).getImage();
                if (found != null && found.getWidth(null) > 0) icon = found;
            } catch (Throwable e) {
                // no picture for this id
            }
            ITEM_ICONS_BY_ID.put(id, icon);
        }
        return icon instanceof Image ? (Image) icon : null;
    }

    /** Draws the game's own picture of an item by id. Returns false when there is none, so the caller can leave no gap. */

    /**
     * Non-zero while a shareable picture is being drawn, holding how many real pixels that
     * picture is getting for each one its layout works in. Set by {@link ResultsImage.Sheet}
     * and cleared when the picture is finished; the render is single-threaded.
     * <p>
     * The game's art is pixel art, eight or sixteen pixels square for an item, and a run's
     * picture asks for it in a box of some unrelated size - twenty pixels, fifteen on the
     * per-boss page. Two and a half source pixels to the box pixel means some rows of the
     * sprite come out two pixels tall and some three, which is the mush you see when you
     * zoom in on a posted picture looking for what somebody was wearing. At export resolution
     * there is room to fix that: the sprite is drawn at a whole multiple of its own pixels,
     * centred in the box it was given, so every row comes out the same height and the enchant
     * gem in the corner stays a gem.
     */
    static int exportScale;

    /**
     * Paints one of the game's pictures into a square box, never smoothed, and on whole
     * source pixels when there is the resolution for it. See {@link #exportScale}.
     */
    private static void blit(Graphics2D g, Image img, double x, double y, int size) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int w = img.getWidth(null), h = img.getHeight(null);
        int box = (int) Math.round(size * (double) exportScale);
        int mult = exportScale > 1 && w > 0 && h > 0 ? box / Math.max(w, h) : 0;
        if (mult < 1) {
            // either an ordinary draw, or a box too small to hold even one whole copy of the
            // sprite, where shrinking it smoothly is the lesser evil
            g.drawImage(img, (int) Math.round(x), (int) Math.round(y), size, size, null);
            return;
        }
        // straight onto the real pixel grid: setTransform leaves the clip where it is, so
        // this draws in the same place, only on whole pixels
        java.awt.geom.AffineTransform was = g.getTransform();
        int dw = w * mult, dh = h * mult;
        java.awt.geom.Point2D at = was.transform(new java.awt.geom.Point2D.Double(x, y), null);
        g.setTransform(new java.awt.geom.AffineTransform());
        g.drawImage(img, (int) Math.round(at.getX() + (box - dw) / 2.0),
                (int) Math.round(at.getY() + (box - dh) / 2.0), dw, dh, null);
        g.setTransform(was);
    }

    static boolean item(Graphics2D g, int id, double x, double y, int size) {
        return draw(g, itemIcon(id), x, y, size);
    }

    /** The game's picture for an item name, or null when there is none; the same lookup {@link #item} draws with. */
    static Image itemIcon(String name) {
        Object icon = ITEM_ICONS.get(name);
        if (icon == null) {
            icon = Boolean.FALSE;
            Integer id = LiveModel.ITEM_IDS.get(name);
            try {
                if (id != null) {
                    Image found = sonar.assets.ImageBuffer.getOutlinedIcon(id, 24).getImage();
                    if (found != null && found.getWidth(null) > 0) icon = found;
                }
            } catch (Throwable e) {
                // no picture for this item
            }
            // an id may only become known later, so only pictures that were found are remembered for good
            if (icon != Boolean.FALSE || id != null) ITEM_ICONS.put(name, icon);
        }
        return icon instanceof Image ? (Image) icon : null;
    }

    static boolean item(Graphics2D g, String name, double x, double y, int size) {
        return draw(g, itemIcon(name), x, y, size);
    }

    /**
     * Small line symbols drawn on buttons, in a 20x20 box scaled to size.
     * "share" is the arrow that leaves: up out of a tray, then away to the right.
     */
    static void action(Graphics2D g, String name, double x, double y, int size, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y);
        g2.scale(size / 20.0, size / 20.0);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
        if ("share".equals(name)) {
            // out of the tray
            p.moveTo(3, 12);
            p.lineTo(3, 17.5);
            p.lineTo(17, 17.5);
            p.lineTo(17, 12);
            // up, then away to the right
            p.moveTo(10, 14);
            p.lineTo(10, 6.5);
            p.lineTo(17, 6.5);
            g2.draw(p);
            // the head of the arrow
            java.awt.geom.Path2D.Double head = new java.awt.geom.Path2D.Double();
            head.moveTo(14, 3.2);
            head.lineTo(18.4, 6.5);
            head.lineTo(14, 9.8);
            head.closePath();
            g2.fill(head);
        } else if ("share-star".equals(name)) {
            // the same arrow leaving, but what it carries is one player rather than the board
            java.awt.geom.Path2D.Double star = new java.awt.geom.Path2D.Double();
            double cx = 7.6, cy = 11.4, outer = 6.4, inner = 2.7;
            for (int i = 0; i < 10; i++) {
                double r = (i % 2 == 0) ? outer : inner;
                double a = -Math.PI / 2 + i * Math.PI / 5;
                double px = cx + Math.cos(a) * r, py = cy + Math.sin(a) * r;
                if (i == 0) star.moveTo(px, py);
                else star.lineTo(px, py);
            }
            star.closePath();
            g2.fill(star);
            // away to the upper right, clear of the star's points
            p.moveTo(14.2, 8.4);
            p.lineTo(17.6, 5);
            g2.draw(p);
            java.awt.geom.Path2D.Double head = new java.awt.geom.Path2D.Double();
            head.moveTo(13.6, 3.4);
            head.lineTo(19.2, 3.4);
            head.lineTo(19.2, 9);
            head.closePath();
            g2.fill(head);
        }
        g2.dispose();
    }

    /**
     * The game's own enchant mark: a small gem in the lower right of an item,
     * coloured by the best enchant on it, exactly as the game's own inventory
     * draws it. Tier 0 draws nothing.
     * <p>
     * Sits alongside the glow rather than replacing it - the glow says an item
     * is enchanted from across the window, the gem says how well up close.
     */
    private static final java.util.HashMap<String, Object> ENCHANT_GEMS = new java.util.HashMap<>();

    /**
     * The game's own rarity mark in the lower right of an item: one green
     * diamond for one enchant, two blue for two, three violet for three, four
     * gold for four. {@code enchants} is the item's enchant names as Sonar
     * stores them, one per line. Nothing is drawn for plain gear.
     * <p>
     * The mark counts the slots rather than weighing what is in them, which
     * is why a ring holding one unique enchant shows a single green diamond
     * however rare that enchant is on its own.
     */
    static void enchant(Graphics2D g, String enchants, double x, double y, int size) {
        Image gem = rarity(EnchantSprites.slots(enchants));
        if (gem == null) return;
        // a shade over a third of the item, which is the proportion the game itself draws
        int wide = Math.max(5, (int) Math.round(size * 0.38));
        Graphics2D g2 = (Graphics2D) g.create();
        blit(g2, gem, x + size - wide, y + size - wide, wide);
        g2.dispose();
    }

    /**
     * The soft halo behind an enchanted item, in the colour of its rarity.
     * Rounded rings fading outward, so it reads as a glow rather than a
     * border. Drawn before the item itself.
     *
     * @param tier the item's highest enchant rarity, 1 to 4; nothing is drawn for 0.
     */
    static void enchantGlow(Graphics2D g, int tier, double x, double y, int size) {
        enchantGlow(g, tier, x, y, size, 6);
    }

    /**
     * The glow for an enchanted item, drawn so that it reads as the tile's own edge.
     * <p>
     * It used to be rings floating outside the tile while the tile kept its ordinary
     * grey border, so at a glance there were two separate edges and the colour was the
     * fainter of them. Now the bloom ends in a solid ring sitting exactly on the border,
     * and the tile is drawn without a grey one over the top: the glow becomes the
     * outline rather than a halo behind it.
     *
     * @param radius the corner radius of the tile this sits on, so the ring follows
     *               the same curve as the plate rather than cutting across it.
     */
    static void enchantGlow(Graphics2D g, int tier, double x, double y, int size, double radius) {
        if (tier <= 0) return;
        Color glow = vivid(CalcPane.tierColor(tier));
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        bloom(g2, glow, x, y, size, radius);
        // and a quiet line on the edge itself, so the light has something to come off and
        // the tile keeps a defined rim. Kept well below the bloom: at full strength this
        // was the whole effect and it read as a border with a smudge round it.
        g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 150));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(SonarTheme.round(x + 0.8, y + 0.8, size - 1.6, size - 1.6, radius));
        g2.dispose();
    }

    /**
     * The bloom on its own, for gear drawn with no tile beneath it.
     * <p>
     * The breakdown table puts bare item pictures in a dense row. A solid ring there
     * would draw a box around an icon that has no box, so these keep the soft halo and
     * only gear sitting on a plate gets the ring.
     */
    static void enchantHalo(Graphics2D g, String enchants, double x, double y, int size) {
        int tier = enchantTier(enchants);
        if (tier <= 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        bloom(g2, vivid(CalcPane.tierColor(tier)), x, y, size, 6);
        g2.dispose();
    }

    /**
     * Light spreading outward from the edge and fading into the background.
     * <p>
     * This was four rings drawn one pixel apart, which at a glance was not a glow at all
     * but a thick band - four hard edges where there should be none. A glow has no edges,
     * so it is made the way a glow actually is: the shape is drawn once into a small
     * image of its own, blurred, and that is what gets painted. The falloff is smooth all
     * the way out to nothing, and it reaches far enough from the tile to read as light
     * rather than as another border.
     */
    private static void bloom(Graphics2D g, Color glow, double x, double y, int size, double radius) {
        int spread = Math.max(6, Math.round(size * 0.34f));
        java.awt.image.BufferedImage sprite = glowSprite(glow, size, radius, spread);
        if (sprite == null) return;
        g.drawImage(sprite, (int) Math.round(x) - spread, (int) Math.round(y) - spread, null);
    }

    private static final java.util.HashMap<String, Object> GLOWS = new java.util.HashMap<>();

    /** One blurred glow, kept because blurring is far too slow to do inside a paint. */
    private static synchronized java.awt.image.BufferedImage glowSprite(Color glow, int size,
                                                                        double radius, int spread) {
        String key = glow.getRGB() + ":" + size + ":" + radius + ":" + spread;
        Object found = GLOWS.get(key);
        if (found == null) {
            found = Boolean.FALSE;
            try {
                int dim = size + spread * 2;
                java.awt.image.BufferedImage src =
                        new java.awt.image.BufferedImage(dim, dim, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D gg = SonarTheme.quality(src.createGraphics());
                // the edge is what gives off the light, so that is what gets blurred - a
                // filled shape would light the middle too and wash out the item on top
                gg.setColor(glow);
                gg.setStroke(new BasicStroke(Math.max(2f, size * 0.07f)));
                gg.draw(SonarTheme.round(spread + 0.8, spread + 0.8, size - 1.6, size - 1.6, radius));
                gg.dispose();
                found = blur(blur(src, spread, true), spread, false);
            } catch (Throwable e) {
                // without it an enchanted item simply has no glow
            }
            if (GLOWS.size() > 160) GLOWS.clear();
            GLOWS.put(key, found);
        }
        return found instanceof java.awt.image.BufferedImage ? (java.awt.image.BufferedImage) found : null;
    }

    /**
     * One pass of a Gaussian blur, across or down. Two passes give the same result as a
     * square kernel at a fraction of the work, which is why this is separable.
     */
    private static java.awt.image.BufferedImage blur(java.awt.image.BufferedImage src,
                                                     int spread, boolean horizontal) {
        int n = Math.max(1, spread) * 2 + 1;
        float[] kernel = new float[n];
        float sigma = Math.max(1f, spread / 2f), sum = 0;
        for (int i = 0; i < n; i++) {
            float d = i - spread;
            kernel[i] = (float) Math.exp(-(d * d) / (2 * sigma * sigma));
            sum += kernel[i];
        }
        for (int i = 0; i < n; i++) kernel[i] /= sum;
        java.awt.image.Kernel k = horizontal
                ? new java.awt.image.Kernel(n, 1, kernel)
                : new java.awt.image.Kernel(1, n, kernel);
        java.awt.image.ConvolveOp op = new java.awt.image.ConvolveOp(
                k, java.awt.image.ConvolveOp.EDGE_ZERO_FILL, null);
        return op.filter(src, null);
    }

    /**
     * An enchant colour pushed toward its purest form for the glow.
     * <p>
     * The tier colours are chosen to sit quietly in a list of text, which left the glow
     * looking washed out against a dark plate. Lifting the saturation and brightness
     * gives the edge some carry without changing which colour it is - a blue enchant is
     * still unmistakably the same blue, only lit.
     */
    private static Color vivid(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        // grey, the no-enchant tier, has no hue to push and would only turn muddy
        float saturation = hsb[1] < 0.12f ? hsb[1] : Math.min(1f, hsb[1] * 1.5f);
        float hue = hsb[0] * 360f;
        // Divine's gold sits at 39 degrees, which is amber rather than yellow, and
        // saturating an amber only drives it further towards orange - at full strength it
        // came out looking like fire instead of gold. Warm hues are carried up towards
        // yellow to compensate; nothing else is near this band, so green, blue and violet
        // keep the hue they were given.
        if (hue > 25f && hue < 55f) hue = Math.min(50f, hue + 11f);
        // Deepened rather than brightened. The damage list draws these over the
        // class-coloured bars, which are themselves bright greens, blues and violets, and a
        // bright enchant glow sat straight on top of a bar of the same family and vanished
        // into it - worse, it read as part of the bar. Holding the saturation up but taking
        // the brightness down keeps each tier obviously its own colour while staying darker
        // than anything it is drawn over, so the two never merge.
        return Color.getHSBColor(hue / 360f, saturation, hsb[2] * 0.72f);
    }

    /** The enchant tier on an item, 0 when it has none - the colour its tile should take. */
    static int enchantTier(String enchants) {
        return EnchantSprites.slots(enchants);
    }

    /**
     * The same, for anywhere that has the item's enchants but not the rarity
     * worked out - the game files say which rarity each enchant is.
     */
    static void enchantGlow(Graphics2D g, String enchants, double x, double y, int size) {
        enchantGlow(g, EnchantSprites.slots(enchants), x, y, size);
    }

    /**
     * The game's own mark for an enchant rarity: one green diamond for
     * Uncommon, two blue for Rare, three violet for Legendary, four gold for
     * Divine. These are the game's own 16x16 sprites, cut out of the GUI atlas
     * it ships - the atlas names them RarityIcon_1 to RarityIcon_4 - because a
     * mark drawn here would never have matched the one on screen.
     *
     * @param tier 1 to 4; null for anything else.
     */
    static Image rarity(int tier) {
        if (tier < 1 || tier > 4) return null;
        Object found = RARITY[tier - 1];
        if (found == null) {
            found = Boolean.FALSE;
            String name = new String[]{"Uncommon", "Rare", "Legendary", "Divine"}[tier - 1];
            try (java.io.InputStream in = Glyph.class.getResourceAsStream("/rarity/" + name + ".png")) {
                if (in != null) {
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
                    if (img != null) found = img;
                }
            } catch (Exception e) {
                // without it the item keeps its glow and its own enchant gem
            }
            RARITY[tier - 1] = found;
        }
        return found instanceof Image ? (Image) found : null;
    }

    private static final Object[] RARITY = new Object[4];
    /** Paints an already-resolved item picture, for callers that looked it up themselves. */
    static boolean drawIcon(Graphics2D g, Image img, double x, double y, int size) {
        return draw(g, img, x, y, size);
    }

    /** Paints one item picture centred in a square, pixel-crisp rather than smoothed. */
    private static boolean draw(Graphics2D g, Image img, double x, double y, int size) {
        if (img == null) return false;
        Graphics2D g2 = (Graphics2D) g.create();
        if (exportScale > 1) {
            blit(g2, img, x, y, size);
            g2.dispose();
            return true;
        }
        int w = img.getWidth(null), h = img.getHeight(null);
        double scale = Math.min(size / (double) w, size / (double) h);
        int dw = (int) Math.round(w * scale), dh = (int) Math.round(h * scale);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(img, (int) Math.round(x + (size - dw) / 2.0), (int) Math.round(y + (size - dh) / 2.0), dw, dh, null);
        g2.dispose();
        return true;
    }

    private static final java.util.HashMap<String, Object> PLAYERS = new java.util.HashMap<>();
    /** Where each class and skin's frame "walking to the right" sits on the game's character sheet. */
    private static java.util.HashMap<String, int[]> walking;

    /** Standing frames of every animated sprite, by sheet and index; built once. */
    private static java.util.HashMap<String, int[]> standing;
    /** Entity sprites already cut, by object type. Holds nulls, so a miss is not looked up twice. */
    private static final java.util.HashMap<Integer, java.awt.image.BufferedImage> ENTITY_SPRITES = new java.util.HashMap<>();

    /**
     * The game's own sprite for an entity, at its native size, or null when
     * the game does not give it one.
     * <p>
     * Takes the standing frame of an animated sprite where there is one, so a
     * monster is drawn facing you rather than mid-stride. Some objects the
     * normal texture lookup does not answer for - the Derelict Monument among
     * them - so the Shatters data is read directly as a fallback rather than
     * giving those up.
     */
    static synchronized java.awt.image.BufferedImage entitySprite(int objectType) {
        if (ENTITY_SPRITES.containsKey(objectType)) return ENTITY_SPRITES.get(objectType);
        java.awt.image.BufferedImage img = null;
        try {
            String sheet = null;
            int index = 0;
            try {
                sheet = sonar.assets.IdToAsset.getObjectTextureName(objectType, 0);
                index = sonar.assets.IdToAsset.getObjectTextureIndex(objectType, 0);
            } catch (Throwable ignored) {
                // the id table does not answer for this one; the game data below still might
            }
            if (sheet == null || sheet.isEmpty()) {
                String[] from = textureFromShattersXml(objectType);
                if (from != null) {
                    sheet = from[0];
                    index = Integer.decode(from[1]);
                }
            }
            // "invisible" is the game's own way of saying this object has no sprite at all
            if (sheet != null && !sheet.isEmpty() && !"invisible".equals(sheet)) {
                int[] at = standingFrame(sheet, index);
                if (at == null) at = spriteData(sheet, index);
                if (at != null) img = sheetPart(at);
            }
        } catch (Throwable ignored) {
            // a sprite that cannot be cut just falls back to the drawn mark
        }
        ENTITY_SPRITES.put(objectType, img);
        return img;
    }

    /**
     * Whether a sprite is worth drawing as itself, or whether a plain mark
     * would say more.
     * <p>
     * Some objects are drawn almost black on purpose - the Shatters void
     * cracks are cracks in the floor, and on a dark minimap they come out as
     * nothing at all. Rather than keep a list of which sprites those are,
     * which would go stale, the pixels are counted: a sprite needs a
     * twentieth of itself to be both solid and brighter than the dark it sits
     * on. The cracks measure 0-1% by that rule and everything else 20-45%, so
     * it separates them cleanly without a judgement call.
     */
    static boolean spriteReads(java.awt.image.BufferedImage s) {
        if (s == null) return false;
        int lit = 0, n = s.getWidth() * s.getHeight();
        if (n == 0) return false;
        for (int y = 0; y < s.getHeight(); y++) {
            for (int x = 0; x < s.getWidth(); x++) {
                int p = s.getRGB(x, y);
                if ((p >>> 24) < 40) continue;
                if (Math.max((p >> 16) & 255, Math.max((p >> 8) & 255, p & 255)) > 60) lit++;
            }
        }
        return lit * 100 / n >= 5;
    }

    /** The frame an animated sprite stands still on, facing sideways, or null if it is not animated. */
    private static int[] standingFrame(String sheet, int index) throws Exception {
        if (standing == null) {
            java.util.HashMap<String, int[]> frames = new java.util.HashMap<>();
            byte[] data = java.nio.file.Files.readAllBytes(sonar.DataDir.file("assets/flatbuffer/spritesheetf").toPath());
            sonar.assets.flattbuffer.SpriteSheetRoot root = sonar.assets.flattbuffer.SpriteSheetRoot.getRootAsSpriteSheetRoot(java.nio.ByteBuffer.wrap(data));
            for (int i = 0; i < root.animatedSpritesLength(); i++) {
                sonar.assets.flattbuffer.AnimatedSprite s = root.animatedSprites(i);
                // direction 0 is the side view; action 0 is standing, which is the frame wanted here
                if (s.direction() != 0 || s.action() != 0) continue;
                sonar.assets.flattbuffer.Sprite p = s.sprites();
                frames.putIfAbsent(s.name() + ":" + s.index(), new int[]{(int) p.position().x(), (int) p.position().y(),
                        (int) p.position().w(), (int) p.position().h(), (int) p.aId()});
            }
            standing = frames;
        }
        return standing.get(sheet + ":" + index);
    }

    /** Sheet and index straight out of the Shatters data, for objects the id table has no texture for. */
    private static String[] textureFromShattersXml(int objectType) {
        try {
            String xml = new String(java.nio.file.Files.readAllBytes(
                    sonar.DataDir.file("assets/xml/theShattersObjects.xml").toPath()), java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<Object ([^>]*\\btype=\"0x0*" + Integer.toHexString(objectType)
                            + "\"[^>]*)>(.*?)</Object>", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                    .matcher(xml);
            if (!m.find()) return null;
            java.util.regex.Matcher t = java.util.regex.Pattern
                    .compile("<(?:Animated)?Texture>\\s*<File>([^<]*)</File>\\s*<Index>([^<]*)</Index>", java.util.regex.Pattern.DOTALL)
                    .matcher(m.group(2));
            return t.find() ? new String[]{t.group(1).trim(), t.group(2).trim()} : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Draws a player as in game: their class or skin walking to the right,
     * with their two dyes or cloths on the parts the game marks for them.
     *
     * @param main      The game's value for the main dye or cloth, 0 for none.
     * @param accessory The same for the accessory.
     */
    static boolean player(Graphics2D g, int skin, int main, int accessory, double x, double y, int size) {
        if (skin <= 0) return false;
        // the game sends which dye or cloth item is worn; the item says what colour or pattern that is
        main = dyeValue(main);
        accessory = dyeValue(accessory);
        String key = skin + ":" + main + ":" + accessory;
        Object found = PLAYERS.get(key);
        if (found == null) {
            found = Boolean.FALSE;
            try {
                java.awt.image.BufferedImage img = playerImage(skin, main, accessory);
                if (img != null) found = img;
            } catch (Throwable e) {
                // drawn the plain way instead
            }
            if (PLAYERS.size() > 600) PLAYERS.clear();
            PLAYERS.put(key, found);
        }
        if (!(found instanceof Image)) return object(g, skin, x, y, size);
        Graphics2D g2 = (Graphics2D) g.create();
        blit(g2, (Image) found, x, y, size);
        g2.dispose();
        return true;
    }

    /**
     * A character with their Arcane Style over them, when they wear one and its art has
     * been extracted; otherwise exactly {@link #player}.
     * <p>
     * An effect is twice the character's size in game and spreads around them, so
     * {@code bleed} says how much of that spread this may use. Pass {@code 0} anywhere the
     * portrait sits on top of something whose colour carries meaning - the damage list's
     * class-coloured bars above all - and the effect stays inside the portrait instead of
     * tinting what is behind it.
     *
     * @param styleType the item id from the shader stat, or -1 for none.
     * @param timeMs    how long the effect has been running, which picks the frame.
     * @param bleed     how far past the portrait the effect may reach, 0 to 0.5.
     */
    static boolean playerStyled(Graphics2D g, int skin, int main, int accessory, int styleType,
                                long timeMs, double x, double y, int size, double bleed) {
        ArcaneStyle style = styleType > 0 ? ArcaneStyle.of(styleType) : null;
        java.awt.image.BufferedImage look = style == null ? null : playerLook(skin, main, accessory);
        if (look == null) return player(g, skin, main, accessory, x, y, size);
        java.awt.image.BufferedImage styled = ArcaneArt.apply(look, style, timeMs);
        if (styled == null) return player(g, skin, main, accessory, x, y, size);
        Graphics2D g2 = (Graphics2D) g.create();
        blit(g2, styled, x, y, size);
        g2.dispose();
        return true;
    }

    /** The drawn character, as {@link #player} caches it, for anything that must work on the pixels. */
    static java.awt.image.BufferedImage playerLook(int skin, int main, int accessory) {
        if (skin <= 0) return null;
        main = dyeValue(main);
        accessory = dyeValue(accessory);
        String key = skin + ":" + main + ":" + accessory;
        Object found = PLAYERS.get(key);
        if (found == null) {
            found = Boolean.FALSE;
            try {
                java.awt.image.BufferedImage img = playerImage(skin, main, accessory);
                if (img != null) found = img;
            } catch (Throwable e) {
                // drawn the plain way instead
            }
            if (PLAYERS.size() > 600) PLAYERS.clear();
            PLAYERS.put(key, found);
        }
        return found instanceof java.awt.image.BufferedImage ? (java.awt.image.BufferedImage) found : null;
    }

    private static java.util.HashMap<Integer, Integer> dyeItems;

    /** The colour or pattern code of a dye or cloth item; codes that are already one pass through. */
    private static int dyeValue(int value) {
        if (value == 0 || value == -1) return 0;
        if ((value >>> 24) != 0) return value;
        if (dyeItems == null) {
            java.util.HashMap<Integer, Integer> found = new java.util.HashMap<>();
            for (String file : new String[]{"dyes.xml", "textiles.xml", "equip.xml"}) {
                try {
                    String xml = new String(java.nio.file.Files.readAllBytes(sonar.DataDir.file("assets/xml/" + file).toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("<Object ([^>]*)>((?:(?!</Object>).)*?)<Tex[12]>0x([0-9a-fA-F]+)</Tex[12]>", java.util.regex.Pattern.DOTALL).matcher(xml);
                    java.util.regex.Pattern type = java.util.regex.Pattern.compile("\\btype=\"0x([0-9a-fA-F]+)\"");
                    while (m.find()) {
                        java.util.regex.Matcher t = type.matcher(m.group(1));
                        if (t.find()) found.put(Integer.parseInt(t.group(1), 16), (int) Long.parseLong(m.group(3), 16));
                    }
                } catch (Exception e) {
                    // that file's dyes stay unknown
                }
            }
            dyeItems = found;
        }
        Integer code = dyeItems.get(value);
        return code == null || code == -1 ? 0 : code;
    }

    private static java.awt.image.BufferedImage sheetPart(int[] data) throws Exception {
        java.lang.reflect.Method cut = sonar.assets.ImageBuffer.class.getDeclaredMethod("getSprite", int[].class);
        cut.setAccessible(true);
        return (java.awt.image.BufferedImage) cut.invoke(null, (Object) data);
    }

    private static int[] spriteData(String sheet, int index) throws Exception {
        java.lang.reflect.Field reader = sonar.assets.ImageBuffer.class.getDeclaredField("spriteFlatBuffer");
        reader.setAccessible(true);
        return ((sonar.assets.SpriteFlatBuffer) reader.get(null)).getSpriteData(sheet, index);
    }

    private static java.awt.image.BufferedImage playerImage(int skin, int main, int accessory) throws Exception {
        if (walking == null) {
            java.util.HashMap<String, int[]> frames = new java.util.HashMap<>();
            byte[] data = java.nio.file.Files.readAllBytes(sonar.DataDir.file("assets/flatbuffer/spritesheetf").toPath());
            sonar.assets.flattbuffer.SpriteSheetRoot root = sonar.assets.flattbuffer.SpriteSheetRoot.getRootAsSpriteSheetRoot(java.nio.ByteBuffer.wrap(data));
            for (int i = 0; i < root.animatedSpritesLength(); i++) {
                sonar.assets.flattbuffer.AnimatedSprite s = root.animatedSprites(i);
                // direction 0 is the side view, which faces right; action 1 is walking; the first such frame is kept
                if (s.direction() != 0 || s.action() != 1) continue;
                sonar.assets.flattbuffer.Sprite p = s.sprites();
                frames.putIfAbsent(s.name() + ":" + s.index(), new int[]{(int) p.position().x(), (int) p.position().y(), (int) p.position().w(), (int) p.position().h(), (int) p.aId(),
                        (int) p.maskPosition().x(), (int) p.maskPosition().y(), (int) p.maskPosition().w(), (int) p.maskPosition().h()});
            }
            walking = frames;
        }
        int[] at = walking.get(sonar.assets.IdToAsset.getObjectTextureName(skin, 0) + ":" + sonar.assets.IdToAsset.getObjectTextureIndex(skin, 0));
        if (at == null) return null;
        java.awt.image.BufferedImage body = sheetPart(at);
        // the mask sheet (the third) has its own place for the frame: red marks the main part, green the accessory, brighter meaning lighter
        java.awt.image.BufferedImage mask = (main == 0 && accessory == 0) || at[7] <= 0 ? null : sheetPart(new int[]{at[5], at[6], at[7], at[8], 3});
        final int scale = 5;
        int w = at[2], h = at[3];
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w * scale, h * scale, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.image.BufferedImage[] cloth = {clothOf(main), clothOf(accessory)};
        int[] dye = {main, accessory};
        for (int py = 0; py < h * scale; py++) {
            for (int px = 0; px < w * scale; px++) {
                int argb = body.getRGB(px / scale, py / scale);
                if (mask != null && (argb >>> 24) != 0) {
                    int m = mask.getRGB(Math.min(mask.getWidth() - 1, px / scale), Math.min(mask.getHeight() - 1, py / scale));
                    int red = (m >> 16) & 255, green = (m >> 8) & 255;
                    int part = red > 0 ? 0 : green > 0 ? 1 : -1;
                    if (part >= 0 && dye[part] != 0) {
                        int shade = part == 0 ? red : green, colour;
                        // a dye is one colour; a cloth is a small pattern repeated over the part
                        // the pattern repeats over the character's own pixels, so it is sampled in
                        // sprite space; tiling it per output pixel instead made it repeat five times
                        // too often and striped the character, which only showed at portrait size
                        if (cloth[part] != null) colour = cloth[part].getRGB((px / scale) % cloth[part].getWidth(), (py / scale) % cloth[part].getHeight());
                        else colour = dye[part];
                        int r = ((colour >> 16) & 255) * shade / 255, gr = ((colour >> 8) & 255) * shade / 255, b = (colour & 255) * shade / 255;
                        argb = 0xff000000 | (r << 16) | (gr << 8) | b;
                    }
                }
                out.setRGB(px, py, argb);
            }
        }
        return out;
    }

    /**
     * Fills a shape with what a dye or cloth paints: the one colour of a dye,
     * or the small pattern of a cloth repeated over it, which is how the game
     * itself lays a cloth onto a character.
     *
     * @param value the colour or pattern code, as {@link Wardrobe} keeps it.
     */
    static void dye(Graphics2D g, int value, Shape shape) {
        if (value == 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        java.awt.image.BufferedImage cloth = clothOf(value);
        if (cloth == null) {
            g2.setColor(new Color(value & 0xffffff));
        } else {
            // twice life size, so a weave is still readable in a swatch the size of a letter
            Rectangle at = shape.getBounds();
            g2.setPaint(new TexturePaint(cloth, new java.awt.geom.Rectangle2D.Double(at.x, at.y, cloth.getWidth() * 2.0, cloth.getHeight() * 2.0)));
        }
        g2.fill(shape);
        g2.dispose();
    }

    private static final java.util.HashMap<Integer, Object> CLOTHS = new java.util.HashMap<>();

    /**
     * The pattern of a cloth, or null for a plain dye or none. Kept, because
     * the Wardrobe draws several hundred of these at once and cutting each
     * from the sheet every frame is far too slow.
     */
    private static java.awt.image.BufferedImage clothOf(int value) {
        int size = value >>> 24;
        if (value == 0 || size == 1) return null;
        Object found = CLOTHS.get(value);
        if (found == null) {
            found = Boolean.FALSE;
            try {
                java.awt.image.BufferedImage img = sheetPart(spriteData("textile" + size + "x" + size, value & 0xffffff));
                if (img != null && img.getWidth() > 0) found = img;
            } catch (Throwable e) {
                // not a cloth Sonar can draw; it falls back to the plain colour
            }
            CLOTHS.put(value, found);
        }
        return found instanceof java.awt.image.BufferedImage ? (java.awt.image.BufferedImage) found : null;
    }

    private static final java.util.HashMap<Integer, Object> OBJECTS = new java.util.HashMap<>();

    /** Draws the game's picture of anything by its game id, such as a class or a skin. */
    static boolean object(Graphics2D g, int id, double x, double y, int size) {
        if (id <= 0) return false;
        Object icon = OBJECTS.get(id);
        if (icon == null) {
            icon = Boolean.FALSE;
            try {
                Image img = sonar.assets.ImageBuffer.getOutlinedIcon(id, 24).getImage();
                if (img != null && img.getWidth(null) > 0) icon = img;
            } catch (Throwable e) {
                // no picture
            }
            OBJECTS.put(id, icon);
        }
        if (!(icon instanceof Image)) return false;
        Graphics2D g2 = (Graphics2D) g.create();
        blit(g2, (Image) icon, x, y, size);
        g2.dispose();
        return true;
    }

    private static final java.util.HashMap<String, Object> SPRITES = new java.util.HashMap<>();

    /**
     * Draws one picture from the game's sprite sheets, such as an enchant's
     * own symbol, by the sheet's name and the picture's number on it.
     *
     * @return False when the game files have no such picture.
     */
    static boolean sprite(Graphics2D g, String sheet, int index, double x, double y, int size, float alpha) {
        if (sheet == null || sheet.isEmpty() || index < 0) return false;
        String key = sheet + ":" + index;
        Object found = SPRITES.get(key);
        if (found == null) {
            found = Boolean.FALSE;
            try {
                // the packet library keeps its sprite reader to itself, so it is reached from outside
                java.lang.reflect.Field reader = sonar.assets.ImageBuffer.class.getDeclaredField("spriteFlatBuffer");
                reader.setAccessible(true);
                int[] data = ((sonar.assets.SpriteFlatBuffer) reader.get(null)).getSpriteData(sheet, index);
                java.lang.reflect.Method cut = sonar.assets.ImageBuffer.class.getDeclaredMethod("getSprite", int[].class);
                cut.setAccessible(true);
                Object img = data == null ? null : cut.invoke(null, (Object) data);
                if (img instanceof Image) found = img;
            } catch (Throwable e) {
                // no picture
            }
            SPRITES.put(key, found);
        }
        if (!(found instanceof Image)) return false;
        Image img = (Image) found;
        Graphics2D g2 = (Graphics2D) g.create();
        if (alpha < 1f) g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
        blit(g2, img, x, y, size);
        g2.dispose();
        return true;
    }

    /** A head and shoulders: filled for a party member, an outline for someone who can be added. */
    static void person(Graphics2D g, double x, double y, double size, Color color, boolean filled) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setColor(color);
        g2.setStroke(new BasicStroke((float) Math.max(1.2, size / 9), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Shape head = new java.awt.geom.Ellipse2D.Double(x + size * 0.28, y + size * 0.04, size * 0.44, size * 0.44);
        Shape shoulders = new java.awt.geom.Arc2D.Double(x + size * 0.06, y + size * 0.58, size * 0.88, size * 0.80, 0, 180, filled ? java.awt.geom.Arc2D.CHORD : java.awt.geom.Arc2D.OPEN);
        if (filled) {
            g2.fill(head);
            g2.fill(shoulders);
        } else {
            g2.draw(head);
            g2.draw(shoulders);
        }
        g2.dispose();
    }

    /** A small cross, for removing. */
    static void cross(Graphics2D g, double x, double y, double size, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new java.awt.geom.Line2D.Double(x, y, x + size, y + size));
        g2.draw(new java.awt.geom.Line2D.Double(x + size, y, x, y + size));
        g2.dispose();
    }

    /** A soft halo of concentric fading rounded rects behind an item's tile. */
    static void glow(Graphics2D g, double x, double y, double size, double cornerRadius, Color color) {
        for (int r = 3; r >= 1; r--) {
            g.setColor(SonarTheme.alpha(color, 0.045 * r));
            g.fill(SonarTheme.round(x - r * 2.0, y - r * 2.0, size + r * 4.0, size + r * 4.0, cornerRadius + r * 2.0));
        }
    }

    /**
     * The bright reflection that periodically sweeps across a Shiny item's
     * picture, the same as it does in game. The position comes from the
     * system clock, so every shiny tile on screen animates together without
     * needing to share any state; the caller only needs its own repaint timer.
     */
    static void shine(Graphics2D g, double x, double y, double size, double cornerRadius) {
        double period = 2600, sweep = 0.35;
        double phase = (System.currentTimeMillis() % (long) period) / period;
        if (phase >= sweep) return;
        double t = phase / sweep;
        double cx = x - size * 0.3 + t * (size * 1.6);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.clip(SonarTheme.round(x + 1, y + 1, size - 2, size - 2, Math.max(0, cornerRadius - 1)));
        g2.setPaint(new LinearGradientPaint(
                new java.awt.geom.Point2D.Double(cx - size * 0.3, y),
                new java.awt.geom.Point2D.Double(cx + size * 0.3, y + size),
                new float[]{0f, 0.5f, 1f},
                new Color[]{SonarTheme.alpha(Color.WHITE, 0), SonarTheme.alpha(Color.WHITE, 0.85), SonarTheme.alpha(Color.WHITE, 0)}));
        g2.fill(new java.awt.geom.Rectangle2D.Double(x, y, size, size));
        g2.dispose();
    }

    /** A small "SHINY" tag, placed in the top-left corner of an item's tile. */
    static void shinyBadge(Graphics2D g, double x, double y) {
        g.setFont(SonarTheme.bold(8f));
        g.setColor(SonarTheme.GOLD);
        g.drawString("SHINY", (float) x, (float) y);
    }

    private static void line(Path2D.Double p, double... xy) {
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
    }
}
