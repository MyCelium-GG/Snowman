package my.celium.org.snowmen;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A snowman item with an icy name: the Top Hat, the Santa Hat and the Fairy Lights. */
public class TopHatItem extends Item {
    private final String fallback;

    public TopHatItem(Properties properties) {
        this(properties, "Top Hat");
    }

    public TopHatItem(Properties properties, String fallback) {
        super(properties);
        this.fallback = fallback;
    }

    /** An icy name, in the player's language. */
    @Override
    public Component getName(ItemStack stack) {
        return Ice.gradient(Language.getInstance().getOrDefault(getDescriptionId(), fallback));
    }
}
