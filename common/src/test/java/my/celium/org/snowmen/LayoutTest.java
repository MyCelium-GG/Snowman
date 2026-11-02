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
    void aLoneBallIsThreeRoundingBoxes() {
        List<Layout.Piece> pieces = Layout.pieces(State.ball(5), 0, 0, null);
        assertEquals(3, pieces.size());
        assertEquals(3, count(pieces, Layout.Kind.BALL));
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
        assertEquals(9, count(pieces, Layout.Kind.BALL));
        assertEquals(2, count(pieces, Layout.Kind.EYE));
        assertEquals(1, count(pieces, Layout.Kind.NOSE));
        assertEquals(3, count(pieces, Layout.Kind.BUTTON));
        assertEquals(2, count(pieces, Layout.Kind.ARM));
        assertEquals(3, count(pieces, Layout.Kind.SCARF));
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
}
