package my.celium.org.snowmen.fabric;

import my.celium.org.fabric.FabricNetworking;
import my.celium.org.snowmen.SnowmenMod;
import net.fabricmc.api.ModInitializer;

public final class SnowmenFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mycel")) {
            my.celium.org.snowmen.MissingMycel.logOnce();
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                    (handler, sender, server) -> my.celium.org.snowmen.MissingMycel.tell(handler.getPlayer()));
            return;
        }
        my.celium.org.snowmen.SnowGate.open();
        SnowmenMod.init();
        FabricNetworking.flush();
    }
}
