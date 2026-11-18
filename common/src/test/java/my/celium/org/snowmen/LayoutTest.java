package my.celium.org.snowmen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class LayoutTest {
    private static long count(List<Layout.Piece> pieces, Layout.Kind kind) {
        return pieces.stream().filter(p -> p.kind() == kind).count();
    }

    @Test
    void aLoneBallIsThreeRoundingBoxesAndAnInnerShell() {
        List<Layout.Piece> pieces = Layout.pieces(State.ball(5), 0, 0, null);
        assertEquals(4, pieces.size());
        assertEquals(4, count(pieces, Layout.Kind.BALL));
        assertTrue(pieces.get(3).matrix().determinant() < 0, "the inner shell is mirrored so it faces inward");
    }

    @Test
    void aDecoratedSnowmanHasEveryPiece() {
        State s = new State();
        s.sizes = new int[] {14, 9, 5};
        s.eyes = true;
        s.nose = true;
        s.buttons = 3;
        s.arms = 2;
        s.scarf = "red";
        s.hat = true;
        List<Layout.Piece> pieces = Layout.pieces(s, 0, 0, null);
        assertEquals(12, count(pieces, Layout.Kind.BALL));
        assertEquals(2, count(pieces, Layout.Kind.EYE));
        assertEquals(1, count(pieces, Layout.Kind.NOSE));
        assertEquals(3, count(pieces, Layout.Kind.BUTTON));
        assertEquals(2, count(pieces, Layout.Kind.ARM));
        assertEquals(2, count(pieces, Layout.Kind.SCARF));
        assertEquals(1, count(pieces, Layout.Kind.HAT));
    }

    @Test
    void theFaceIsOnTheFrontAndFollowsTheYaw() {
        State s = new State();
        s.sizes = new int[] {14, 9, 5};
        s.nose = true;
        Vector3f south = noseTip(s, 0);
        assertTrue(south.z > 0.2f, "facing south, the nose points to +Z: " + south);
        Vector3f west = noseTip(s, 90);
        assertTrue(west.x < -0.2f, "facing west, the nose points to -X: " + west);
        assertTrue(south.y > Ball.centre(s.sizes, 1), "the nose is on the head");
    }

    @Test
    void baseOffsetMovesEverything() {
        float y0 = Layout.pieces(State.ball(5), 0, 0, null).get(0).matrix().transformPosition(new Vector3f(0.5f)).y;
        float y1 = Layout.pieces(State.ball(5), -2, 0, null).get(0).matrix().transformPosition(new Vector3f(0.5f)).y;
        assertEquals(-2, y1 - y0, 1e-5);
    }

    private static Vector3f noseTip(State s, float yaw) {
        Layout.Piece nose = Layout.pieces(s, 0, yaw, null).stream()
                .filter(p -> p.kind() == Layout.Kind.NOSE).findFirst().orElseThrow();
        return nose.matrix().transformPosition(new Vector3f(0.5f, 0.5f, 1f));
    }

    @Test
    void onTallStacksTheFaceIsOnTheTopBall() {
        State s = new State();
        s.sizes = new int[] {16, 13, 10, 7, 5};
        s.eyes = true;
        s.buttons = 1;
        List<Layout.Piece> pieces = Layout.pieces(s, 0, 0, null);
        Layout.Piece eye = pieces.stream().filter(p -> p.kind() == Layout.Kind.EYE).findFirst().orElseThrow();
        Layout.Piece button = pieces.stream().filter(p -> p.kind() == Layout.Kind.BUTTON).findFirst().orElseThrow();
        float eyeY = eye.matrix().transformPosition(new Vector3f(0.5f)).y;
        float buttonY = button.matrix().transformPosition(new Vector3f(0.5f)).y;
        assertTrue(eyeY > Ball.centre(s.sizes, 3) && eyeY < Ball.height(s.sizes), "eyes on the top ball: " + eyeY);
        assertTrue(Math.abs(buttonY - Ball.centre(s.sizes, 3)) < Ball.scale(7) / 2, "buttons on the ball below: " + buttonY);
    }

    @Test
    void aSmileIsFivePebblesUnderTheNose() {
        State s = new State();
        s.sizes = new int[] {14, 9, 5};
        s.mouth = true;
        s.nose = true;
        List<Layout.Piece> pieces = Layout.pieces(s, 0, 0, null);
        assertEquals(5, count(pieces, Layout.Kind.MOUTH));
        Vector3f nose = pieces.stream().filter(p -> p.kind() == Layout.Kind.NOSE).findFirst().orElseThrow()
                .matrix().transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
        for (Layout.Piece p : pieces) {
            if (p.kind() == Layout.Kind.MOUTH) {
                Vector3f at = p.matrix().transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
                assertTrue(at.y < nose.y, "the smile is below the nose");
            }
        }
    }

    @Test
    void fairyLightsSitOutsideTheSnow() {
        State s = new State();
        s.sizes = new int[] {14, 9, 5};
        s.lights = true;
        List<Layout.Piece> pieces = Layout.pieces(s, 0, 30, null);
        long bulbs = count(pieces, Layout.Kind.LIGHT);
        assertEquals(bulbs, count(pieces, Layout.Kind.WIRE), "one wire from every bulb to the next");
        assertEquals(Layout.bulbs(Ball.scale(14)) + Layout.bulbs(Ball.scale(9)), bulbs);
        for (Layout.Piece p : pieces) {
            if (p.kind() != Layout.Kind.LIGHT) {
                continue;
            }
            Vector3f at = p.matrix().transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
            float dist = (float) Math.hypot(at.x, at.z);
            assertTrue(dist >= Ball.scale(9) / 2 * Layout.ROUND, "a bulb is never inside a ball: " + dist);
        }
    }

    @Test
    void fairyLightsWindAroundThreeBallsAtMost() {
        State s = new State();
        s.sizes = new int[] {16, 14, 12, 10, 8, 6};
        s.lights = true;
        long bulbs = count(Layout.pieces(s, 0, 0, null), Layout.Kind.LIGHT);
        assertEquals(Layout.bulbs(Ball.scale(12)) + Layout.bulbs(Ball.scale(10)) + Layout.bulbs(Ball.scale(8)), bulbs);
    }

    @Test
    void surfaceIsTheOutsideOfTheCrossedBoxes() {
        float r = 1;
        assertEquals(r, Layout.surface(r, 1, 0, 0), 1e-5);
        assertEquals(r, Layout.surface(r, 0, 1, 0), 1e-5);
        float diag = (float) Math.sqrt(0.5);
        assertTrue(Layout.surface(r, diag, diag, 0) > r, "the diagonal reaches past the radius");
        assertEquals(r * Layout.ROUND, Layout.surface(r, 1, 0, 0.9f), 1e-5);
    }

    @Test
    void biggerBallsGetMoreBulbs() {
        assertEquals(Layout.MIN_BULBS, Layout.bulbs(0.3f));
        assertTrue(Layout.bulbs(1.2f) > Layout.bulbs(0.6f));
        assertEquals(Layout.MAX_BULBS, Layout.bulbs(20));
    }
}
