package my.celium.org.snowmen.fabric;

import my.celium.org.fabric.FabricNetworking;
import my.celium.org.snowmen.client.SnowmenClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/** Fabric client entrypoint: the rotate key and its packet. */
public final class SnowmenFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(SnowmenClient.ROTATE_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(SnowmenClient::tick);
        FabricNetworking.flushClient();
    }
}
