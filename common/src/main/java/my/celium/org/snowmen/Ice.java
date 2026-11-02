package my.celium.org.snowmen;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/** Icy text colours: names fade from snow white to ice blue and back. */
public final class Ice {
    public static final int SNOW = 0xFFFFFF;
    public static final int FROST = 0xCFF4FF;
    public static final int ICE = 0x8FDCFF;
    public static final int DEEP = 0x5FA9D6;

    private Ice() {
    }

    /** {@code text} with a snow-to-ice gradient across its letters and a snowflake in front. */
    public static MutableComponent gradient(String text) {
        MutableComponent out = Component.literal("❄ ").withStyle(s -> s.withColor(FROST).withItalic(false));
        int[] codePoints = text.codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            // 0 at the ends, 1 in the middle: white edges, blue core.
            double t = codePoints.length <= 1 ? 1 : Math.sin(Math.PI * i / (codePoints.length - 1));
            int color = mix(SNOW, ICE, t);
            out.append(Component.literal(new String(Character.toChars(codePoints[i])))
                    .withStyle(s -> s.withColor(TextColor.fromRgb(color)).withItalic(false)));
        }
        return out;
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
