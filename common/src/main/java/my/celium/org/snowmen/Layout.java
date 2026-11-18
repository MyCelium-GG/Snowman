package my.celium.org.snowmen;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Where every piece of a snowman goes, as display transformations relative to the
 * root entity. Pure math (JOML only), so it is unit tested.
 *
 * <p>Local frame: +Y up, +Z is the way the snowman faces. Block displays draw a unit
 * cube from 0 to 1, item displays draw centred on the origin.
 */
public final class Layout {
    /** What a display shows. */
    public enum Kind { BALL, EYE, NOSE, BUTTON, ARM, SCARF, HAT, MOUTH, LIGHT, WIRE }

    /** One display: its kind, its transformation and whether it shows a block (true) or an item. */
    public record Piece(Kind kind, int index, Matrix4f matrix, boolean block) {
    }

    /** How much of a ball's width the rounding boxes keep. */
    static final float ROUND = 0.74f;
    /** Fairy lights: blocks between two bulbs, and the most rings and bulbs per ring. */
    static final float BULB_SPACING = 0.2f;
    static final int MAX_RINGS = 3;
    static final int MIN_BULBS = 8;
    static final int MAX_BULBS = 24;

    private Layout() {
    }

    /**
     * @param baseY  offset from the root's passenger point down to the ground
     * @param yaw    Minecraft yaw the snowman faces, in degrees
     * @param roll   extra spin of a lone rolling snowball, or null
     */
    public static List<Piece> pieces(State s, float baseY, float yaw, Quaternionf roll) {
        List<Piece> out = new ArrayList<>();
        Matrix4f frame = new Matrix4f().translate(0, baseY, 0).rotateY((float) Math.toRadians(-yaw));
        int[] sizes = s.sizes;
        for (int i = 0; i < sizes.length; i++) {
            float w = Ball.scale(sizes[i]);
            float y = Ball.centre(sizes, i);
            if (roll != null && sizes.length == 1) {
                // A tumbling ball rests on whatever point is lowest right now, so its corners
                // never poke into the ground.
                y = restHeight(w, roll);
            }
            // Three crossed boxes make the ball look round instead of like a cube; a fourth fills the inside.
            float[][] dims = {{w, w * ROUND, w * ROUND}, {w * ROUND, w, w * ROUND}, {w * ROUND, w * ROUND, w}};
            for (int k = 0; k < 4; k++) {
                Matrix4f m = new Matrix4f(frame).translate(0, y, 0);
                if (roll != null && sizes.length == 1) {
                    m.rotate(roll);
                }
                Matrix4f box;
                if (k < 3) {
                    box = cube(m, dims[k][0], dims[k][1], dims[k][2]);
                } else {
                    // A mirrored shell: the negative scale turns its faces inward, so a player
                    // standing inside a big snowball sees white snow instead of looking through it.
                    float inner = w * ROUND * 0.99f;
                    box = cube(m, -inner, inner, inner);
                }
                out.add(new Piece(Kind.BALL, i * 4 + k, box, true));
            }
        }
        if (sizes.length < State.SNOWMAN) {
            return out;
        }
        // The body is the ball under the head; on taller stacks the balls below are just snow.
        int body = sizes.length - 2;
        int head = sizes.length - 1;
        float midW = Ball.scale(sizes[body]);
        float midY = Ball.centre(sizes, body);
        float midR = midW / 2;
        for (int b = 0; b < s.buttons; b++) {
            float e = Math.max(0.06f, midW * 0.11f);
            float by = midY + midW * (0.22f - 0.2f * b);
            out.add(new Piece(Kind.BUTTON, b,
                    cube(new Matrix4f(frame).translate(0, by, frontZ(midR, by - midY) + e * 0.15f), e, e, e), true));
        }
        for (int a = 0; a < s.arms; a++) {
            float side = a == 0 ? -1 : 1;
            float len = Math.max(0.5f, midW * 0.95f);
            Matrix4f m = new Matrix4f(frame)
                    .translate(side * (midR * ROUND + len * 0.36f), midY + midW * 0.18f, 0)
                    // The stick sprite runs from bottom left to top right; mirror it for the other arm.
                    .rotateY(side < 0 ? (float) Math.PI : 0)
                    .rotateZ((float) Math.toRadians(-20))
                    .scale(len);
            out.add(new Piece(Kind.ARM, a, m, false));
        }
        float headW = Ball.scale(sizes[head]);
        float headY = Ball.centre(sizes, head);
        float headR = headW / 2;
        if (s.eyes) {
            float e = Math.max(0.06f, headW * 0.12f);
            for (int k = 0; k < 2; k++) {
                float x = (k == 0 ? -1 : 1) * headW * 0.19f;
                float ey = headY + headW * 0.12f;
                out.add(new Piece(Kind.EYE, k,
                        cube(new Matrix4f(frame).translate(x, ey, frontZ(headR, ey - headY) + e * 0.15f), e, e, e), true));
            }
        }
        if (s.mouth) {
            // A smile of five coal pebbles under the nose, its ends curving up.
            float e = Math.max(0.045f, headW * 0.075f);
            for (int k = 0; k < 5; k++) {
                int off = k - 2;
                float x = off * headW * 0.095f;
                float my = headY - headW * 0.21f + off * off * headW * 0.022f;
                out.add(new Piece(Kind.MOUTH, k,
                        cube(new Matrix4f(frame).translate(x, my, frontZ(headR, my - headY) + e * 0.1f), e, e, e), true));
            }
        }
        if (s.lights) {
            lights(out, frame, sizes, head);
        }
        if (s.nose) {
            float t = Math.max(0.05f, headW * 0.1f);
            float len = Math.max(0.16f, headW * 0.42f);
            float ny = headY - headW * 0.04f;
            out.add(new Piece(Kind.NOSE, 0,
                    cube(new Matrix4f(frame).translate(0, ny, frontZ(headR, 0) + len * 0.38f), t, t, len), true));
        }
        if (s.scarf != null) {
            // The band goes around the neck, wide enough to sit outside the snow (the band is 14/16 wide).
            float neckY = headY - headR * 0.85f;
            out.add(new Piece(Kind.SCARF, 0,
                    new Matrix4f(frame).translate(0, neckY, 0).scale(headW * 1.32f), false));
            // The loose end hangs down the front of the body, whatever the body's size.
            float len = Math.max(0.3f, headW * 0.7f);
            float tailY = neckY - len * 0.45f;
            out.add(new Piece(Kind.SCARF, 1, new Matrix4f(frame)
                    .translate(headW * 0.2f, tailY, frontZ(midR, tailY - midY) + len * 0.06f)
                    .rotateZ((float) Math.toRadians(4)).scale(len), false));
        }
        if (s.hat) {
            Matrix4f m;
            if (s.hatTop) {
                // The top hat's model is a full block: brim at the bottom, centred on the origin.
                // Flat and sunk a little into the head, so no snow shows between brim and head.
                float size = headW * 1.15f;
                m = new Matrix4f(frame).translate(0, headY + headR * 0.86f + size * 0.5f, 0).scale(size);
            } else if (s.hatBlock) {
                // Blocks use the "worn on the head" model, which sits over the head like a pumpkin.
                m = new Matrix4f(frame).translate(0, headY + headR * 0.95f, 0).scale(headW * 1.25f);
            } else {
                // Flat items stand on top of the head, tipped a little to one side.
                float size = headW * 1.05f;
                m = new Matrix4f(frame).translate(0, headY + headR + size * 0.36f, 0)
                        .rotateZ((float) Math.toRadians(-8)).scale(size);
            }
            out.add(new Piece(Kind.HAT, 0, m, false));
        }
        return out;
    }

    /**
     * Fairy lights: a tilted ring of bulbs around each ball below the head (the top three at
     * most), every ring leaning the other way, like a string wound down the snowman.
     */
    private static void lights(List<Piece> out, Matrix4f frame, int[] sizes, int head) {
        int index = 0;
        int wires = 0;
        int lowest = Math.max(0, head - MAX_RINGS);
        for (int i = head - 1; i >= lowest; i--) {
            float w = Ball.scale(sizes[i]);
            float r = w / 2;
            float cy = Ball.centre(sizes, i);
            int n = bulbs(w);
            float bulb = Math.max(0.085f, Math.min(0.17f, w * 0.085f));
            float wire = Math.max(0.02f, bulb * 0.22f);
            float tilt = (i % 2 == 0 ? 1 : -1) * r * 0.32f;
            org.joml.Vector3f[] at = new org.joml.Vector3f[n];
            for (int k = 0; k < n; k++) {
                double angle = 2 * Math.PI * k / n;
                float c = (float) Math.cos(angle);
                float z = (float) Math.sin(angle);
                float dy = tilt * c;
                float d = surface(r, c, z, dy) + bulb * 0.15f;
                at[k] = new org.joml.Vector3f(c * d, cy + dy, z * d);
                Matrix4f m = new Matrix4f(frame).translate(at[k]).rotateY((float) -angle);
                out.add(new Piece(Kind.LIGHT, index++, cube(m, bulb, bulb, bulb), true));
            }
            // A thin dark wire from each bulb to the next, so the bulbs read as one string.
            for (int k = 0; k < n; k++) {
                org.joml.Vector3f a = at[k];
                org.joml.Vector3f b = at[(k + 1) % n];
                org.joml.Vector3f dir = new org.joml.Vector3f(b).sub(a);
                float len = dir.length();
                if (len < 1e-4f) {
                    continue;
                }
                Matrix4f m = new Matrix4f(frame).translate(new org.joml.Vector3f(a).add(b).mul(0.5f))
                        .rotate(new Quaternionf().rotationTo(1, 0, 0, dir.x / len, dir.y / len, dir.z / len));
                out.add(new Piece(Kind.WIRE, wires++, cube(m, len, wire, wire), true));
            }
        }
    }

    /** How many bulbs go around a ball this wide. */
    static int bulbs(float width) {
        int n = Math.round((float) (Math.PI * width / BULB_SPACING));
        return Math.max(MIN_BULBS, Math.min(MAX_BULBS, n));
    }

    /**
     * Distance from a ball's centre to its outside, going in the horizontal direction
     * ({@code x}, {@code z}) at {@code dy} above the centre. The ball is three crossed boxes,
     * so this is the farthest exit through any box that reaches that height.
     */
    static float surface(float r, float x, float z, float dy) {
        float flat = r * ROUND;
        float[][] half = {{r, flat, flat}, {flat, r, flat}, {flat, flat, r}};
        float best = 0;
        for (float[] h : half) {
            if (Math.abs(dy) > h[1]) {
                continue;
            }
            float tx = Math.abs(x) < 1e-6f ? Float.MAX_VALUE : h[0] / Math.abs(x);
            float tz = Math.abs(z) < 1e-6f ? Float.MAX_VALUE : h[2] / Math.abs(z);
            best = Math.max(best, Math.min(tx, tz));
        }
        return best == 0 ? flat : best;
    }

    /** Height of the centre of a ball of width {@code w}, turned by {@code roll}, resting on the ground. */
    static float restHeight(float w, Quaternionf roll) {
        float[][] dims = {{w, w * ROUND, w * ROUND}, {w * ROUND, w, w * ROUND}, {w * ROUND, w * ROUND, w}};
        float lowest = 0;
        org.joml.Vector3f corner = new org.joml.Vector3f();
        for (float[] d : dims) {
            for (int c = 0; c < 8; c++) {
                corner.set(((c & 1) == 0 ? -0.5f : 0.5f) * d[0], ((c & 2) == 0 ? -0.5f : 0.5f) * d[1],
                        ((c & 4) == 0 ? -0.5f : 0.5f) * d[2]);
                roll.transform(corner);
                lowest = Math.min(lowest, corner.y);
            }
        }
        return -lowest;
    }

    /** How far the round front of a ball sticks out at height {@code dy} above its centre. */
    static float frontZ(float r, float dy) {
        float flat = r * ROUND;
        float h = Math.abs(dy);
        // Between the three rounding boxes the front is the full radius near the middle and
        // steps back to the inner boxes further up or down.
        return h <= flat ? r : flat;
    }

    /** A block display's unit cube, centred and scaled to the given size. */
    static Matrix4f cube(Matrix4f m, float x, float y, float z) {
        return m.scale(x, y, z).translate(-0.5f, -0.5f, -0.5f);
    }
}
