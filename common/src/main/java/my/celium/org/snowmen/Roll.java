package my.celium.org.snowmen;

/**
 * How a lone snowball moves: speeds in blocks per tick. Pure math, so it is unit tested.
 * Bigger balls are heavier: kicks send them less far and pushing them is slower.
 */
public final class Roll {
    /** Speed kept per tick on snow, and on anything else. */
    public static final double FRICTION_SNOW = 0.88;
    public static final double FRICTION = 0.80;
    /** Below this the ball stops. */
    public static final double MIN_SPEED = 0.006;
    public static final double MAX_SPEED = 0.6;
    /** Share of the speed a ball keeps when it bounces off a wall. */
    public static final double BOUNCE = 0.35;
    /** How quickly a pushed ball moves back out in front of the player, per block of overlap. */
    public static final double CATCH_UP = 0.6;
    /** Blocks of rolling between two crunch sounds. */
    public static final double SOUND_STEP = 0.8;

    private Roll() {
    }

    /** Speed a hit adds: about 4 blocks of rolling on snow for a small ball, 2 for a big one. */
    public static double kick(int size) {
        return 0.5 / (1 + 0.06 * (Ball.clamp(size) - 1));
    }

    /** Share of the walking speed a pushed ball takes on: it keeps pace, whatever its size. */
    public static double pushFactor(int size) {
        return 1.05;
    }

    /** Radians a ball turns while rolling {@code distance} blocks, like a real ball. */
    public static float spin(double distance, int size) {
        return (float) (distance / (Ball.scale(size) / 2));
    }

    /** Most a ball turns in one tick. Clients animate the shortest way between two turns, so
     *  more than half a turn would look like the ball spinning backwards. */
    public static final float MAX_SPIN_STEP = 2.6f;

    /** {@link #spin}, capped to what one tick can show. */
    public static float spinStep(double distance, int size) {
        return Math.min(MAX_SPIN_STEP, spin(distance, size));
    }

    /** How far a ball rolls from {@code speed} until it stops, on snow. */
    public static double coastDistance(double speed) {
        double distance = 0;
        while (speed >= MIN_SPEED) {
            distance += speed;
            speed *= FRICTION_SNOW;
        }
        return distance;
    }
}
