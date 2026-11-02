package my.celium.org.snowmen;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A top hat for snowmen. A snowman wearing one does not melt. */
public class TopHatItem extends Item {
    public TopHatItem(Properties properties) {
        super(properties);
    }

    /** An icy name, in the player's language. */
    @Override
    public Component getName(ItemStack stack) {
        return Ice.gradient(Language.getInstance().getOrDefault(getDescriptionId(), "Top Hat"));
    }
}
