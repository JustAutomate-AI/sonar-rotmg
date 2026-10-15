package sonar.assets.resextractor;

/**
 * Decodes the block-compressed textures the game ships, which the plain
 * {@link Texture2D} path cannot read.
 * <p>
 * The four spritesheets Sonar has always extracted are stored uncompressed, so
 * nothing here was needed for them. The Arcane Style effect strips are DXT5, and
 * every one of them would come out as noise without this.
 * <p>
 * DXT5 (also called BC3) describes each 4x4 block of pixels in sixteen bytes: two
 * bytes of endpoint alpha followed by six bytes holding sixteen three-bit alpha
 * indices, then two RGB565 endpoint colours followed by sixteen two-bit colour
 * indices. The endpoints are the extremes and the indices pick points along the
 * line between them, so a block is a gradient with a per-pixel position on it.
 */
public final class Dxt {

    private Dxt() {
    }

    /**
     * Decodes a DXT5 image into packed ARGB pixels, row by row from the top.
     *
     * @param data   the compressed bytes, sixteen per 4x4 block.
     * @param width  pixel width; blocks cover it in multiples of four.
     * @param height pixel height.
     * @return {@code width * height} pixels as 0xAARRGGBB, or null if the data is short.
     */
    public static int[] decodeDxt5(byte[] data, int width, int height) {
        if (data == null || width <= 0 || height <= 0) return null;
        int blocksX = (width + 3) / 4, blocksY = (height + 3) / 4;
        if (data.length < blocksX * blocksY * 16) return null;

        int[] out = new int[width * height];
        int[] alpha = new int[8];
        int[] colour = new int[4];

        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                int p = (by * blocksX + bx) * 16;

                int a0 = data[p] & 255, a1 = data[p + 1] & 255;
                alpha[0] = a0;
                alpha[1] = a1;
                if (a0 > a1) {
                    // six interpolated steps between the endpoints
                    for (int i = 2; i < 8; i++) alpha[i] = ((8 - i) * a0 + (i - 1) * a1) / 7;
                } else {
                    // four interpolated steps, then fully clear and fully opaque
                    for (int i = 2; i < 6; i++) alpha[i] = ((6 - i) * a0 + (i - 1) * a1) / 5;
                    alpha[6] = 0;
                    alpha[7] = 255;
                }

                // the sixteen three-bit alpha indices are one 48-bit little-endian run
                long alphaBits = 0;
                for (int i = 0; i < 6; i++) alphaBits |= (long) (data[p + 2 + i] & 255) << (8 * i);

                int c0 = (data[p + 8] & 255) | ((data[p + 9] & 255) << 8);
                int c1 = (data[p + 10] & 255) | ((data[p + 11] & 255) << 8);
                colour[0] = rgb565(c0);
                colour[1] = rgb565(c1);
                // DXT5 always uses the four-colour mode; the three-colour form is DXT1 only
                colour[2] = mix(colour[0], colour[1], 2, 1);
                colour[3] = mix(colour[0], colour[1], 1, 2);

                int colourBits = (data[p + 12] & 255) | ((data[p + 13] & 255) << 8)
                        | ((data[p + 14] & 255) << 16) | ((data[p + 15] & 255) << 24);

                for (int y = 0; y < 4; y++) {
                    int py = by * 4 + y;
                    if (py >= height) break;
                    for (int x = 0; x < 4; x++) {
                        int px = bx * 4 + x;
                        if (px >= width) continue;
                        int i = y * 4 + x;
                        int ai = (int) ((alphaBits >>> (3 * i)) & 7);
                        int ci = (colourBits >>> (2 * i)) & 3;
                        out[py * width + px] = (alpha[ai] << 24) | (colour[ci] & 0xffffff);
                    }
                }
            }
        }
        return out;
    }

    /** One RGB565 endpoint as a 0xRRGGBB value, each channel spread back over its full range. */
    private static int rgb565(int v) {
        int r = (v >>> 11) & 31, g = (v >>> 5) & 63, b = v & 31;
        // repeat the high bits into the low ones, so 31 becomes 255 rather than 248
        r = (r << 3) | (r >>> 2);
        g = (g << 2) | (g >>> 4);
        b = (b << 3) | (b >>> 2);
        return (r << 16) | (g << 8) | b;
    }

    /** A weighted blend of two packed colours, {@code (a*wa + b*wb) / (wa + wb)} per channel. */
    private static int mix(int a, int b, int wa, int wb) {
        int total = wa + wb;
        int r = (((a >> 16) & 255) * wa + ((b >> 16) & 255) * wb) / total;
        int g = (((a >> 8) & 255) * wa + ((b >> 8) & 255) * wb) / total;
        int bl = ((a & 255) * wa + (b & 255) * wb) / total;
        return (r << 16) | (g << 8) | bl;
    }
}
