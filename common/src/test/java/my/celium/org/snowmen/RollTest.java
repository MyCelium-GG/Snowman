package my.celium.org.snowmen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RollTest {
    @Test
    void kicksSendSmallBallsFurther() {
        double small = Roll.coastDistance(Roll.kick(1));
        double big = Roll.coastDistance(Roll.kick(16));
        assertTrue(small > 3 && small < 5, "a small ball rolls about 4 blocks: " + small);
        assertTrue(big > 1.5 && big < small, "a big ball rolls less far: " + big);
    }

    @Test
    void pushedBallsKeepPaceWithThePlayer() {
        assertTrue(Roll.pushFactor(1) >= 1);
        assertTrue(Roll.pushFactor(200) >= 1);
    }

    @Test
    void spinMatchesDistanceLikeARealBall() {
        // Rolling one circumference turns the ball once.
        int size = 10;
        double circumference = Math.PI * Ball.scale(size);
        assertEquals(2 * Math.PI, Roll.spin(circumference, size), 1e-4);
        // A tick of rolling never turns a ball by half a turn, so the animation can't spin backwards.
        assertTrue(Roll.spinStep(Roll.MAX_SPEED, 1) < Math.PI);
    }
}
