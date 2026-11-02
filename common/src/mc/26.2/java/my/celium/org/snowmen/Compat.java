package my.celium.org.snowmen;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Minecraft 26.2 names for the few things that moved between 26.1, 26.2 and 26.3. */
final class Compat {
    private Compat() {
    }

    static EntityType<Interaction> interaction() {
        return net.minecraft.world.entity.EntityTypes.INTERACTION;
    }

    static EntityType<Display.BlockDisplay> blockDisplay() {
        return net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY;
    }

    static EntityType<Display.ItemDisplay> itemDisplay() {
        return net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY;
    }

    static void drop(Player player, ItemStack stack) {
        player.drop(stack, false);
    }

    static Block orangeConcrete() {
        return Blocks.CONCRETE.pick(DyeColor.ORANGE);
    }

    static Block wool(DyeColor color) {
        return Blocks.WOOL.pick(color);
    }

    static Block carpet(DyeColor color) {
        return Blocks.CARPET.pick(color);
    }
}
