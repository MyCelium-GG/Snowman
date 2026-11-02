package my.celium.org.snowmen.mixin;

import net.minecraft.world.entity.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Interaction.class)
public interface InteractionAccessor {
    @Invoker("setWidth")
    void snowmen$setWidth(float width);

    @Invoker("setHeight")
    void snowmen$setHeight(float height);

    @Invoker("setResponse")
    void snowmen$setResponse(boolean response);
}
