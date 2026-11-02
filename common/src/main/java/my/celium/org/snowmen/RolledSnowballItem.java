package my.celium.org.snowmen;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A snowball you rolled and picked up. It remembers its size and goes back down as the same
 * snowball: right-click the ground to place it, or a bigger snowball to stack it on top.
 */
public class RolledSnowballItem extends Item {
    public RolledSnowballItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return Snowman.place(context.getLevel(), context.getClickedPos(), context.getClickedFace(),
                context.getPlayer(), context.getItemInHand());
    }

    /** An icy name, in the player's language. */
    @Override
    public Component getName(ItemStack stack) {
        return Ice.gradient(Language.getInstance().getOrDefault(getDescriptionId(), "Rolled Snowball"));
    }
}
