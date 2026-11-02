package my.celium.org.snowmen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BallTest {
    @Test
    void sizesGrowAndClamp() {
        assertEquals(0.36f, Ball.scale(1), 1e-5);
        assertEquals(1.26f, Ball.scale(16), 1e-5);
        assertEquals(Ball.scale(16), Ball.scale(40), 1e-5);
        assertEquals(Ball.scale(1), Ball.scale(-3), 1e-5);
    }

    @Test
    void stackedBallsSinkIntoTheOneBelow() {
        int[] sizes = {15, 10, 6};
        float base = Ball.scale(15);
        assertEquals(base / 2, Ball.centre(sizes, 0), 1e-5);
        assertEquals(base * (1 - Ball.OVERLAP) + Ball.scale(10) / 2, Ball.centre(sizes, 1), 1e-5);
        assertTrue(Ball.height(sizes) < base + Ball.scale(10) + Ball.scale(6));
        assertEquals(0, Ball.height(new int[0]));
    }

    @Test
    void onlySmallerBallsGoOnTopAndThreeIsTheLimit() {
        assertTrue(Ball.canStack(new int[] {12}, 8));
        assertFalse(Ball.canStack(new int[] {12}, 12));
        assertFalse(Ball.canStack(new int[] {12, 8}, 9));
        assertTrue(Ball.canStack(new int[] {12, 8}, 5));
        assertFalse(Ball.canStack(new int[] {12, 8, 5}, 2));
        assertFalse(Ball.canStack(new int[0], 2));
    }
}
