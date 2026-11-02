package my.celium.org.snowmen.mixin;

import my.celium.org.snowmen.Snowman;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Snowballs and snowmen are interaction entities; clicks and ticks on ours go to {@link Snowman}. */
@Mixin(Interaction.class)
public abstract class InteractionMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void snowmen$interact(Player player, InteractionHand hand, Vec3 location,
            CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = Snowman.interact((Interaction) (Object) this, player, hand);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "skipAttackInteraction", at = @At("HEAD"), cancellable = true)
    private void snowmen$attack(Entity attacker, CallbackInfoReturnable<Boolean> cir) {
        if (Snowman.attack((Interaction) (Object) this, attacker)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void snowmen$tick(CallbackInfo ci) {
        Snowman.tick((Interaction) (Object) this);
    }
}
