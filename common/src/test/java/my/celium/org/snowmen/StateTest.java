package my.celium.org.snowmen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StateTest {
    @Test
    void roundTripsThroughTags() {
        State s = new State();
        s.sizes = new int[] {14, 9, 5};
        s.eyes = true;
        s.nose = true;
        s.buttons = 3;
        s.arms = 2;
        s.scarf = "light_blue";
        s.hat = true;
        Set<String> tags = new HashSet<>(s.tags());
        tags.add(State.ROOT);
        State back = State.read(tags);
        assertArrayEquals(s.sizes, back.sizes);
        assertTrue(back.eyes && back.nose && back.hat && back.complete() && back.decorated());
        assertEquals(3, back.buttons);
        assertEquals(2, back.arms);
        assertEquals("light_blue", back.scarf);
    }

    @Test
    void ignoresForeignAndBrokenTags() {
        State s = State.read(Set.of("snowmen.b0=abc", "snowmen.buttons=99", "other.tag", "snowmen.arms=-4"));
        assertArrayEquals(new int[] {Ball.MIN_SIZE}, s.sizes);
        assertEquals(3, s.buttons);
        assertEquals(0, s.arms);
        assertNull(s.scarf);
        assertFalse(s.complete());
    }

    @Test
    void stateTagsAreTheOnesItWrites() {
        assertTrue(State.isStateTag("snowmen.b1=4"));
        assertTrue(State.isStateTag("snowmen.scarf=red"));
        assertFalse(State.isStateTag(State.ROOT));
        assertFalse(State.isStateTag("snowmen.of=0c1d2e3f-0000-0000-0000-000000000000"));
        assertFalse(State.isStateTag(State.PART + ".ball.0"));
        assertFalse(State.isStateTag("minecraft.anything"));
    }

    @Test
    void tallStacksRoundTrip() {
        State s = new State();
        s.sizes = new int[] {20, 16, 12, 9, 6, 4};
        State back = State.read(new HashSet<>(s.tags()));
        assertArrayEquals(s.sizes, back.sizes);
        assertTrue(back.complete());
        assertFalse(State.ball(3).complete());
    }
}
