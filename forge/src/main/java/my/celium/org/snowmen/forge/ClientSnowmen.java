package my.celium.org.snowmen.forge;

import my.celium.org.client.ClientHooks;
import my.celium.org.snowmen.SnowmenMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/** Client-only: the settings screen in the mod list. A dedicated server never loads this class. */
public final class ClientSnowmen {
    private ClientSnowmen() {
    }

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (Minecraft minecraft, Screen parent) -> ClientHooks.createConfigScreen(parent, SnowmenMod.ID)));
    }
}
