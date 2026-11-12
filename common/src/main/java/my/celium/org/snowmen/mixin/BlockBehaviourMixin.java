package my.celium.org.snowmen.mixin;

import my.celium.org.snowmen.Snowman;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Right-clicking snow with an empty hand packs a snowball; with a snowball overhead it puts it down. */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void snowmen$use(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (!my.celium.org.snowmen.SnowGate.on()) {
            return;
        }
        InteractionResult result = Snowman.useBlock(state, level, pos, player, hit);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
