package my.celium.org.snowmen.neoforge;

import my.celium.org.client.ClientHooks;
import my.celium.org.snowmen.SnowmenMod;
import my.celium.org.snowmen.client.SnowmenClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only: the settings screen and the rotate key. A dedicated server never loads this class. */
public final class ClientSnowmen {
    private ClientSnowmen() {
    }

    public static void register(IEventBus modBus) {
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                () -> (ModContainer ignored, Screen parent) -> ClientHooks.createConfigScreen(parent, SnowmenMod.ID));
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(SnowmenClient.ROTATE_KEY));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SnowmenClient.tick(Minecraft.getInstance()));
    }
}
