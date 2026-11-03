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

    @Test
    void aStoppedBallSettlesOnItsNearestSide() {
        org.joml.Quaternionf tilted = new org.joml.Quaternionf().rotateX(0.3f).rotateZ(-0.2f);
        org.joml.Quaternionf settled = Roll.nearestQuarterTurn(tilted);
        assertTrue(Math.abs(settled.dot(new org.joml.Quaternionf())) > 0.999f, "a small tilt settles back upright");
        org.joml.Quaternionf turned = new org.joml.Quaternionf().rotateX((float) Math.PI / 2 + 0.2f);
        assertTrue(Math.abs(Roll.nearestQuarterTurn(turned).dot(new org.joml.Quaternionf().rotateX((float) Math.PI / 2))) > 0.999f);
    }

    @Test
    void aTumblingBallNeverSinksIntoTheGround() {
        float w = Ball.scale(10);
        assertEquals(w / 2, Layout.restHeight(w, new org.joml.Quaternionf()), 1e-4);
        float tipped = Layout.restHeight(w, new org.joml.Quaternionf().rotateX((float) Math.PI / 4));
        assertTrue(tipped > w / 2, "standing on an edge lifts the centre: " + tipped);
    }
}
