package my.celium.org.snowmen.mixin;

import my.celium.org.snowmen.Snowman;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets snowman displays notice when their snowman is gone. */
@Mixin(Display.class)
public abstract class DisplayMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void snowmen$tick(CallbackInfo ci) {
        Snowman.partTick((Display) (Object) this);
    }
}
