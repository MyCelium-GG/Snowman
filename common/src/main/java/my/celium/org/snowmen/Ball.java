package my.celium.org.snowmen;

/** Snowball sizes and how big they look. Pure math, so it is unit tested. */
public final class Ball {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 16;
    /** Biggest snowball a player can still lift over their head. */
    public static final int CARRY_MAX = 10;
    /** How much of the ball below a stacked ball sinks into. */
    public static final float OVERLAP = 0.15f;

    private Ball() {
    }

    /** Width of a snowball in blocks: 0.36 at size 1 up to 1.26 at size 16. */
    public static float scale(int size) {
        return 0.30f + 0.06f * clamp(size);
    }

    public static int clamp(int size) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    }

    public static boolean canCarry(int size) {
        return size <= CARRY_MAX;
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

    /** A ball may only go on top of a bigger one, and a snowman has three. */
    public static boolean canStack(int[] below, int carried) {
        return below.length > 0 && below.length < 3 && carried < below[below.length - 1];
    }
}
