package my.celium.org.snowmen.forge;

import my.celium.org.client.ClientHooks;
import my.celium.org.snowmen.SnowmenMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import my.celium.org.snowmen.client.SnowmenClient;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;

/** Client-only: the settings screen and the rotate key. A dedicated server never loads this class. */
public final class ClientSnowmen {
    private ClientSnowmen() {
    }

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (Minecraft minecraft, Screen parent) -> ClientHooks.createConfigScreen(parent, SnowmenMod.ID)));
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(SnowmenClient.ROTATE_KEY));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> SnowmenClient.tick(Minecraft.getInstance()));
    }
}
