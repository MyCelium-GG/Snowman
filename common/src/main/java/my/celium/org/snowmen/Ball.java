package my.celium.org.snowmen;

/** Snowball sizes and how big they look. Pure math, so it is unit tested. */
public final class Ball {
    public static final int MIN_SIZE = 1;
    /** The size where the item model and the creative menu stop adding new steps; balls can grow past it. */
    public static final int MAX_SIZE = 16;
    /** How much of the ball below a stacked ball sinks into. */
    public static final float OVERLAP = 0.15f;

    private Ball() {
    }

    /** Width of a snowball in blocks: 0.36 at size 1, 1.26 at size 16, and it keeps growing. */
    public static float scale(int size) {
        return 0.30f + 0.06f * clamp(size);
    }

    /** Sizes have no upper limit; only too small (or broken) values are fixed. */
    public static int clamp(int size) {
        return Math.max(MIN_SIZE, size);
    }

    /** Width of the widest ball in a stack. */
    public static float widest(int[] sizes) {
        float w = 0;
        for (int size : sizes) {
            w = Math.max(w, scale(size));
        }
        return w;
    }

    /** Height of the centre of ball {@code index} above the ground, for a stack of these sizes. */
    public static float centre(int[] sizes, int index) {
        float y = 0;
        for (int i = 0; i < index; i++) {
            y += scale(sizes[i]) * (1 - OVERLAP);
        }
        return y + scale(sizes[index]) / 2;
    }

    /** Total height of a stack. */
    public static float height(int[] sizes) {
        if (sizes.length == 0) {
            return 0;
        }
        int top = sizes.length - 1;
        return centre(sizes, top) + scale(sizes[top]) / 2;
    }

    /** A snowball goes on top when it is not bigger than the top one and the stack is below {@code maxStack}. */
    public static boolean canStack(int[] below, int carried, int maxStack) {
        return below.length > 0 && below.length < maxStack && carried >= MIN_SIZE
                && carried <= below[below.length - 1];
    }
}
