package my.celium.org.snowmen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a snowball or snowman is made of, saved as entity tags on its root entity, so
 * it survives restarts without any custom save data. Pure, so it is unit tested.
 */
public final class State {
    static final String PREFIX = "snowmen.";
    public static final String ROOT = PREFIX + "root";
    public static final String PART = PREFIX + "part";
    public static final String CARRIED = PREFIX + "carried";

    public int[] sizes = new int[0];
    public boolean eyes;
    public boolean nose;
    public int buttons;
    public int arms;
    /** Dye colour name of the scarf, or null. */
    public String scarf;
    /** Whether a hat display belongs to it (the hat item itself lives in that display). */
    public boolean hat;
    /** Whether the hat is a block (worn over the head like a pumpkin) or a flat item on top. Not saved. */
    public boolean hatBlock;

    public static State ball(int size) {
        State s = new State();
        s.sizes = new int[] {Ball.clamp(size)};
        return s;
    }

    public boolean complete() {
        return sizes.length == 3;
    }

    public boolean decorated() {
        return eyes || nose || buttons > 0 || arms > 0 || scarf != null || hat;
    }

    public int top() {
        return sizes[sizes.length - 1];
    }

    public static State read(Set<String> tags) {
        State s = new State();
        int[] sizes = new int[3];
        int count = 0;
        for (String tag : tags) {
            if (!tag.startsWith(PREFIX)) {
                continue;
            }
            String body = tag.substring(PREFIX.length());
            int eq = body.indexOf('=');
            String key = eq < 0 ? body : body.substring(0, eq);
            String value = eq < 0 ? "" : body.substring(eq + 1);
            switch (key) {
                case "b0", "b1", "b2" -> {
                    int i = key.charAt(1) - '0';
                    sizes[i] = Ball.clamp(parse(value, Ball.MIN_SIZE));
                    count = Math.max(count, i + 1);
                }
                case "eyes" -> s.eyes = true;
                case "nose" -> s.nose = true;
                case "hat" -> s.hat = true;
                case "buttons" -> s.buttons = Math.max(0, Math.min(3, parse(value, 0)));
                case "arms" -> s.arms = Math.max(0, Math.min(2, parse(value, 0)));
                case "scarf" -> s.scarf = value.isEmpty() ? null : value;
                default -> {
                }
            }
        }
        s.sizes = new int[count];
        for (int i = 0; i < count; i++) {
            s.sizes[i] = sizes[i] == 0 ? Ball.MIN_SIZE : sizes[i];
        }
        return s;
    }

    /** The state tags; {@link #ROOT}, {@link #CARRIED} and part tags are managed elsewhere. */
    public List<String> tags() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            out.add(PREFIX + "b" + i + "=" + sizes[i]);
        }
        if (eyes) {
            out.add(PREFIX + "eyes");
        }
        if (nose) {
            out.add(PREFIX + "nose");
        }
        if (hat) {
            out.add(PREFIX + "hat");
        }
        if (buttons > 0) {
            out.add(PREFIX + "buttons=" + buttons);
        }
        if (arms > 0) {
            out.add(PREFIX + "arms=" + arms);
        }
        if (scarf != null) {
            out.add(PREFIX + "scarf=" + scarf);
        }
        return out;
    }

    /** True for tags written by {@link #tags()}, so old ones can be removed before writing. */
    public static boolean isStateTag(String tag) {
        return tag.startsWith(PREFIX) && !tag.equals(ROOT) && !tag.equals(CARRIED) && !tag.startsWith(PART);
    }

    private static int parse(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
