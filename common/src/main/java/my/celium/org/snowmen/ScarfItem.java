package my.celium.org.snowmen;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A knitted scarf for snowmen. Dye it like leather armor; the snowman wears the same colour. */
public class ScarfItem extends Item {
    /** Red, the colour of an undyed scarf. */
    public static final int DEFAULT_COLOR = 0xB02E26;

    public ScarfItem(Properties properties) {
        super(properties);
    }

    /** An icy name, in the player's language. */
    @Override
    public Component getName(ItemStack stack) {
        return Ice.gradient(Language.getInstance().getOrDefault(getDescriptionId(), "Scarf"));
    }
}
