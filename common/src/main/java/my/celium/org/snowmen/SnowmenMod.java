package my.celium.org.snowmen;

import my.celium.org.Mycel;
import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.config.MycelConfig;
import my.celium.org.metadata.ModMetadata;
import my.celium.org.network.MycelNetwork;
import my.celium.org.network.PacketDefinition;
import my.celium.org.registry.DeferredEntry;
import my.celium.org.registry.MycelRegistry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;

/**
 * Snowmen: roll snowballs through snow until they grow, stack three into a snowman
 * and decorate it with vanilla items. Snowmen are built from vanilla display entities; the
 * only new thing is the rolled snowball item you get when you pick one up.
 */
public final class SnowmenMod {
    public static final String ID = "snowmen";

    public static final ModMetadata META = ModMetadata.builder(ID, "Snowman", "1.0.0")
            .author("MyCelium")
            .website("https://www.curseforge.com/minecraft/mc-mods/snowmen")
            .updateUrl("https://wiki.mycelium-gg.org/updates/snowmen.json")
            .build();

    public static ConfigValue<Boolean> melting;
    public static ConfigValue<Integer> maxBallSize;
    public static ConfigValue<Boolean> snowTrail;
    public static ConfigValue<Integer> maxStack;
    public static MycelConfig config;
    public static ConfigValue<Boolean> rotateHint;
    public static DeferredEntry<Item> rolledSnowball;
    public static DeferredEntry<Item> topHat;
    public static DeferredEntry<Item> scarf;
    public static PacketDefinition<RotatePacket> rotate;

    private SnowmenMod() {
    }

    /** Called once from each loader's entrypoint. */
    public static void init() {
        Mycel.initialize();
        Mycel.registerMod(META);

        rolledSnowball = MycelRegistry.items(META).registerWithKey("rolled_snowball",
                key -> new RolledSnowballItem(new Item.Properties().setId(key).stacksTo(16)));
        topHat = MycelRegistry.items(META).registerWithKey("top_hat",
                key -> new TopHatItem(new Item.Properties().setId(key).stacksTo(16)
                        .component(DataComponents.LORE, new ItemLore(List.of(
                                Component.translatable("item.snowmen.top_hat.hint")
                                        .withStyle(st -> st.withItalic(false)).withStyle(ChatFormatting.GRAY))))));

        scarf = MycelRegistry.items(META).registerWithKey("scarf",
                key -> new ScarfItem(new Item.Properties().setId(key).stacksTo(16)
                        .component(DataComponents.LORE, new ItemLore(List.of(
                                Component.translatable("item.snowmen.scarf.hint")
                                        .withStyle(st -> st.withItalic(false)).withStyle(ChatFormatting.GRAY))))));

        rotate = PacketDefinition.toServer(RotatePacket.TYPE, RotatePacket.CODEC,
                (packet, context) -> Snowman.rotateNear(context.sender(), packet.back()));
        MycelNetwork.register(META, rotate);

        ConfigBuilder builder = Mycel.config(META);
        melting = builder.booleanValue("melting", true,
                "Snowmen and snowballs slowly melt in places where a snow golem would melt, like deserts and the Nether.");
        maxBallSize = builder.intValue("ballSizeLimit", 0, 0, 1_000_000,
                "How big a snowball can grow while rolling. 0 means no limit.");
        maxStack = builder.intValue("maxStackHeight", 5, State.SNOWMAN, 32,
                "How many snowballs can be stacked. Three make a snowman; more make it taller.");
        snowTrail = builder.booleanValue("snowTrail", true,
                "Rolling a snowball picks up the snow it rolls over and leaves a trail of bare ground.");
        rotateHint = builder.booleanValue("rotateHint", true,
                "Show a hint above the hotbar when you stand next to a finished snowman.");
        config = builder.build();
    }

    public static boolean isModEnabled() {
        return Mycel.isEnabled(ID);
    }

    public static int maxStack() {
        return maxStack == null ? 5 : maxStack.get();
    }

    public static int maxBallSize() {
        int max = maxBallSize == null ? 0 : maxBallSize.get();
        return max <= 0 ? Integer.MAX_VALUE : max;
    }
}
