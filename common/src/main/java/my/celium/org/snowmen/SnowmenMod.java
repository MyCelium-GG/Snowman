package my.celium.org.snowmen;

import my.celium.org.Mycel;
import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.config.MycelConfig;
import my.celium.org.metadata.ModMetadata;

/**
 * Snowmen: roll snowballs through snow until they grow, stack three into a snowman
 * and decorate it with vanilla items. Everything a player sees is built from vanilla
 * display entities, so the mod only does work on the server side.
 */
public final class SnowmenMod {
    public static final String ID = "snowmen";

    public static final ModMetadata META = ModMetadata.builder(ID, "Snowmen", "1.0.0")
            .author("MyCelium")
            .website("https://www.curseforge.com/minecraft/mc-mods/snowmen")
            .updateUrl("https://wiki.mycelium-gg.org/updates/snowmen.json")
            .build();

    public static ConfigValue<Boolean> melting;
    public static ConfigValue<Integer> maxBallSize;
    public static ConfigValue<Boolean> snowTrail;
    public static MycelConfig config;

    private SnowmenMod() {
    }

    /** Called once from each loader's entrypoint. */
    public static void init() {
        Mycel.initialize();
        Mycel.registerMod(META);

        ConfigBuilder builder = Mycel.config(META);
        melting = builder.booleanValue("melting", true,
                "Snowmen and snowballs slowly melt in places where a snow golem would melt, like deserts and the Nether.");
        maxBallSize = builder.intValue("maxBallSize", Ball.MAX_SIZE, Ball.MIN_SIZE + 1, Ball.MAX_SIZE,
                "How big a snowball can grow while rolling.");
        snowTrail = builder.booleanValue("snowTrail", true,
                "Rolling a snowball picks up the snow it rolls over and leaves a trail of bare ground.");
        config = builder.build();
    }

    public static boolean isModEnabled() {
        return Mycel.isEnabled(ID);
    }

    public static int maxBallSize() {
        return maxBallSize == null ? Ball.MAX_SIZE : maxBallSize.get();
    }
}
