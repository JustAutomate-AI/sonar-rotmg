package sonar.assets.resextractor;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pulls the Arcane Style effect art out of the player's own game install.
 * <p>
 * This art does not live in {@code resources.assets} with everything else Sonar
 * extracts; it is in {@code sharedassets1.assets}, as horizontal strips sixteen
 * pixels tall with one animation frame per sixteen-pixel cell.
 * <p>
 * Which strip belongs to which style is not guessed from the file names. The client
 * carries two tables of its own that map the names used in {@code shaders.xml} onto
 * textures, and those are what this reads:
 * <ul>
 *   <li>{@code AnimatedFlipbooks} - the frame animations, and how many frames of each
 *       strip are actually played. That count is worth having: one strip is three cells
 *       wider than the animation it holds, and trusting the image size would play three
 *       frames of blank padding.</li>
 *   <li>{@code ScrollerEffectSprites} - the tiling art the Scroller styles pan across a
 *       character instead of stepping through.</li>
 * </ul>
 * Nothing is redistributed: like every other asset Sonar uses, this comes off the
 * machine it runs on.
 */
public final class ArcaneExtractor {

    /** Where the strips and the index are written, under the data folder. */
    public static final String DIR = "assets/arcane";

    /** The name of the index file, which the drawing side reads instead of the game files. */
    public static final String INDEX = "index.txt";

    private ArcaneExtractor() {
    }

    /** One effect: the name a style refers to it by, and the strip that draws it. */
    public static final class Effect {
        public final String key;
        public final String texture;
        public final int frames;
        public final int cells;
        public final boolean scroller;

        Effect(String key, String texture, int frames, int cells, boolean scroller) {
            this.key = key;
            this.texture = texture;
            this.frames = frames;
            this.cells = cells;
            this.scroller = scroller;
        }
    }

    /**
     * Finds {@code sharedassets1.assets} beside the resources file Sonar already locates.
     *
     * @return the file, or null if it is not there.
     */
    public static File sharedAssets(File resourcesAssets) {
        if (resourcesAssets == null) return null;
        File dir = resourcesAssets.getParentFile();
        if (dir == null) return null;
        File f = new File(dir, "sharedassets1.assets");
        return f.isFile() ? f : null;
    }

    /**
     * Reads the effect art out of the game and writes it where Sonar can draw from it.
     *
     * @param sharedAssets1 the game's {@code sharedassets1.assets}.
     * @param outDir        the folder to write the strips and index into.
     * @return the effects written, empty if the file could not be read.
     */
    public static List<Effect> extract(File sharedAssets1, File outDir) throws IOException {
        List<Effect> done = new ArrayList<>();
        if (sharedAssets1 == null || !sharedAssets1.isFile()) return done;
        if (!outDir.isDirectory() && !outDir.mkdirs()) return done;

        FileHeader header = new FileHeader(sharedAssets1);
        if (header.type != FileHeader.AssetsFile) return done;
        SerializedFile sf = new SerializedFile(sharedAssets1, header);

        // every object by its id, so the tables' pointers can be followed
        Map<Long, ObjectReader> byId = new LinkedHashMap<>();
        List<ObjectReader> behaviours = new ArrayList<>();
        for (ObjectReader o : sf.objects) {
            byId.put(o.path_id, o);
            if (o.type == ClassIDType.MonoBehaviour) behaviours.add(o);
        }

        File resS = new File(sharedAssets1.getParentFile(), sharedAssets1.getName() + ".resS");

        for (ObjectReader o : behaviours) {
            String name = behaviourName(o);
            boolean flip = "AnimatedFlipbooks".equals(name);
            boolean scroll = "ScrollerEffectSprites".equals(name);
            if (!flip && !scroll) continue;
            done.addAll(readTable(o, flip, byId, resS, outDir));
        }

        writeIndex(outDir, done);
        return done;
    }

    /**
     * The {@code m_Name} of a MonoBehaviour, without reading the script's own fields.
     * The layout before the name is fixed: a pointer to the game object, the enabled
     * flag, then a pointer to the script.
     */
    private static String behaviourName(ObjectReader o) {
        try {
            DataReader r = o.reader;
            r.setPosition((int) o.byte_start);
            r.readInt();
            r.readLong();       // m_GameObject
            r.readBoolean();    // m_Enabled
            r.alignStream();
            r.readInt();
            r.readLong();       // m_Script
            return r.readAlignedString();
        } catch (Exception e) {
            // not one of ours, or a script whose layout differs
            return null;
        }
    }

    /**
     * Reads one of the two tables. Both are a count followed by entries of a name and a
     * pointer to the art; the flipbook table carries the played frame count as well.
     */
    private static List<Effect> readTable(ObjectReader o, boolean withFrameCount,
                                          Map<Long, ObjectReader> byId, File resS, File outDir) {
        List<Effect> out = new ArrayList<>();
        try {
            DataReader r = o.reader;
            r.setPosition((int) o.byte_start);
            r.readInt();
            r.readLong();
            r.readBoolean();
            r.alignStream();
            r.readInt();
            r.readLong();
            r.readAlignedString();
            int count = r.readInt();
            if (count < 0 || count > 4096) return out;

            // the names and pointers first: decoding a texture moves the shared reader
            List<String> keys = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            List<Integer> declared = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                keys.add(r.readAlignedString());
                r.readInt();                      // the file this points into; always this one
                ids.add(r.readLong());
                declared.add(withFrameCount ? r.readInt() : 0);
            }

            for (int i = 0; i < count; i++) {
                ObjectReader target = resolve(byId.get(ids.get(i)), byId);
                if (target == null) continue;
                BufferedImage img = texture(target, resS);
                if (img == null) continue;
                Texture2D t = new Texture2D(target);
                int cells = img.getHeight() == 16 ? img.getWidth() / 16 : 0;
                int frames = withFrameCount ? declared.get(i) : 0;
                // the table is the authority on how much of the strip is animation
                if (frames <= 0 || frames > cells) frames = cells;
                ImageIO.write(img, "png", new File(outDir, fileName(keys.get(i), !withFrameCount)));
                out.add(new Effect(keys.get(i), t.name, frames, cells, !withFrameCount));
            }
        } catch (Exception e) {
            // a table that will not read leaves the styles it covers undrawn, which is
            // the same outcome as not having extracted at all
        }
        return out;
    }

    /**
     * The texture an entry points at. The flipbook table points straight at one; the
     * scroller table points at a Sprite instead, which is a named window onto a texture.
     * Rather than decode the sprite's rectangle, this matches the texture of the same
     * name: the scroller art is a whole texture, so the window is all of it.
     *
     * @return a Texture2D object, or null if the pointer leads somewhere else.
     */
    private static ObjectReader resolve(ObjectReader target, Map<Long, ObjectReader> byId) {
        if (target == null) return null;
        if (target.type == ClassIDType.Texture2D) return target;
        if (target.type != ClassIDType.Sprite) return null;
        String name = firstName(target);
        if (name == null || name.isEmpty()) return null;
        for (ObjectReader o : byId.values()) {
            if (o.type != ClassIDType.Texture2D) continue;
            String other = firstName(o);
            if (name.equals(other)) return o;
        }
        return null;
    }

    /**
     * The {@code m_Name} an object starts with. Sprite and Texture2D both begin with it,
     * which is all this needs to pair the two up.
     */
    private static String firstName(ObjectReader o) {
        try {
            o.reader.setPosition((int) o.byte_start);
            return o.reader.readAlignedString();
        } catch (Exception e) {
            return null;
        }
    }

    /** The file one effect's art is written to. */
    public static String fileName(String key, boolean scroller) {
        String safe = key.replaceAll("[^A-Za-z0-9_.-]", "_");
        return (scroller ? "scroll-" : "") + safe + ".png";
    }

    /**
     * Decodes one texture, reading its pixels from the streaming file when they are not
     * stored in the assets file itself. All of this art is DXT5.
     */
    private static BufferedImage texture(ObjectReader o, File resS) throws IOException {
        Texture2D t = new Texture2D(o);
        if (t.m_Width <= 0 || t.m_Height <= 0) return null;

        byte[] data = t.image_data;
        if (data == null || data.length == 0) {
            if (t.path == null || t.size <= 0) return null;
            File src = new File(resS.getParentFile(), new File(t.path).getName());
            if (!src.isFile()) return null;
            data = new byte[(int) t.size];
            try (RandomAccessFile raf = new RandomAccessFile(src, "r")) {
                raf.seek(t.offset);
                raf.readFully(data);
            }
        }

        int[] argb;
        switch (t.m_TextureFormat) {
            case DXT5:
                argb = Dxt.decodeDxt5(data, t.m_Width, t.m_Height);
                break;
            case RGBA32:
                argb = new int[t.m_Width * t.m_Height];
                for (int i = 0; i < argb.length && i * 4 + 3 < data.length; i++) {
                    argb[i] = ((data[i * 4 + 3] & 255) << 24) | ((data[i * 4] & 255) << 16)
                            | ((data[i * 4 + 1] & 255) << 8) | (data[i * 4 + 2] & 255);
                }
                break;
            default:
                // no other format appears in this art; a new one should be noticed, not guessed at
                return null;
        }
        if (argb == null) return null;

        BufferedImage img = new BufferedImage(t.m_Width, t.m_Height, BufferedImage.TYPE_INT_ARGB);
        // Unity stores textures bottom row first, so the rows go back the other way
        for (int y = 0; y < t.m_Height; y++) {
            img.setRGB(0, t.m_Height - 1 - y, t.m_Width, 1, argb, y * t.m_Width, t.m_Width);
        }
        return img;
    }

    /** Writes what was extracted, so the drawing side never has to open a game file. */
    private static void writeIndex(File outDir, List<Effect> effects) throws IOException {
        try (Writer w = Files.newBufferedWriter(new File(outDir, INDEX).toPath(), StandardCharsets.UTF_8)) {
            w.write("# key\tframes\tcells\tscroller\ttexture\n");
            for (Effect e : effects) {
                w.write(e.key + "\t" + e.frames + "\t" + e.cells + "\t" + (e.scroller ? 1 : 0)
                        + "\t" + e.texture + "\n");
            }
        }
    }

    /** Reads back what {@link #extract} wrote. */
    public static List<Effect> index(File dir) {
        List<Effect> out = new ArrayList<>();
        File f = new File(dir, INDEX);
        if (!f.isFile()) return out;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] p = line.split("\t", -1);
                if (p.length < 5) continue;
                out.add(new Effect(p[0], p[4], Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        "1".equals(p[3])));
            }
        } catch (Exception e) {
            // an index that will not parse is the same as none
        }
        return out;
    }
}
