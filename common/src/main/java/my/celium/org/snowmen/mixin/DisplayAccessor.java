package my.celium.org.snowmen.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setTransformation")
    void snowmen$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void snowmen$setInterpolationDuration(int ticks);

    @Invoker("setTransformationInterpolationDelay")
    void snowmen$setInterpolationDelay(int ticks);

    @Invoker("setPosRotInterpolationDuration")
    void snowmen$setPosRotInterpolationDuration(int ticks);

    @Invoker("setShadowRadius")
    void snowmen$setShadowRadius(float radius);
}
