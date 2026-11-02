package my.celium.org.snowmen.fabric;

import my.celium.org.fabric.FabricNetworking;
import my.celium.org.snowmen.SnowmenMod;
import net.fabricmc.api.ModInitializer;

public final class SnowmenFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SnowmenMod.init();
        FabricNetworking.flush();
    }
}
