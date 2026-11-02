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
    public enum Kind { BALL, EYE, NOSE, BUTTON, ARM, SCARF, HAT }

    /** One display: its kind, its transformation and whether it shows a block (true) or an item. */
    public record Piece(Kind kind, int index, Matrix4f matrix, boolean block) {
    }

    /** How much of a ball's width the rounding boxes keep. */
    static final float ROUND = 0.74f;

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
            // Three crossed boxes make the ball look round instead of like a cube.
            float[][] dims = {{w, w * ROUND, w * ROUND}, {w * ROUND, w, w * ROUND}, {w * ROUND, w * ROUND, w}};
            for (int k = 0; k < 3; k++) {
                Matrix4f m = new Matrix4f(frame).translate(0, y, 0);
                if (roll != null && sizes.length == 1) {
                    m.rotate(roll);
                }
                out.add(new Piece(Kind.BALL, i * 3 + k, cube(m, dims[k][0], dims[k][1], dims[k][2]), true));
            }
        }
        if (sizes.length < 2) {
            return out;
        }
        float midW = Ball.scale(sizes[1]);
        float midY = Ball.centre(sizes, 1);
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
        if (sizes.length < 3) {
            return out;
        }
        float headW = Ball.scale(sizes[2]);
        float headY = Ball.centre(sizes, 2);
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
        if (s.nose) {
            float t = Math.max(0.05f, headW * 0.1f);
            float len = Math.max(0.16f, headW * 0.42f);
            float ny = headY - headW * 0.04f;
            out.add(new Piece(Kind.NOSE, 0,
                    cube(new Matrix4f(frame).translate(0, ny, frontZ(headR, 0) + len * 0.38f), t, t, len), true));
        }
        if (s.scarf != null) {
            float t = Math.max(0.08f, headW * 0.17f);
            float neckY = headY - headR * 0.82f;
            float outer = headW * 0.98f + 0.04f;
            float inner = headW * ROUND + 0.04f;
            out.add(new Piece(Kind.SCARF, 0, cube(new Matrix4f(frame).translate(0, neckY, 0), outer, t, inner), true));
            out.add(new Piece(Kind.SCARF, 1, cube(new Matrix4f(frame).translate(0, neckY, 0), inner, t, outer), true));
            float tail = Math.max(0.2f, headW * 0.55f);
            out.add(new Piece(Kind.SCARF, 2, cube(new Matrix4f(frame)
                    .translate(headW * 0.24f, neckY - tail * 0.45f, outer / 2 + 0.01f), t * 0.9f, tail, 0.05f), true));
        }
        if (s.hat) {
            Matrix4f m;
            if (s.hatTop) {
                // The top hat's model is a full block: brim at the bottom, centred on the origin.
                float size = headW * 1.15f;
                m = new Matrix4f(frame).translate(0, headY + headR * 0.92f + size * 0.5f, 0)
                        .rotateZ((float) Math.toRadians(-6)).scale(size);
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
