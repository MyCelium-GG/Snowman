package my.celium.org.snowmen.forge;

import my.celium.org.snowmen.SnowmenMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint: server-safe bootstrap. Client classes (the settings screen) stay in {@link ClientSnowmen},
 * because a dedicated server cannot even verify a class that mentions them.
 */
@Mod(SnowmenMod.ID)
public final class SnowmenForge {
    public SnowmenForge(@SuppressWarnings("unused") FMLJavaModLoadingContext context) {
        SnowmenMod.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSnowmen.register();
        }
    }
}
