package my.celium.org.snowmen.neoforge;

import my.celium.org.snowmen.SnowmenMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * NeoForge entrypoint: server-safe bootstrap. Client classes (the settings screen) stay in {@link ClientSnowmen},
 * because a dedicated server cannot even verify a class that mentions them.
 */
@Mod(SnowmenMod.ID)
public final class SnowmenNeoForge {
    public SnowmenNeoForge(@SuppressWarnings("unused") IEventBus modBus,
            @SuppressWarnings("unused") ModContainer container) {
        SnowmenMod.init();
        if (FMLEnvironment.getDist().isClient()) {
            ClientSnowmen.register();
        }
    }
}
