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
    public SnowmenNeoForge(IEventBus modBus,
            @SuppressWarnings("unused") ModContainer container) {
        if (!net.neoforged.fml.ModList.get().isLoaded("mycel")) {
            my.celium.org.snowmen.MissingMycel.logOnce();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
                if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                    my.celium.org.snowmen.MissingMycel.tell(player);
                }
            });
            return;
        }
        my.celium.org.snowmen.SnowGate.open();
        SnowmenMod.init();
        if (FMLEnvironment.getDist().isClient()) {
            ClientSnowmen.register(modBus);
        }
    }
}
